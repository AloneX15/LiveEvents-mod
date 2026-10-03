package com.takumistudios.liveevents.api;

import net.minecraft.resources.Identifier;
import net.minecraft.world.phys.Vec3;

import java.util.Optional;

/** Vista de solo lectura de una instancia de evento en curso. */
public interface LiveEventInfo {
    /** Número de instancia (único mientras el servidor está encendido). */
    int instanceId();

    /** Id de la definición ("liveevents:supernova"). */
    String definitionId();

    /** Dimensión ("minecraft:overworld"). */
    Identifier dimension();

    Vec3 center();

    long seed();

    /** cinematic, gameplay o safe. */
    String profile();

    double elapsedSeconds();

    double durationSeconds();

    /** Fase actual, si el evento está dentro de alguna. */
    Optional<String> currentPhase();

    boolean paused();

    /** true si es una previsualización solo visual (/le preview). */
    boolean preview();
}
