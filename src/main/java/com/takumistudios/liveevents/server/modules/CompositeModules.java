package com.takumistudios.liveevents.server.modules;

import com.takumistudios.liveevents.api.module.ServerModule;
import com.takumistudios.liveevents.api.module.ServerModuleContext;
import com.takumistudios.liveevents.content.LEContent;
import com.takumistudios.liveevents.core.plan.MeteorPlan;
import com.takumistudios.liveevents.core.random.DeterministicRandom;
import com.takumistudios.liveevents.core.scheduler.WorkScheduler.Priority;
import com.takumistudios.liveevents.core.scheduler.WorkScheduler.Result;
import com.takumistudios.liveevents.server.instance.EventInstance;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.item.FallingBlockEntity;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.FallingBlock;
import net.minecraft.world.level.block.PointedDripstoneBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;

import java.util.List;

/** Módulos compuestos (servidor + cliente) de la lluvia de meteoritos y el terremoto. */
final class CompositeModules {
    private CompositeModules() {
    }

    /**
     * Lluvia de meteoritos: el plan de impactos sale de la semilla del módulo, así que el cliente dibuja las estelas y
     * los avisos de los mismos impactos sin recibir datos. Aquí se aplican cráter, daño, empujón y fuego.
     */
    static final class MeteorImpacts implements ServerModule {
        private List<MeteorPlan.Impact> plan;
        private long cycle;


        @Override
        public void start(ServerModuleContext ctx) {
            double[] crater = ctx.params().bounds("crater_radius", 3);
            if (ctx.params().json().has("crater_radius") && ctx.params().json().get("crater_radius").isJsonObject()) {
                crater = new double[]{ctx.params().range("crater_radius", 0, 2), ctx.params().range("crater_radius", 1, 5)};
            }
            plan = MeteorPlan.generate(ctx.moduleSeed(), ctx.params().integer("count", 40),
                    Math.max(8, Math.min(1024,ctx.params().number("radius",((EventInstance)ctx.event()).vars().getOrDefault("radius",128.0)))), ctx.spec().fromTicks(), ctx.spec().toTicks(),
                    crater[0], crater[1], ctx.params().bool("first_big", false));
        }

        @Override
        public void tick(ServerModuleContext ctx) {
            EventInstance instance = (EventInstance) ctx.event();
            if (ctx.spec().fromTicks() >= 500 && ctx.spec().toTicks() >= 1300 && ctx.eventTicks() >= ctx.spec().toTicks()) {
                long wanted = 1 + (long)((ctx.eventTicks()-ctx.spec().toTicks())/800);
                if (wanted != cycle) {
                    cycle=wanted;
                    double from=ctx.spec().toTicks()+(cycle-1)*800;
                    plan=MeteorPlan.generate(DeterministicRandom.derive(ctx.moduleSeed(),cycle),ctx.params().integer("count",40),
                            Math.max(8,Math.min(1024,ctx.params().number("radius",instance.vars().getOrDefault("radius",128.0)))),from,from+800,2,5,false).stream()
                            .map(m->new MeteorPlan.Impact((int)(cycle*1000)+m.index(),m.tick(),m.dx(),m.dz(),m.craterRadius(),m.entryAzimuth(),m.entryAngle(),m.big())).toList();
                }
            }
            if (plan != null) instance.meteors.prepare(ctx, plan);
            for (var meteor : instance.meteors.due(ctx)) impact(ctx, meteor);
        }

        private void impact(ServerModuleContext ctx, com.takumistudios.liveevents.core.plan.AnnouncedMeteor impact) {
            ServerLevel level = ctx.level();
            Vec3 c = ctx.event().center();
            int x = (int) Math.floor(impact.x()), z = (int) Math.floor(impact.z());
            if (!BlockModules.loaded(level, x, z)) {
                return; // en chunks descargados el meteorito se ve, pero no cambia nada
            }
            BlockPos ground = BlockPos.containing(impact.x(), impact.y(), impact.z());
            if (!level.isLoaded(ground)) {
                return; // en chunks descargados el meteorito se ve, pero no cambia nada
            }
            if (!ctx.event().preview() && impact.power()>0 && !impact.surface().equals("water")) {
                long seed = DeterministicRandom.derive(ctx.moduleSeed(), impact.index());
                MeteorAftermath.create(ctx,impact,tracked->{
                    BlockModules.carve(tracked,x,z,impact.radius(),0.55,Blocks.BASALT.defaultBlockState(),Blocks.MAGMA_BLOCK.defaultBlockState(),seed,ground.getY());
                    if(impact.surface().equals("earth") && ctx.params().bool("fire",true))
                        BlockModules.placeFires(tracked,x+0.5,z+0.5,impact.radius()+3,(int)Math.round(impact.radius()*2),seed+7);
                });
            }

            double blast = Math.max(0, Math.min(128,ctx.params().number("blast_radius", 6))) * (impact.big() ? 1.5 : 1);
            float damage = (float) Math.max(0, Math.min(1000,ctx.params().number("damage", 8))) * (impact.big() ? 1.75f : 1f) * (float) (0.3 + impact.power());
            Vec3 at = Vec3.atBottomCenterOf(ground);
            if (ctx.event().preview() || impact.power()<=0) return;
            for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, new net.minecraft.world.phys.AABB(ground).inflate(blast), e -> true)) {
                EventInstance instance = (EventInstance) ctx.event();
                if (Math.hypot(e.getX()-c.x,e.getZ()-c.z)>instance.vars().getOrDefault("radius",128.0)) continue;
                if (!instance.accepts(e) || com.takumistudios.liveevents.server.integration.ProtectionCompat.protectedAt(level,e.blockPosition())
                        || com.takumistudios.liveevents.server.journal.BlockChanger.inExcludedRegion(level,e.blockPosition())) {
                    continue;
                }
                double d = e.position().distanceTo(at);
                if (d > blast) {
                    continue;
                }
                double falloff = 1 - d / Math.max(0.1, blast);
                ctx.damage(e, LEContent.METEOR_IMPACT.identifier(), (float) (damage * falloff));
                Vec3 dir = e.position().subtract(at);
                dir = dir.lengthSqr() < 1e-4 ? new Vec3(0, 1, 0) : dir.normalize();
                float factor = ctx.factorFor(e);
                GameModules.push(e, dir.scale(1.2 * falloff * factor).add(0, 0.5 * falloff * factor, 0));
                if (falloff > 0.4 && factor > 0) {
                    e.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, 40, 1));
                }
            }
        }
    }

    /**
     * Terremoto: náusea y lentitud cerca del epicentro, pequeños empujones y desprendimientos. El temblor de cámara con
     * retraso de las ondas P y S lo calcula cada cliente según su distancia.
     */
    static final class SeismicWave implements ServerModule {
        private DeterministicRandom random;

        @Override
        public void start(ServerModuleContext ctx) {
            random = new DeterministicRandom(ctx.moduleSeed());
        }

        @Override
        public void tick(ServerModuleContext ctx) {
            double radius = Math.max(8, ctx.params().number("radius", 160));
            Vec3 c = ctx.event().center();
            EventInstance instance = (EventInstance) ctx.event();
            if (ctx.every(20)) {
                for (ServerPlayer p : ctx.level().players()) {
                    double d = Math.sqrt(p.distanceToSqr(c.x, p.getY(), c.z));
                    if (d > radius || !instance.accepts(p) || ctx.factorFor(p) <= 0) {
                        continue;
                    }
                    if (d < radius * 0.4) {
                        if (ctx.params().bool("nausea", true)) {
                            p.addEffect(new MobEffectInstance(MobEffects.NAUSEA, 100, 0));
                        }
                        p.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, 40, 0));
                    }
                }
            }
            if (ctx.params().bool("drop_blocks", true) && !instance.preview() && ctx.every(5)) {
                for (ServerPlayer p : ctx.level().players()) {
                    if (Math.sqrt(p.distanceToSqr(c.x, p.getY(), c.z)) <= radius) {
                        dropAround(ctx, p.blockPosition());
                    }
                }
            }
        }

        /** Hace caer bloques sin soporte cerca del jugador (registrados en el diario: se pueden revertir). */
        private void dropAround(ServerModuleContext ctx, BlockPos around) {
            ServerLevel level = ctx.level();
            BlockPos origin = around;
            long seed = random.nextLong();
            ctx.submit(Priority.BLOCKS, allowance -> {
                DeterministicRandom r = new DeterministicRandom(seed);
                int changed = 0;
                for (int attempt = 0; attempt < 24 && changed < allowance; attempt++) {
                    BlockPos pos = origin.offset(r.nextInt(33) - 16, r.nextInt(17) - 4, r.nextInt(33) - 16);
                    if (!level.isLoaded(pos)) {
                        continue;
                    }
                    BlockState state = level.getBlockState(pos);
                    BlockState below = level.getBlockState(pos.below());
                    if (state.getBlock() instanceof FallingBlock && below.isAir()) {
                        // Caída en columna hecha por el mod: origen → aire, destino → bloque (las dos en el diario)
                        BlockPos land = pos.below();
                        while (land.getY() > level.getMinY() && level.getBlockState(land.below()).isAir()) {
                            land = land.below();
                        }
                        if (ctx.setBlock(pos, Blocks.AIR.defaultBlockState())) {
                            changed++;
                            if (ctx.setBlock(land, state)) {
                                changed++;
                            }
                        }
                    } else if (state.getBlock() instanceof PointedDripstoneBlock
                            && state.getValue(BlockStateProperties.VERTICAL_DIRECTION) == Direction.DOWN
                            && below.isAir() && r.chance(0.35)) {
                        // Las estalactitas caen y hacen daño como en vanilla (se rompen al caer)
                        if (ctx.setBlock(pos, Blocks.AIR.defaultBlockState())) {
                            FallingBlockEntity.fall(level, pos, state);
                            changed++;
                        }
                    }
                }
                return Result.done(changed);
            });
        }
    }
}
