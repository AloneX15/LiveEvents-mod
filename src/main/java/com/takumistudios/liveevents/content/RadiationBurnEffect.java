package com.takumistudios.liveevents.content;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.entity.LivingEntity;

/**
 * Quemadura por radiación: daño periódico de tipo {@code liveevents:radiation}. El nivel II hace daño el doble de
 * rápido. Respeta el modo no letal del evento a través de {@link com.takumistudios.liveevents.server.LethalGuard}.
 */
public final class RadiationBurnEffect extends MobEffect {
    public RadiationBurnEffect() {
        super(MobEffectCategory.HARMFUL, 0x9CFF3A);
    }

    @Override
    public boolean shouldApplyEffectTickThisTick(int duration, int amplifier) {
        int interval = Math.max(10, 50 >> Math.min(amplifier, 3));
        return duration % interval == 0;
    }

    @Override
    public boolean applyEffectTick(ServerLevel level, LivingEntity entity, int amplifier) {
        LEContent.damageSource(level, LEContent.RADIATION.identifier()).ifPresent(source ->
                com.takumistudios.liveevents.server.LethalGuard.hurt(level, entity, source, 1.0f));
        return true;
    }
}
