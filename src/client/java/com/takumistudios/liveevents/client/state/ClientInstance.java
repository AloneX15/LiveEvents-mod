package com.takumistudios.liveevents.client.state;

import com.takumistudios.liveevents.core.definition.EventDefinition;
import com.takumistudios.liveevents.core.definition.ModuleParams;
import com.takumistudios.liveevents.core.definition.ModuleSpec;
import com.takumistudios.liveevents.core.definition.Profile;
import com.takumistudios.liveevents.core.random.DeterministicRandom;
import com.takumistudios.liveevents.core.timeline.SmoothClock;
import net.minecraft.world.phys.Vec3;

import java.util.HashMap;
import java.util.Map;

/**
 * Réplica ligera de una instancia en el cliente. Todo lo visual se calcula a partir del tiempo del evento y de la
 * semilla, así que entrar a mitad de evento basta con fijar el reloj (§9.3).
 */
public final class ClientInstance {
    public final int id;
    public final EventDefinition def;
    public final String dimension;
    public final Vec3 center;
    public final long seed;
    public final Profile profile;
    public final boolean preview;
    public final SmoothClock clock;
    public final Map<String, Double> vars = new HashMap<>();
    private final Map<Integer, ModuleParams> params = new HashMap<>();
    private final Map<Integer, Object> state = new HashMap<>();
    /** Tiempo (nanos) en que terminó, para el fundido de salida; -1 mientras está activa. */
    public long endedAt = -1;
    public double timescale;
    /** Último tiempo calculado en este fotograma. */
    public double t;

    public ClientInstance(int id, EventDefinition def, String dimension, Vec3 center, long seed, Profile profile, boolean preview,
                          double startTicks, double timescale, Map<String, Double> overrides, boolean paused, long nowNanos) {
        this.id = id;
        this.def = def;
        this.dimension = dimension;
        this.center = center;
        this.seed = seed;
        this.profile = profile;
        this.preview = preview;
        this.timescale = timescale;
        this.clock = new SmoothClock(startTicks, nowNanos);
        this.clock.setTimescale(timescale);
        if (paused) {
            this.clock.setPaused(true, startTicks, nowNanos);
        }
        this.t = startTicks;
        setOverrides(overrides);
    }

    /** /le set en vivo: nuevas variables y parámetros resueltos de nuevo. */
    public void setOverrides(Map<String, Double> overrides) {
        vars.clear();
        vars.putAll(def.params());
        // El servidor ya validó los valores; aquí solo se recortan a rangos razonables
        overrides.forEach((k, v) -> vars.put(k, Double.isFinite(v) ? Math.max(-1e6, Math.min(1e6, v)) : 0));
        vars.put("duration", def.durationTicks() / 20.0);
        params.clear();
    }

    public ModuleParams params(ModuleSpec spec) {
        return params.computeIfAbsent(spec.index(), i -> new ModuleParams(ModuleParams.forProfile(spec.raw(), profile), def.palette(), vars));
    }

    public long moduleSeed(ModuleSpec spec) {
        return DeterministicRandom.derive(seed, spec.index());
    }

    /** Estado propio de un módulo (sonidos ya disparados, cortes concedidos…). */
    @SuppressWarnings("unchecked")
    public <T> T state(ModuleSpec spec, java.util.function.Supplier<T> factory) {
        return (T) state.computeIfAbsent(spec.index(), i -> factory.get());
    }

    /** Factor de fundido de salida (1 → 0 en 1,5 s tras terminar). */
    public double fadeOut(long nowNanos) {
        if (endedAt < 0) {
            return 1;
        }
        return Math.max(0, 1 - (nowNanos - endedAt) / 1_500_000_000.0);
    }
}
