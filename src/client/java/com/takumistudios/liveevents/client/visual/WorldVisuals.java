package com.takumistudios.liveevents.client.visual;

import com.takumistudios.liveevents.core.definition.ModuleParams;
import com.takumistudios.liveevents.core.plan.MeteorPlan;
import com.takumistudios.liveevents.core.random.DeterministicRandom;
import com.takumistudios.liveevents.client.visual.FrameState.Sprite;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;

import java.util.List;

import static com.takumistudios.liveevents.client.visual.SkyVisuals.clamp;

/** Efectos en el mundo: partículas con presupuesto y LOD, ondas, estelas e impactos. */
final class WorldVisuals {
    private WorldVisuals() {
    }

    static ParticleOptions particle(String id, ParticleOptions fallback) {
        try {
            var type = BuiltInRegistries.PARTICLE_TYPE.getValue(Identifier.parse(id));
            return type instanceof SimpleParticleType simple ? simple : fallback;
        } catch (RuntimeException e) {
            return fallback;
        }
    }

    static BlockState block(String id) {
        try {
            Identifier key = Identifier.parse(id);
            return BuiltInRegistries.BLOCK.containsKey(key) ? BuiltInRegistries.BLOCK.getValue(key).defaultBlockState() : Blocks.STONE.defaultBlockState();
        } catch (RuntimeException e) {
            return Blocks.STONE.defaultBlockState();
        }
    }

    static double groundY(VisualContext ctx, double x, double z, double fallback) {
        int bx = (int) Math.floor(x), bz = (int) Math.floor(z);
        if (!ctx.level.hasChunk(bx >> 4, bz >> 4)) {
            return fallback;
        }
        return ctx.level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, bx, bz);
    }

    /** Emisor genérico alrededor del jugador o del centro. */
    static final class ParticleEmitter implements ClientVisual {
        @Override
        public void tick(VisualContext ctx) {
            ModuleParams p = ctx.params;
            double rate = clamp(p.range("rate", ctx.progress, 40), 0, 2000) * ctx.fade;
            int n = ctx.particleBudget(rate / 20.0);
            if (n <= 0) {
                return;
            }
            ParticleOptions type = particle(p.string("particle", "minecraft:ash"), ParticleTypes.ASH);
            boolean aroundCenter = p.string("around", "player").equals("center");
            Vec3 origin = aroundCenter ? ctx.instance.center : ctx.camera;
            double radius = clamp(p.number("radius", 24), 0, 256), height = clamp(p.number("height", 12), -64, 256);
            DeterministicRandom r = new DeterministicRandom(ctx.nowNanos ^ ctx.seed());
            for (int i = 0; i < n; i++) {
                double a = r.range(0, Math.PI * 2), d = radius * Math.sqrt(r.nextDouble());
                double x = origin.x + Math.cos(a) * d, z = origin.z + Math.sin(a) * d;
                double y = aroundCenter ? groundY(ctx, x, z, origin.y) + height : origin.y + r.range(0, height);
                ctx.director.spawn(type, x, y, z, r.range(-0.02, 0.02), aroundCenter ? r.range(0.02, 0.08) : -0.02, r.range(-0.02, 0.02));
            }
        }
    }

    static final class AshFall implements ClientVisual {
        @Override
        public void tick(VisualContext ctx) {
            int n = ctx.particleBudget(clamp(ctx.params.range("rate", ctx.progress, 60), 0, 2000) / 20.0 * ctx.fade * ctx.exposure);
            DeterministicRandom r = new DeterministicRandom(ctx.nowNanos ^ ctx.seed());
            for (int i = 0; i < n; i++) {
                ctx.director.spawn(i % 3 == 0 ? ParticleTypes.WHITE_ASH : ParticleTypes.ASH, ctx.camera.x + r.range(-16, 16),
                        ctx.camera.y + r.range(2, 14), ctx.camera.z + r.range(-16, 16), 0, -0.05, 0);
            }
        }
    }

    static final class Debris implements ClientVisual {
        @Override
        public void tick(VisualContext ctx) {
            int n = ctx.particleBudget(clamp(ctx.params.range("rate", ctx.progress, 30), 0, 2000) / 20.0 * ctx.fade);
            if (n <= 0 || ctx.distance > 192) {
                return;
            }
            BlockParticleOption options = new BlockParticleOption(ParticleTypes.BLOCK, block(ctx.params.string("block", "minecraft:stone")));
            DeterministicRandom r = new DeterministicRandom(ctx.nowNanos ^ ctx.seed());
            Vec3 c = ctx.instance.center;
            for (int i = 0; i < n; i++) {
                double a = r.range(0, Math.PI * 2), d = r.range(0, 24);
                double x = c.x + Math.cos(a) * d, z = c.z + Math.sin(a) * d;
                ctx.director.spawn(options, x, groundY(ctx, x, z, c.y) + 0.2, z, Math.cos(a) * 0.2, r.range(0.3, 0.8), Math.sin(a) * 0.2);
            }
        }
    }

    /** Anillo que se expande desde el centro, dibujado en el mundo, con polvo donde toca el suelo. */
    static final class Shockwave implements ClientVisual {
        @Override
        public void frame(VisualContext ctx, FrameState f) {
            double radius = clamp(ctx.params.range("radius", ctx.progress, 64), 0, 1024);
            int color = ctx.params.color("color", 0xFFE0B0);
            float a = (float) ((1 - ctx.progress) * 0.8 * ctx.fade);
            Vec3 c = ctx.instance.center;
            int segments = ctx.quality.ordinal() <= 1 ? 24 : 48;
            for (int i = 0; i < segments; i++) {
                double a0 = i * Math.PI * 2 / segments, a1 = (i + 1) * Math.PI * 2 / segments;
                f.line(c.x + Math.cos(a0) * radius, c.y + 1, c.z + Math.sin(a0) * radius,
                        c.x + Math.cos(a1) * radius, c.y + 1, c.z + Math.sin(a1) * radius, color, a, 3);
            }
        }

        @Override
        public void tick(VisualContext ctx) {
            double radius = clamp(ctx.params.range("radius", ctx.progress, 64), 0, 1024);
            int n = ctx.particleBudget(Math.min(40, radius));
            DeterministicRandom r = new DeterministicRandom(ctx.nowNanos);
            Vec3 c = ctx.instance.center;
            for (int i = 0; i < n; i++) {
                double a = r.range(0, Math.PI * 2);
                double x = c.x + Math.cos(a) * radius, z = c.z + Math.sin(a) * radius;
                ctx.director.spawn(ParticleTypes.POOF, x, groundY(ctx, x, z, c.y) + 0.3, z, Math.cos(a) * 0.1, 0.05, Math.sin(a) * 0.1);
            }
        }
    }

    /** Estrellas fugaces solo visuales, generadas por franjas de tiempo (sin estado). */
    static final class MeteorTrail implements ClientVisual {
        @Override
        public void frame(VisualContext ctx, FrameState f) {
            if (ctx.instance.def.id().equals("liveevents:meteor_shower")) return;
            double rate = clamp(ctx.params.range("rate", ctx.progress, 1), 0, 30);
            if (rate <= 0) {
                return;
            }
            int color = ctx.params.color("color", 0xFF7A1A), core = ctx.params.color("core", 0xFFF4D6);
            double slot = 1.0 / rate; // segundos por estela
            double now = ctx.seconds();
            long first = (long) Math.floor((now - 1.5) / slot);
            for (long k = first; k <= (long) Math.floor(now / slot); k++) {
                DeterministicRandom r = new DeterministicRandom(DeterministicRandom.derive(ctx.seed(), k));
                double start = k * slot + r.range(0, slot);
                double life = r.range(0.6, 1.4);
                double age = (now - start) / life;
                if (age < 0 || age > 1) {
                    continue;
                }
                double az = r.range(0, 360), alt = r.range(25, 75), dir = r.range(0, Math.PI * 2), len = r.range(10, 30);
                double headAz = az + Math.cos(dir) * len * age, headAlt = alt + Math.sin(dir) * len * age * 0.5 - age * 6;
                float a = (float) (Math.sin(age * Math.PI) * ctx.fade);
                double back = Math.max(0, age - 0.5) / Math.max(0.01, age);
                double tailAz = az + (headAz - az) * back, tailAlt = alt + (headAlt - alt) * back;
                f.skyLine(tailAz, tailAlt, headAz, headAlt, color, a * 0.8f, 3);
                f.skyLine(tailAz, tailAlt, headAz, headAlt, core, a * 0.6f, 1);
                f.sky(Sprite.GLOW, headAz, headAlt, 2.2f, core, a);
            }
        }
    }

    /** Screen effects and budgeted particles consume server-fixed impacts. */
    static final class MeteorImpacts implements ClientVisual {
        static final class State {
            final java.util.Set<String> heard = new java.util.HashSet<>();
            final java.util.Set<String> warned = new java.util.HashSet<>();
            final java.util.Set<String> flashed = new java.util.HashSet<>();
        }
        @Override public void frame(VisualContext ctx, FrameState f) {
            State state = ctx.instance.state(ctx.spec, State::new);
            for (var m : ctx.instance.meteors.values()) {
                if (m.module() != ctx.spec.index()) continue;
                double age = ctx.t - m.tick();
                if (age < 0 || age > 20) continue;
                double distance = ctx.camera.distanceTo(new Vec3(m.x(),m.y(),m.z()));
                double near = clamp(1-distance/96,0,1);
                f.addShake((float)(near*(m.big()?2:1.2)*(1-age/20)),14,0);
                if (state.flashed.add(m.key()) && age < 4 && near>0.5 && ctx.requestFlash(0.15,0xFFE0B0))
                    f.addFlash(0xFFE0B0,(float)(0.2*near));
            }
        }
        @Override public void tick(VisualContext ctx) {
            State state = ctx.instance.state(ctx.spec, State::new);
            for (var m : ctx.instance.meteors.values()) {
                if (m.module()!=ctx.spec.index()) continue;
                double remaining=m.tick()-ctx.t;
                double distance=ctx.camera.distanceTo(new Vec3(m.x(),m.y(),m.z()));
                if(remaining>0 && remaining<=40 && state.warned.add(m.key()) && distance<96)
                    ctx.director.playSoundAt("liveevents:meteor.whistle",(float)(0.8*ctx.exposure),0.8f,0,m.x(),m.y(),m.z());
                if(remaining<=0 && state.heard.add(m.key()) && remaining>-4) {
                    String sound=m.surface().equals("water")?"minecraft:entity.generic.splash":m.surface().equals("snow")?"minecraft:block.powder_snow.break":distance<64?"liveevents:meteor.impact_near":"liveevents:meteor.impact_far";
                    ctx.director.playSoundAt(sound,(float)(clamp(2-distance/200,0.2,2)*ctx.exposure),1,VisualDirector.soundDelaySeconds(distance),m.x(),m.y(),m.z());
                }
                if(remaining<0 && remaining>-400 && distance<192) {
                    int n=ctx.director.particleBudget(remaining>-40?4:1,distance);
                    DeterministicRandom r=new DeterministicRandom(m.seed()+(long)ctx.t);
                    for(int j=0;j<n;j++) {
                        var particle=m.surface().equals("water")?ParticleTypes.SPLASH:m.surface().equals("snow")?ParticleTypes.SNOWFLAKE:remaining>-60?ParticleTypes.FLAME:ParticleTypes.CAMPFIRE_COSY_SMOKE;
                        ctx.director.spawn(particle,m.x()+r.range(-m.radius(),m.radius()),m.y()+0.1,m.z()+r.range(-m.radius(),m.radius()),r.range(-0.03,0.03),r.range(0.02,0.15),0);
                    }
                }
            }
        }
    }

    /**
     * Parte de cliente de seismic_wave: cada cliente calcula su retraso. Primero la onda P (sacudida vertical seca) y
     * después la S (horizontal y violenta), con amplitud según la distancia al epicentro.
     */
    static final class SeismicWave implements ClientVisual {
        @Override
        public void frame(VisualContext ctx, FrameState f) {
            ModuleParams p = ctx.params;
            double radius = Math.max(8, p.number("radius", 160));
            double d = ctx.distance;
            if (d > radius * 1.5) {
                return;
            }
            double local = ctx.t - ctx.spec.fromTicks();
            double pArrive = d / clamp(p.number("p_speed", 120), 1, 2000) * 20;
            double sArrive = d / clamp(p.number("s_speed", 60), 1, 2000) * 20;
            double falloff = clamp(1 - d / (radius * 1.5), 0, 1);
            double amp = clamp(p.range("amplitude", ctx.progress, 1), 0, 5) * falloff;
            if (local >= pArrive && local < pArrive + 16) {
                f.addShake((float) (amp * 0.6 * (1 - (local - pArrive) / 16)), 16, 1);
            }
            if (local >= sArrive) {
                double ramp = clamp((local - sArrive) / 20, 0, 1);
                f.addShake((float) (amp * ramp * ctx.envelope(0, 3)), 7, 2);
            }
        }

        @Override
        public void tick(VisualContext ctx) {
            double sArrive = ctx.distance / clamp(ctx.params.number("s_speed", 60), 1, 2000) * 20;
            if (ctx.t - ctx.spec.fromTicks() < sArrive) {
                return;
            }
            int n = ctx.particleBudget(8 * ctx.fade);
            DeterministicRandom r = new DeterministicRandom(ctx.nowNanos);
            for (int i = 0; i < n; i++) {
                double x = ctx.camera.x + r.range(-10, 10), z = ctx.camera.z + r.range(-10, 10);
                ctx.director.spawn(ParticleTypes.WHITE_ASH, x, groundY(ctx, x, z, ctx.camera.y) + 0.1, z, 0, r.range(0.05, 0.2), 0);
            }
        }
    }
}
