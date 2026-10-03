package com.takumistudios.liveevents.core.timeline;

/**
 * Reloj del evento en el cliente (§9.4). El servidor manda latidos con el tiempo del evento; entre latidos el cliente
 * avanza con su reloj local y, si se desvía, corrige poco a poco acelerando o frenando (como mucho un 10 %). Si la
 * diferencia es enorme (parón del servidor, salto con /le seek), salta.
 * <p>
 * Trabaja en ticks del evento con decimales y tiempo real en nanosegundos.
 */
public final class SmoothClock {
    /** Diferencia (ticks) a partir de la cual se salta en lugar de corregir. */
    public static final double SNAP_THRESHOLD = 40;
    private static final double MAX_RATE_CORRECTION = 0.10;
    private static final double NANOS_PER_TICK = 50_000_000.0;

    private double ticks;
    private long lastNanos;
    private double targetOffset;
    private boolean paused;
    private double timescale = 1.0;

    public SmoothClock(double startTicks, long nowNanos) {
        this.ticks = startTicks;
        this.lastNanos = nowNanos;
    }

    /** Avanza el reloj local hasta {@code nowNanos} y devuelve el tiempo del evento en ticks. */
    public double advance(long nowNanos) {
        long elapsed = Math.max(0, nowNanos - lastNanos);
        lastNanos = nowNanos;
        if (paused) {
            return ticks;
        }
        double step = elapsed / NANOS_PER_TICK * timescale;
        if (targetOffset != 0 && step > 0) {
            double maxCorrection = step * MAX_RATE_CORRECTION;
            double correction = Math.max(-maxCorrection, Math.min(maxCorrection, targetOffset));
            step += correction;
            targetOffset -= correction;
        }
        ticks += Math.max(0, step);
        return ticks;
    }

    /** Latido del servidor: tiempo real del evento según el servidor. */
    public void sync(double serverTicks, long nowNanos) {
        advance(nowNanos);
        double diff = serverTicks - ticks;
        if (paused || Math.abs(diff) > SNAP_THRESHOLD) {
            ticks = serverTicks;
            targetOffset = 0;
        } else {
            targetOffset = diff;
        }
    }

    public void setPaused(boolean paused, double serverTicks, long nowNanos) {
        advance(nowNanos);
        this.paused = paused;
        this.ticks = serverTicks;
        this.targetOffset = 0;
    }

    public void setTimescale(double timescale) {
        this.timescale = Math.max(0.05, Math.min(4, timescale));
    }

    public boolean paused() {
        return paused;
    }

    public double ticks() {
        return ticks;
    }
}
