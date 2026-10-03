package com.takumistudios.liveevents.client.visual;

import com.takumistudios.liveevents.client.visual.FrameState.SkyObject;
import com.takumistudios.liveevents.client.visual.FrameState.Sprite;
import com.takumistudios.liveevents.core.definition.ModuleParams;
import com.takumistudios.liveevents.core.random.DeterministicRandom;
import com.takumistudios.liveevents.core.safety.FlashLimiter;
import com.takumistudios.liveevents.core.timeline.Curve;

import java.util.List;

/** Visuales anclados a la bóveda celeste (HUD proyectado: funcionan igual con Sodium e Iris). */
final class SkyVisuals {
    private SkyVisuals() {
    }

    static double clamp(double v, double min, double max) {
        return Math.max(min, Math.min(max, v));
    }

    static int lerpColor(int a, int b, double t) {
        double k = clamp(t, 0, 1);
        int r = (int) (((a >> 16) & 0xFF) + (((b >> 16) & 0xFF) - ((a >> 16) & 0xFF)) * k);
        int g = (int) (((a >> 8) & 0xFF) + (((b >> 8) & 0xFF) - ((a >> 8) & 0xFF)) * k);
        int bl = (int) ((a & 0xFF) + ((b & 0xFF) - (a & 0xFF)) * k);
        return (r << 16) | (g << 8) | bl;
    }

    /** Color de una lista según el progreso (pasa por todos en orden). */
    static int gradient(List<Integer> colors, double t) {
        if (colors.isEmpty()) {
            return 0xFFFFFF;
        }
        if (colors.size() == 1) {
            return colors.get(0);
        }
        double x = clamp(t, 0, 1) * (colors.size() - 1);
        int i = (int) Math.min(colors.size() - 2, Math.floor(x));
        return lerpColor(colors.get(i), colors.get(i + 1), x - i);
    }

    // ------------------------------------------------------------------------------------------------ tinte

    static final class SkyTint implements ClientVisual {
        @Override
        public void frame(VisualContext ctx, FrameState f) {
            ModuleParams p = ctx.params;
            double fade = Math.max(0.05, p.ticks("fade", 20) / 20.0);
            float a = (float) (clamp(p.range("strength", ctx.progress, 0.6), 0, 1) * ctx.envelope(fade, fade));
            f.tintSky(p.color("color", 0x0A0E1A), a);
            if (p.has("horizon")) {
                f.tintHorizon(p.color("horizon", 0xFF00FF), a);
            }
        }
    }

    static final class Blackout implements ClientVisual {
        @Override
        public void frame(VisualContext ctx, FrameState f) {
            f.blackout = (float) Math.max(f.blackout, clamp(ctx.params.number("strength", 0.95), 0, 1) * ctx.envelope(0.05, 0.1));
        }
    }

    /** Fuerza la noche en la vista del cliente (fundido de entrada y salida). */
    static final class TimeOverride implements ClientVisual {
        @Override
        public void frame(VisualContext ctx, FrameState f) {
            double fade = Math.max(0.1, ctx.params.ticks("fade", 60) / 20.0);
            float a = (float) ctx.envelope(fade, fade);
            String mode = ctx.params.string("mode", "night");
            f.night = Math.max(f.night, mode.equals("dark") ? a : a * 0.75f);
        }
    }

    /** Ilumina el mundo como de día (capa cálida aditiva) — la hora del servidor no cambia. */
    static final class ArtificialDay implements ClientVisual {
        @Override
        public void frame(VisualContext ctx, FrameState f) {
            f.addDay(ctx.params.color("color", 0xFFF6E0), (float) (0.35 * ctx.envelope(1.0, 2.0)));
            f.night = 0; // "no hay noche"
        }
    }

    /** Sin shaders no hay sombras dinámicas: se sustituye por un parpadeo de luz lento sincronizado. */
    static final class ShadowWhip implements ClientVisual {
        @Override
        public void frame(VisualContext ctx, FrameState f) {
            double speed = clamp(ctx.params.number("speed", 1), 0, 10);
            double hz = Math.min(FlashLimiter.MAX_FLASHES_PER_SECOND, 0.5 + speed * 0.3);
            double wave = 0.5 + 0.5 * Math.sin(ctx.nowNanos / 1e9 * Math.PI * 2 * hz);
            f.tintLight(0xFFFFFF, (float) (0.06 * wave * ctx.envelope(0.3, 0.3)));
        }
    }

    // ------------------------------------------------------------------------------------------------ cuerpos

    /** Estado de parpadeo de un cuerpo celeste (frecuencia aleatoria dentro del rango, cambia cada segundo). */
    static final class CelestialBody implements ClientVisual {
        @Override
        public void frame(VisualContext ctx, FrameState f) {
            ModuleParams p = ctx.params;
            double size = clamp(p.range("size_deg", ctx.progress, 2), 0.1, 120);
            String shape = p.string("shape", "star");
            int color = p.color("color", 0xFFFFFF);
            int core = p.color("core", lerpColor(color, 0xFFFFFF, 0.6));
            double fade = ctx.envelope(0.4, 0.2);
            // Colapso: se encoge con aceleración hasta desaparecer
            if (p.has("collapse")) {
                ModuleParams c = p.child("collapse");
                double at = c.ticks("at", ctx.spec.toTicks()), dur = Math.max(1, c.ticks("duration", 20));
                if (ctx.t >= at) {
                    double k = Curve.parse(c.string("curve", "ease_in"), Curve.EASE_IN).apply((ctx.t - at) / dur);
                    size *= 1 - k;
                    fade *= 1 - k * 0.6;
                }
            }
            if (size <= 0.02) {
                return;
            }
            // Parpadeo: frecuencia dentro del rango, cambia cada segundo de forma determinista
            double[] hzRange = p.bounds("flicker_hz", 0);
            double flicker = 1;
            if (hzRange[1] > 0) {
                long second = (long) Math.floor(ctx.seconds());
                double hz = new DeterministicRandom(DeterministicRandom.derive(ctx.seed(), second)).range(hzRange[0], hzRange[1]);
                double area = Math.pow(size * 3 / 70.0, 2); // fracción aproximada de pantalla
                hz = FlashLimiter.maxBlinkHz(area, hz);
                flicker = 0.75 + 0.25 * Math.sin(ctx.nowNanos / 1e9 * Math.PI * 2 * hz);
            }
            double az = ctx.anchorAzimuth(), alt = ctx.anchorAltitude();
            float a = (float) (fade * flicker);
            float bloom = 1 + f.bloom * 0.5f;
            f.sky(Sprite.GLOW, az, alt, (float) (size * 9 * bloom), color, a * 0.45f);
            f.sky(Sprite.GLOW, az, alt, (float) (size * 4 * bloom), color, a * 0.75f);
            if (shape.equals("sun")) {
                int corona = p.color("corona", 0xFFD700);
                f.sky(Sprite.GLOW, az, alt, (float) (size * 2.2 * bloom), corona, a * 0.85f);
                SkyObject ring = f.sky(Sprite.RING, az, alt, (float) (size * 1.6), corona, a * 0.5f);
                if (ring != null) {
                    ring.rotation = (float) (ctx.nowNanos / 1e9 * 10 % 360);
                }
                f.sky(Sprite.CORE, az, alt, (float) size, core, a);
            } else if (shape.equals("boiling")) {
                // "Mercurio hirviendo": varias gotas que se deforman con ruido alrededor del centro
                long seed = ctx.seed();
                double time = ctx.nowNanos / 1e9;
                for (int i = 0; i < 6; i++) {
                    double ox = DeterministicRandom.noise1(seed + i, time * 1.3) * size * 0.25;
                    double oy = DeterministicRandom.noise1(seed + 50 + i, time * 1.1) * size * 0.25;
                    double s = size * (0.55 + 0.25 * DeterministicRandom.noise1(seed + 99 + i, time * 2));
                    f.sky(Sprite.CORE, az + ox, alt + oy, (float) s, i == 0 ? core : color, a * 0.8f);
                }
            } else {
                f.sky(Sprite.CORE, az, alt, (float) size, core, a);
            }
            if (p.bool("flares", false)) {
                long seed = ctx.seed();
                double time = ctx.nowNanos / 1e9;
                for (int i = 0; i < 5; i++) {
                    double life = (time * 0.7 + i * 0.37) % 1.0; // cada llamarada nace y muere
                    double angle = Math.toRadians(new DeterministicRandom(DeterministicRandom.derive(seed, (long) (time * 0.7 + i * 0.37) * 7 + i)).range(0, 360));
                    double reach = size * (0.8 + life * 2.5);
                    double ez = az + Math.cos(angle) * reach / Math.max(0.3, Math.cos(Math.toRadians(alt))), ea = alt + Math.sin(angle) * reach;
                    f.skyLine(az, alt, ez, ea, color, (float) (a * (1 - life) * 0.9), 4);
                }
            }
        }
    }

    static final class StarPoint implements ClientVisual {
        @Override
        public void frame(VisualContext ctx, FrameState f) {
            double size = clamp(ctx.params.range("size_deg", ctx.progress, 1), 0.1, 30);
            int color = ctx.params.color("color", 0xFFFFFF);
            float a = (float) ctx.envelope(0.5, 0.5);
            f.sky(Sprite.GLOW, ctx.anchorAzimuth(), ctx.anchorAltitude(), (float) (size * 5), color, a * 0.5f);
            f.sky(Sprite.CORE, ctx.anchorAzimuth(), ctx.anchorAltitude(), (float) size, 0xFFFFFF, a);
        }
    }

    // ------------------------------------------------------------------------------------------------ bóveda rota

    /** Grietas luminosas que se extienden desde el ancla (Voronoi aproximado con ramas deterministas). */
    static final class SkyShatter implements ClientVisual {
        @Override
        public void frame(VisualContext ctx, FrameState f) {
            ModuleParams p = ctx.params;
            int shards = (int) clamp(p.number("shards", 200), 8, 600);
            int cracks = Math.min(48, Math.max(8, shards / 5));
            if (ctx.quality.ordinal() <= 1) {
                cracks /= 2;
            }
            double spread = Math.max(1, p.ticks("spread", 30));
            double grow = clamp((ctx.t - ctx.spec.fromTicks()) / spread, 0, 1);
            int color = p.color("crack_color", 0x00FFFF);
            float a = (float) ctx.envelope(0.0, 1.5);
            DeterministicRandom r = new DeterministicRandom(ctx.seed());
            double az0 = ctx.anchorAzimuth(), alt0 = ctx.anchorAltitude();
            for (int i = 0; i < cracks; i++) {
                double dir = r.range(0, Math.PI * 2);
                double length = r.range(25, 110) * grow;
                double az = az0, alt = alt0;
                int segments = 4;
                for (int s = 0; s < segments; s++) {
                    double segLen = length / segments;
                    dir += r.range(-0.5, 0.5);
                    double naz = az + Math.cos(dir) * segLen / Math.max(0.3, Math.cos(Math.toRadians(alt)));
                    double nalt = clamp(alt + Math.sin(dir) * segLen, -5, 89);
                    f.skyLine(az, alt, naz, nalt, color, a, s == 0 ? 3 : 2);
                    f.skyLine(az, alt, naz, nalt, 0xFFFFFF, a * 0.6f, 1);
                    az = naz;
                    alt = nalt;
                }
            }
            // El haz de rayos X durante el primer medio segundo
            if (ctx.t - ctx.spec.fromTicks() < 10) {
                f.skyLine(az0, alt0, az0, 0, 0xE0FFFF, 0.9f * (float) ctx.fade, 4);
            }
            f.sky(Sprite.GLOW, az0, alt0, (float) (20 + 40 * grow), color, a * 0.35f);
        }
    }

    /** Anillos de vapor que salen del ancla cada intervalo y recorren la bóveda. */
    static final class AtmoRings implements ClientVisual {
        @Override
        public void frame(VisualContext ctx, FrameState f) {
            ModuleParams p = ctx.params;
            double interval = Math.max(10, p.ticks("interval", 50));
            double speed = clamp(p.number("speed_deg", 45), 1, 360);
            int color = p.color("color", 0xFFFFFF);
            double local = ctx.t - ctx.spec.fromTicks();
            for (int k = 0; k < 6; k++) {
                double age = (local - Math.floor(local / interval) * interval + k * interval) / 20.0;
                double radius = age * speed;
                if (age < 0 || radius > 170) {
                    continue;
                }
                float a = (float) ((1 - radius / 170) * 0.9 * ctx.envelope(0.2, 1.0));
                f.sky(Sprite.RING, ctx.anchorAzimuth(), ctx.anchorAltitude(), (float) (radius * 2), color, a);
                f.sky(Sprite.RING, ctx.anchorAzimuth(), ctx.anchorAltitude(), (float) (radius * 2 * 0.97), 0xFFFFFF, a * 0.5f);
            }
            f.glitch = Math.max(f.glitch, (float) (clamp(p.number("distortion", 0.5), 0, 1) * 0.08 * ctx.envelope(0.2, 1)));
        }
    }

    /** Esfera de plasma con filamentos que crece y cambia de color. */
    static final class PlasmaShell implements ClientVisual {
        @Override
        public void frame(VisualContext ctx, FrameState f) {
            ModuleParams p = ctx.params;
            double coverage = clamp(p.range("coverage", ctx.progress, 0.4), 0, 1);
            double size = 10 + coverage * 170;
            int color = gradient(p.colors("colors", List.of(0xFFFFFF, 0xFF4500)), ctx.progress);
            double turb = clamp(p.number("turbulence", 1), 0, 4);
            double time = ctx.nowNanos / 1e9;
            float a = (float) (0.6 * ctx.envelope(1.0, 2.0));
            int layers = ctx.quality.ordinal() <= 0 ? 2 : ctx.quality.ordinal() == 1 ? 3 : 5;
            for (int i = 0; i < layers; i++) {
                SkyObject o = f.sky(i % 2 == 0 ? Sprite.CLOUD : Sprite.FILAMENTS, ctx.anchorAzimuth(), ctx.anchorAltitude(),
                        (float) (size * (0.75 + 0.12 * i)), lerpColor(color, 0xFFFFFF, i == 0 ? 0.2 : 0), a * (i == 0 ? 1f : 0.7f));
                if (o != null) {
                    o.rotation = (float) ((time * (8 + i * 5) * turb * (i % 2 == 0 ? 1 : -1)) % 360);
                }
            }
        }
    }

    /** Cortinas de aurora desde los horizontes norte y sur que se cruzan en el cénit. */
    static final class Aurora implements ClientVisual {
        @Override
        public void frame(VisualContext ctx, FrameState f) {
            ModuleParams p = ctx.params;
            List<Integer> colors = p.colors("colors", List.of(0x39FF14, 0xDC143C));
            double intensity = clamp(p.range("intensity", ctx.progress, 0.7), 0, 1);
            double speed = clamp(p.number("speed", 1), 0, 5);
            double time = ctx.nowNanos / 1e9 * speed;
            int curtains = ctx.quality.ordinal() <= 0 ? 12 : 26;
            float a = (float) (intensity * ctx.envelope(2.0, 3.0));
            for (int side = 0; side < 2; side++) {
                double baseAz = side == 0 ? 0 : 180; // norte y sur
                for (int i = 0; i < curtains; i++) {
                    double u = i / (double) (curtains - 1) - 0.5;
                    double az = baseAz + u * 140 + DeterministicRandom.noise1(ctx.seed() + side * 100 + i, time * 0.2) * 8;
                    double alt = 22 + 14 * Math.sin(time * 0.3 + i * 0.7 + side) + Math.abs(u) * -10;
                    int color = colors.get((i + side) % colors.size());
                    SkyObject o = f.sky(Sprite.AURORA, az, alt, 16, color,
                            (float) (a * (0.5 + 0.5 * Math.sin(time * 0.8 + i * 1.3 + side * 2))));
                    if (o != null) {
                        o.heightDeg = (float) (40 + 12 * Math.sin(time * 0.5 + i));
                    }
                }
            }
            f.tintLight(colors.get(0), (float) (0.04 * a)); // reflejo verde leve en el suelo
        }
    }

    /** Nebulosa por capas que ocupa parte de la bóveda (también es la secuela persistente). */
    static final class Nebula implements ClientVisual {
        @Override
        public void frame(VisualContext ctx, FrameState f) {
            List<Integer> list = ctx.params.colors("colors", List.of(0xFF0000, 0x00CED1, 0xC8B400));
            int[] colors = new int[list.size()];
            for (int i = 0; i < colors.length; i++) {
                colors[i] = list.get(i);
            }
            draw(f, ctx.anchorAzimuth(), ctx.anchorAltitude(), colors,
                    clamp(ctx.params.number("span_deg", 180), 10, 360), (float) (clamp(ctx.params.range("opacity", ctx.progress, 0.6), 0, 1) * ctx.fade),
                    ctx.seed(), ctx.quality.ordinal());
        }

        static void draw(FrameState f, double az0, double alt0, int[] colors, double span, float opacity, long seed, int quality) {
            int blobs = quality <= 0 ? 5 : quality == 1 ? 8 : 14;
            DeterministicRandom r = new DeterministicRandom(seed);
            for (int i = 0; i < blobs; i++) {
                double az = az0 + r.range(-span / 2, span / 2);
                double alt = clamp(alt0 + r.range(-25, 25) * (1 - Math.abs(az - az0) / span), 5, 85);
                int color = colors[i % colors.length];
                SkyObject o = f.sky(i % 3 == 2 ? Sprite.FILAMENTS : Sprite.CLOUD, az, alt, (float) r.range(30, 70), color, opacity * (float) r.range(0.35, 0.7));
                if (o != null) {
                    o.rotation = (float) r.range(0, 360);
                    o.heightDeg = o.widthDeg * (float) r.range(0.5, 0.9);
                }
            }
        }
    }

    /** Púlsar: punto que parpadea (área pequeña) y dos haces de faro que giran sin barrer la cámara más de 3 veces/s. */
    static final class Pulsar implements ClientVisual {
        @Override
        public void frame(VisualContext ctx, FrameState f) {
            draw(f, ctx.anchorAzimuth(), ctx.anchorAltitude(), ctx.params.color("color", 0x7DF9FF), ctx.params.number("hz", 30),
                    ctx.params.number("beam_rpm", 40), (float) ctx.envelope(1, 1), ctx.nowNanos);
        }

        static void draw(FrameState f, double az, double alt, int color, double hz, double rpm, float a, long nowNanos) {
            double time = nowNanos / 1e9;
            double blink = hz <= 0 ? 1 : (Math.sin(time * Math.PI * 2 * FlashLimiter.maxBlinkHz(0.001, clamp(hz, 0, 60))) > 0 ? 1 : 0.25);
            f.sky(Sprite.GLOW, az, alt, 6, color, a * 0.6f);
            f.sky(Sprite.CORE, az, alt, 1.2f, 0xFFFFFF, (float) (a * blink));
            // dos haces por vuelta → cruces por segundo = rpm / 60 * 2 ≤ 3
            double safeRpm = Math.min(clamp(rpm, 0, 180), 90);
            double angle = (time * safeRpm / 60.0 * 360) % 360;
            for (int k = 0; k < 2; k++) {
                SkyObject beam = f.sky(Sprite.BEAM, az, alt, 6, color, a * 0.45f);
                if (beam != null) {
                    beam.heightDeg = 70;
                    beam.rotation = (float) (angle + k * 180);
                    beam.screenRotation = true;
                }
            }
        }
    }

    /** Flares anamórficos horizontales desde el ancla. */
    static final class LensFlare implements ClientVisual {
        @Override
        public void frame(VisualContext ctx, FrameState f) {
            List<Integer> colors = ctx.params.colors("colors", List.of(0x7DF9FF, 0xFF00FF));
            float a = (float) (clamp(ctx.params.range("intensity", ctx.progress, 0.8), 0, 1) * ctx.envelope(0.5, 1));
            for (int i = 0; i < colors.size(); i++) {
                SkyObject o = f.sky(Sprite.FLARE, ctx.anchorAzimuth(), ctx.anchorAltitude() - i * 0.6, 60 + i * 25, colors.get(i), a * (0.8f - i * 0.2f));
                if (o != null) {
                    o.heightDeg = 4;
                }
            }
            f.lensDirt = Math.max(f.lensDirt, a * 0.25f);
        }
    }
}
