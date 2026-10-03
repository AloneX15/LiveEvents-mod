package com.takumistudios.liveevents.api;

import net.fabricmc.fabric.api.event.Event;
import net.fabricmc.fabric.api.event.EventFactory;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Eventos al estilo Fabric para otros mods (§15). Parte de la API estable: solo cambia de forma incompatible en una
 * versión mayor.
 */
public final class LiveEventCallbacks {
    private LiveEventCallbacks() {
    }

    /** Antes de empezar un evento. Devolver false lo cancela (p. ej. hay un minijuego en curso). */
    public static final Event<Start> START = EventFactory.createArrayBacked(Start.class, listeners -> event -> {
        for (Start l : listeners) {
            if (!l.allowStart(event)) {
                return false;
            }
        }
        return true;
    });

    /** Al cambiar de fase (logros, misiones, Discord…). */
    public static final Event<PhaseChange> PHASE_CHANGE = EventFactory.createArrayBacked(PhaseChange.class,
            listeners -> (event, phaseId) -> {
                for (PhaseChange l : listeners) {
                    l.onPhaseChange(event, phaseId);
                }
            });

    /** Al terminar o cancelarse un evento. */
    public static final Event<End> END = EventFactory.createArrayBacked(End.class, listeners -> (event, reason) -> {
        for (End l : listeners) {
            l.onEnd(event, reason);
        }
    });

    /** Antes de que un evento cambie un bloque. Devolver false lo protege. */
    public static final Event<BeforeBlockChange> BEFORE_BLOCK_CHANGE = EventFactory.createArrayBacked(BeforeBlockChange.class,
            listeners -> (event, level, pos, from, to) -> {
                for (BeforeBlockChange l : listeners) {
                    if (!l.allowBlockChange(event, level, pos, from, to)) {
                        return false;
                    }
                }
                return true;
            });

    /**
     * Antes de aplicar un efecto de juego (daño, efecto de estado, empujón…) a una entidad. Devuelve un multiplicador:
     * 1 = normal, 0 = inmune. Se usa el menor de todos los listeners.
     */
    public static final Event<BeforeEntityAffected> BEFORE_ENTITY_AFFECTED = EventFactory.createArrayBacked(BeforeEntityAffected.class,
            listeners -> (event, entity, effectType) -> {
                float factor = 1f;
                for (BeforeEntityAffected l : listeners) {
                    factor = Math.min(factor, l.modify(event, entity, effectType));
                }
                return Math.max(0f, factor);
            });

    @FunctionalInterface
    public interface Start {
        boolean allowStart(LiveEventInfo event);
    }

    @FunctionalInterface
    public interface PhaseChange {
        void onPhaseChange(LiveEventInfo event, String phaseId);
    }

    @FunctionalInterface
    public interface End {
        /**
         * @param reason "completed", "stopped", "panic", "error", "shutdown"
         */
        void onEnd(LiveEventInfo event, String reason);
    }

    @FunctionalInterface
    public interface BeforeBlockChange {
        boolean allowBlockChange(LiveEventInfo event, ServerLevel level, BlockPos pos, BlockState from, BlockState to);
    }

    @FunctionalInterface
    public interface BeforeEntityAffected {
        /**
         * @param effectType tipo de módulo que lo aplica ("liveevents:damage_area", "liveevents:knockback"…)
         */
        float modify(LiveEventInfo event, Entity entity, String effectType);
    }
}
