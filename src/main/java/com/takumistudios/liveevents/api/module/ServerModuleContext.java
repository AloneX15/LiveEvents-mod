package com.takumistudios.liveevents.api.module;

import com.takumistudios.liveevents.api.LiveEventInfo;
import com.takumistudios.liveevents.core.definition.ModuleParams;
import com.takumistudios.liveevents.core.definition.ModuleSpec;
import com.takumistudios.liveevents.core.scheduler.WorkScheduler;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.block.state.BlockState;

import java.util.List;

/** Lo que un módulo de servidor puede consultar y hacer. Todo respeta presupuesto, protecciones y diario. */
public interface ServerModuleContext {
    LiveEventInfo event();

    ModuleSpec spec();

    /** Parámetros resueltos para el perfil de la instancia. */
    ModuleParams params();

    ServerLevel level();

    /** Tiempo del evento en ticks (con decimales si hay timescale). */
    double eventTicks();

    /** Progreso 0–1 dentro del tramo del módulo. */
    double progress();

    /** Semilla determinista propia de este módulo (la misma que usa el cliente). */
    long moduleSeed();

    /** true una vez cada {@code intervalTicks} ticks del módulo (el primero, al empezar). */
    boolean every(double intervalTicks);

    /**
     * Entidades afectadas dentro del radio (o de toda la dimensión si el evento es celeste y radius ≤ 0), ya filtradas
     * por "affects", creativo, espectador, permiso de inmunidad y {@code requires_sky}.
     */
    List<LivingEntity> affected(double radius);

    /** ¿Está bajo el cielo? (heightmap, O(1)). */
    boolean exposed(LivingEntity entity);

    /** Multiplicador de la API (0 = inmune) para este módulo. */
    float factorFor(LivingEntity entity);

    /** Daño con un tipo de daño; respeta lethal:false del evento. */
    void damage(LivingEntity entity, net.minecraft.resources.Identifier damageType, float amount);

    /**
     * Cambia un bloque con todas las comprobaciones (chunk cargado, bloques inmunes, block entities, protecciones,
     * API) y lo apunta en el diario. Devuelve true si se cambió.
     */
    boolean setBlock(BlockPos pos, BlockState state);

    /** Encola trabajo pesado en el planificador con presupuesto. */
    void submit(WorkScheduler.Priority priority, WorkScheduler.Task task);
}
