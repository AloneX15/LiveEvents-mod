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
            int n = ctx.particleBudget(clamp(ctx.params.range("rate", ctx.progress, 60), 0, 2000) / 20.0 * ctx.fade);
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

    /** Parte de cliente de meteor_impacts: aviso en el suelo, bólido, impacto, temblor y sonido con retraso. */
    static final class MeteorImpacts implements ClientVisual {
        static final class State {
            List<MeteorPlan.Impact> plan;
            int nextImpact;
            int nextWarning;
        }

        static List<MeteorPlan.Impact> plan(VisualContext ctx) {
            ModuleParams p = ctx.params;
            double[] crater = p.bounds("crater_radius", 3);
            if (p.json().has("crater_radius") && p.json().get("crater_radius").isJsonObject()) {
                crater = new double[]{p.range("crater_radius", 0, 2), p.range("crater_radius", 1, 5)};
            }
            return MeteorPlan.generate(ctx.seed(), p.integer("count", 40), Math.max(8, p.number("radius", 128)),
                    ctx.spec.fromTicks(), ctx.spec.toTicks(), crater[0], crater[1], p.bool("first_big", false));
        }

        @Override
        public void frame(VisualContext ctx, FrameState f) {
            State s = ctx.instance.state(ctx.spec, State::new);
            if (s.plan == null) {
                s.plan = plan(ctx);
            }
            Vec3 c = ctx.instance.center;
            double fall = 60; // ticks de caída visibles
            for (MeteorPlan.Impact impact : s.plan) {
                double remaining = impact.tick() - ctx.t;
                double ix = c.x + impact.dx(), iz = c.z + impact.dz();
                if (remaining > 0 && remaining < fall) {
                    double az = Math.toRadians(impact.entryAzimuth()), el = Math.toRadians(impact.entryAngle());
                    double dx = Math.sin(az) * Math.cos(el), dy = Math.sin(el), dz = -Math.cos(az) * Math.cos(el);
                    double speed = impact.big() ? 5 : 7; // bloques por tick
                    double iy = groundY(ctx, ix, iz, c.y);
                    double hx = ix + dx * remaining * speed, hy = iy + dy * remaining * speed, hz = iz + dz * remaining * speed;
                    double tail = Math.min(remaining + 25, fall) * speed;
                    int color = impact.big() && (int) (ctx.t / 4) % 3 == 0 ? 0x7CFF6B : 0xFF7A1A;
                    f.line(hx, hy, hz, hx + dx * tail, hy + dy * tail, hz + dz * tail, color, 0.85f * (float) ctx.fade, impact.big() ? 9 : 5);
                    f.line(hx, hy, hz, hx + dx * tail * 0.3, hy + dy * tail * 0.3, hz + dz * tail * 0.3, 0xFFF4D6, (float) ctx.fade, impact.big() ? 4 : 2);
                } else if (remaining <= 0 && remaining > -20) {
                    // Destello y temblor del impacto según la distancia
                    double d = Math.hypot(ctx.camera.x - ix, ctx.camera.z - iz);
                    double near = clamp(1 - d / 96, 0, 1);
                    f.addShake((float) (near * (impact.big() ? 2.0 : 1.2) * (1 + remaining / 20)), 14, 0);
                    if (near > 0.5) {
                        f.addFlash(0xFFE0B0, (float) (0.25 * near * (1 + remaining / 20)));
                    }
                }
            }
        }

        @Override
        public void tick(VisualContext ctx) {
            State s = ctx.instance.state(ctx.spec, State::new);
            if (s.plan == null) {
                s.plan = plan(ctx);
            }
            Vec3 c = ctx.instance.center;
            double warning = Math.max(0, ctx.params.ticks("warning", 30));
            // Aviso: círculo de brasas en el suelo
            for (MeteorPlan.Impact impact : s.plan) {
                double remaining = impact.tick() - ctx.t;
                if (remaining > 0 && remaining <= warning) {
                    double ix = c.x + impact.dx(), iz = c.z + impact.dz();
                    if (Math.hypot(ctx.camera.x - ix, ctx.camera.z - iz) > 128) {
                        continue;
                    }
                    double iy = groundY(ctx, ix, iz, c.y) + 0.1;
                    int n = ctx.particleBudget(6);
                    double r = impact.craterRadius() + 1;
                    for (int k = 0; k < n; k++) {
                        double a = (ctx.t * 0.3 + k * Math.PI * 2 / Math.max(1, n));
                        ctx.director.spawn(ParticleTypes.FLAME, ix + Math.cos(a) * r, iy, iz + Math.sin(a) * r, 0, 0.01, 0);
                    }
                }
            }
            while (s.nextImpact < s.plan.size() && s.plan.get(s.nextImpact).tick() <= ctx.t) {
                MeteorPlan.Impact impact = s.plan.get(s.nextImpact++);
                if (ctx.t - impact.tick() > 20) {
                    continue; // entró tarde: no repetir impactos pasados
                }
                double ix = c.x + impact.dx(), iz = c.z + impact.dz();
                double iy = groundY(ctx, ix, iz, c.y);
                double d = Math.sqrt(ctx.camera.distanceToSqr(ix, iy, iz));
                if (d < 160) {
                    int n = ctx.particleBudget(impact.big() ? 60 : 30);
                    ctx.director.spawn(ParticleTypes.EXPLOSION_EMITTER, ix, iy + 1, iz, 0, 0, 0);
                    DeterministicRandom r = new DeterministicRandom(impact.index());
                    for (int k = 0; k < n; k++) {
                        double a = r.range(0, Math.PI * 2);
                        ctx.director.spawn(k % 3 == 0 ? ParticleTypes.LAVA : ParticleTypes.LARGE_SMOKE, ix, iy + 0.5, iz,
                                Math.cos(a) * r.range(0.1, 0.5), r.range(0.2, 0.9), Math.sin(a) * r.range(0.1, 0.5));
                    }
                }
                String sound = d < 64 ? "liveevents:meteor.impact_near" : "liveevents:meteor.impact_far";
                ctx.director.playSoundAt(sound, (float) clamp(2.0 - d / 200, 0.2, 2.0), 1.0f, VisualDirector.soundDelaySeconds(d), ix, iy, iz);
            }
            // Silbido de los que van a caer cerca
            if (s.nextWarning < s.plan.size()) {
                MeteorPlan.Impact next = s.plan.get(s.nextWarning);
                if (next.tick() - ctx.t <= 40) {
                    s.nextWarning++;
                    double ix = c.x + next.dx(), iz = c.z + next.dz();
                    if (Math.hypot(ctx.camera.x - ix, ctx.camera.z - iz) < 96) {
                        ctx.director.playSound("liveevents:meteor.whistle", 0.8f, 0.8f, 0, "listener", ctx);
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
