package com.takumistudios.liveevents.client.visual;

import com.takumistudios.liveevents.client.config.ClientConfig;
import com.takumistudios.liveevents.client.state.ClientInstance;
import com.takumistudios.liveevents.core.definition.ModuleParams;
import com.takumistudios.liveevents.core.definition.ModuleSpec;
import com.takumistudios.liveevents.core.definition.Profile;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.world.phys.Vec3;

/**
 * Lo que recibe un visual en cada fotograma o tick. Es un único objeto reutilizado: no guardar referencias a él.
 */
public final class VisualContext {
    public ClientInstance instance;
    public ModuleSpec spec;
    public ModuleParams params;
    /** Tiempo del evento en ticks (con decimales, interpolado). */
    public double t;
    /** Progreso 0–1 dentro del tramo del módulo. */
    public double progress;
    public long nowNanos;
    public ClientLevel level;
    public Vec3 camera;
    /** Distancia horizontal de la cámara al centro del evento. */
    public double distance;
    /** Fracción de cielo que ve la cámara (0 en una cueva profunda, 1 al aire libre). */
    public double exposure;
    public ClientConfig.Quality quality;
    /** Fundido de salida de la instancia (1 → 0 al terminar). */
    public double fade;
    VisualDirector director;

    public Profile profile() {
        return instance.profile;
    }

    public boolean safe() {
        return instance.profile == Profile.SAFE;
    }

    public long seed() {
        return instance.moduleSeed(spec);
    }

    public double seconds() {
        return t / 20.0;
    }

    /** Segundos desde el inicio del módulo. */
    public double localSeconds() {
        return (t - spec.fromTicks()) / 20.0;
    }

    /** Ancla del evento en la bóveda. */
    public double anchorAzimuth() {
        return instance.def.anchorAzimuth();
    }

    public double anchorAltitude() {
        return instance.def.anchorAltitude();
    }

    /**
     * Pide permiso para un destello o corte grande (regla de destellos, §7.7). Si se deniega, el visual debe
     * convertirlo en un fundido.
     */
    public boolean requestFlash(double areaRatio, int color) {
        return director.requestFlash(nowNanos, areaRatio, color, safe());
    }

    /** Partículas que este visual puede emitir en este tick (presupuesto global y LOD por distancia). */
    public int particleBudget(double requested) {
        return director.particleBudget(requested, distance);
    }

    /** Fundido de entrada/salida estándar dentro del tramo del módulo (segundos). */
    public double envelope(double fadeInSeconds, double fadeOutSeconds) {
        double local = (t - spec.fromTicks()) / 20.0;
        double remaining = (spec.toTicks() - t) / 20.0;
        double in = fadeInSeconds <= 0 ? 1 : Math.min(1, Math.max(0, local / fadeInSeconds));
        double out = fadeOutSeconds <= 0 ? 1 : Math.min(1, Math.max(0, remaining / fadeOutSeconds));
        return in * out * fade;
    }
}
