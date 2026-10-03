package com.takumistudios.liveevents.server;

import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;

import java.util.HashMap;
import java.util.Map;

/**
 * Modo no letal (§8.6): con {@code lethal: false}, el daño de los eventos nunca baja a nadie de medio corazón. Se
 * aplica por dimensión mientras dura el evento y un margen después (la radiación sigue unos segundos).
 */
public final class LethalGuard {
    private static final Map<Identifier, Long> NON_LETHAL_UNTIL = new HashMap<>();

    private LethalGuard() {
    }

    public static synchronized void protect(ServerLevel level, long untilGameTime) {
        NON_LETHAL_UNTIL.merge(level.dimension().identifier(), untilGameTime, Math::max);
    }

    public static synchronized boolean isProtected(ServerLevel level) {
        Long until = NON_LETHAL_UNTIL.get(level.dimension().identifier());
        return until != null && level.getGameTime() <= until;
    }

    public static synchronized void clear() {
        NON_LETHAL_UNTIL.clear();
    }

    /** Daño de evento: si la dimensión está protegida, nunca deja a la entidad por debajo de 1 punto de vida. */
    public static void hurt(ServerLevel level, LivingEntity entity, DamageSource source, float amount) {
        float dealt = amount;
        if (isProtected(level)) {
            dealt = Math.min(amount, Math.max(0f, entity.getHealth() - 1f));
        }
        if (dealt > 0f) {
            entity.hurtServer(level, source, dealt);
        }
    }
}
