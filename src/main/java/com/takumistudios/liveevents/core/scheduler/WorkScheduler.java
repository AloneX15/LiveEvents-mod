package com.takumistudios.liveevents.core.scheduler;

import java.util.ArrayDeque;
import java.util.EnumMap;
import java.util.Map;
import java.util.function.LongSupplier;

/**
 * Cola de tareas pequeñas con presupuesto en milisegundos por tick (§14.2). En cada tick ejecuta tareas por orden de
 * prioridad hasta agotar el presupuesto o el tope de cambios de bloque; lo que no cabe pasa al tick siguiente.
 * <p>
 * Solo se usa desde el hilo del servidor. Sin tareas pendientes, {@link #tick()} sale inmediatamente.
 */
public final class WorkScheduler {
    /** Prioridades: lo que afecta a lo que se juega va primero. */
    public enum Priority {
        GAMEPLAY,
        BLOCKS,
        CLEANUP
    }

    /**
     * Una tarea. Devuelve los cambios de bloque que ha hecho (para el tope) y si ha terminado.
     */
    @FunctionalInterface
    public interface Task {
        /**
         * @param blockAllowance cambios de bloque que puede hacer en esta llamada
         * @return resultado; una tarea que no termina se vuelve a encolar al principio de su prioridad
         */
        Result run(int blockAllowance);
    }

    public record Result(int blocksChanged, boolean done) {
        public static final Result DONE = new Result(0, true);

        public static Result done(int blocks) {
            return new Result(blocks, true);
        }

        public static Result more(int blocks) {
            return new Result(blocks, false);
        }
    }

    /**
     * Estadísticas del último tick para {@code /le debug perf}.
     *
     * @param lastSliceStartMs tiempo gastado cuando empezó el último trozo: nunca se empieza trabajo con el presupuesto
     *                         ya agotado (el total puede pasarse por el coste del último cambio de bloque vanilla)
     */
    public record Stats(double lastTickMs, int lastTickTasks, int lastTickBlocks, int pending, double lastSliceStartMs) {
    }

    /** Cambios de bloque como máximo por llamada a una tarea. */
    public static final int SLICE = 8;

    private final Map<Priority, ArrayDeque<Owned>> queues = new EnumMap<>(Priority.class);
    private final LongSupplier nanoClock;
    private double budgetMs;
    private int maxBlocksPerTick;
    private double loadFactor = 1.0;
    private Stats stats = new Stats(0, 0, 0, 0, 0);
    /** Coste medio (media móvil) de un trozo de trabajo de bloques, para no empezar uno que no cabe. */
    private double avgSliceNanos;

    private record Owned(String owner, Task task) {
    }

    public WorkScheduler(double budgetMs, int maxBlocksPerTick, LongSupplier nanoClock) {
        for (Priority p : Priority.values()) {
            queues.put(p, new ArrayDeque<>());
        }
        this.nanoClock = nanoClock;
        configure(budgetMs, maxBlocksPerTick);
    }

    public void configure(double budgetMs, int maxBlocksPerTick) {
        this.budgetMs = Math.max(0.1, Math.min(budgetMs, 50));
        this.maxBlocksPerTick = Math.max(1, maxBlocksPerTick);
    }

    /**
     * Factor de carga (0.05–1): el watchdog lo baja cuando el servidor va saturado para reducir el presupuesto.
     */
    public void setLoadFactor(double factor) {
        this.loadFactor = Math.max(0.05, Math.min(1, factor));
    }

    public double loadFactor() {
        return loadFactor;
    }

    public void submit(String owner, Priority priority, Task task) {
        queues.get(priority).addLast(new Owned(owner, task));
    }

    /** Quita todas las tareas de un dueño (instancia cancelada) o todas si owner es null (/le panic). */
    public int cancel(String owner) {
        int removed = 0;
        for (ArrayDeque<Owned> q : queues.values()) {
            int before = q.size();
            if (owner == null) {
                q.clear();
            } else {
                q.removeIf(o -> o.owner().equals(owner));
            }
            removed += before - q.size();
        }
        return removed;
    }

    public int pending() {
        int n = 0;
        for (ArrayDeque<Owned> q : queues.values()) {
            n += q.size();
        }
        return n;
    }

    public int pending(String owner) {
        int n = 0;
        for (ArrayDeque<Owned> q : queues.values()) {
            for (Owned o : q) {
                if (o.owner().equals(owner)) {
                    n++;
                }
            }
        }
        return n;
    }

    public Stats stats() {
        return stats;
    }

    /**
     * Ejecuta tareas hasta agotar el presupuesto. Las excepciones de una tarea se propagan con su dueño para que el
     * director desactive solo ese módulo.
     */
    public void tick() {
        if (pending() == 0) {
            stats = new Stats(0, 0, 0, 0, 0);
            return;
        }
        long start = nanoClock.getAsLong();
        long budgetNanos = (long) (budgetMs * loadFactor * 1_000_000L);
        int blockBudget = Math.max(1, (int) (maxBlocksPerTick * loadFactor));
        int tasks = 0, blocks = 0;
        long lastSliceStart = 0;
        outer:
        for (Priority p : Priority.values()) {
            ArrayDeque<Owned> q = queues.get(p);
            int stalled = 0;
            while (!q.isEmpty()) {
                // Se para si el siguiente trozo (coste medio estimado) ya no cabe; al menos una tarea por tick
                long elapsed = nanoClock.getAsLong() - start;
                if (tasks > 0 && elapsed + (long) avgSliceNanos >= budgetNanos) {
                    break outer;
                }
                int allowance = blockBudget - blocks;
                if (allowance <= 0 && p != Priority.GAMEPLAY) {
                    break outer;
                }
                Owned o = q.pollFirst();
                Result r;
                long sliceStart = nanoClock.getAsLong();
                lastSliceStart = sliceStart - start;
                try {
                    // Trozos pequeños: el tiempo se comprueba entre trozos, así una tarea no se pasa del presupuesto
                    r = o.task().run(Math.max(0, Math.min(allowance, SLICE)));
                } catch (RuntimeException e) {
                    throw new TaskFailure(o.owner(), e);
                }
                tasks++;
                blocks += Math.max(0, r.blocksChanged());
                if (r.blocksChanged() > 0) {
                    avgSliceNanos = avgSliceNanos * 0.8 + (nanoClock.getAsLong() - sliceStart) * 0.2;
                }
                if (r.done()) {
                    stalled = 0;
                } else if (r.blocksChanged() > 0) {
                    q.addLast(o); // turnos rotatorios: ninguna instancia acapara la cola
                    stalled = 0;
                } else {
                    // No avanza en este tick (p. ej. limitada por ritmo): al final para no bloquear a las demás
                    q.addLast(o);
                    if (++stalled >= q.size()) {
                        break;
                    }
                }
            }
        }
        double ms = (nanoClock.getAsLong() - start) / 1_000_000.0;
        stats = new Stats(ms, tasks, blocks, pending(), lastSliceStart / 1_000_000.0);
    }

    /** Excepción de una tarea, con el dueño que la encoló. */
    public static final class TaskFailure extends RuntimeException {
        private final String owner;

        public TaskFailure(String owner, RuntimeException cause) {
            super("Fallo en una tarea de " + owner, cause);
            this.owner = owner;
        }

        public String owner() {
            return owner;
        }
    }
}
