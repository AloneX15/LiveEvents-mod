package com.takumistudios.liveevents.client.visual;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.takumistudios.liveevents.core.definition.ModuleParams;
import com.takumistudios.liveevents.core.safety.FlashLimiter;
import com.takumistudios.liveevents.core.time.Durations;

import static com.takumistudios.liveevents.client.visual.SkyVisuals.clamp;
import static com.takumistudios.liveevents.client.visual.SkyVisuals.lerpColor;

/** Efectos de pantalla completa y de cámara. Todos pasan por la regla de destellos y los perfiles. */
final class ScreenVisuals {
    private ScreenVisuals() {
    }

    /** Recuerda si el destello inicial de un módulo se concedió o debe hacerse como fundido. */
    static final class FlashGrant {
        boolean asked;
        boolean granted;

        boolean ask(VisualContext ctx, double area, int color) {
            if (!asked) {
                asked = true;
                granted = ctx.requestFlash(area, color);
            }
            return granted;
        }
    }

    static final class Flash implements ClientVisual {
        @Override
        public void frame(VisualContext ctx, FrameState f) {
            ModuleParams p = ctx.params;
            int color = p.color("color", 0xFFFFFF);
            double peak = clamp(p.number("intensity", 1), 0, 1);
            double fadeTicks = Math.max(1, p.ticks("fade", 12));
            double age = ctx.t - ctx.spec.fromTicks();
            boolean granted = ctx.instance.state(ctx.spec, FlashGrant::new).ask(ctx, 1.0, color);
            double a;
            if (granted) {
                a = peak * Math.max(0, 1 - age / fadeTicks);
            } else {
                // Denegado por la regla de destellos: subida y bajada suaves de 1 s, al 40 %
                double s = age / 20.0;
                a = peak * 0.4 * Math.sin(Math.PI * clamp(s, 0, 1));
            }
            f.addFlash(color, (float) (a * ctx.fade));
        }
    }

    /** Sobreexposición a blanco: pico y sostenido según el perfil (tabla de §6.3). */
    static final class Whiteout implements ClientVisual {
        @Override
        public void frame(VisualContext ctx, FrameState f) {
            ModuleParams p = ctx.params;
            double peak = clamp(p.number("peak", 0.85), 0, 1);
            double sustain = clamp(p.number("sustain", 0.35), 0, 1);
            double peakTicks = Math.max(1, p.ticks("peak_time", 20));
            double age = ctx.t - ctx.spec.fromTicks();
            int color = p.color("color", 0xFFFFFF);
            boolean granted = ctx.instance.state(ctx.spec, FlashGrant::new).ask(ctx, 1.0, color);
            double a;
            if (age < peakTicks) {
                double rise = granted && !ctx.safe() ? 1 : clamp(age / 20.0, 0, 1); // en safe o denegado: fundido de 1 s
                a = peak * rise;
            } else {
                double k = clamp((age - peakTicks) / 30.0, 0, 1);
                a = peak + (sustain - peak) * k;
            }
            a *= ctx.envelope(0, 2.0);
            f.addWhiteout(color, (float) a);
            f.lensDirt = Math.max(f.lensDirt, (float) (a * 0.5));
        }
    }

    /** Cortes de color a golpes (o fundidos): cada corte grande cuenta para la regla de destellos. */
    static final class LightCut implements ClientVisual {
        private static final class State {
            int lastIndex = -2;
            int previousColor;
            int currentColor;
            double transitionStart;
            boolean fadeTransition;
        }

        @Override
        public void frame(VisualContext ctx, FrameState f) {
            ModuleParams p = ctx.params;
            JsonArray cuts = p.array("cuts");
            if (cuts.isEmpty()) {
                return;
            }
            boolean fadeMode = p.string("mode", "cut").equals("fade") || ctx.safe();
            double fadeTicks = Math.max(1, p.ticks("fade", 20));
            int index = -1;
            int color = 0;
            for (int i = 0; i < cuts.size(); i++) {
                JsonElement c = cuts.get(i);
                if (!c.isJsonArray() || c.getAsJsonArray().size() != 2) {
                    continue;
                }
                double at;
                try {
                    at = Durations.parseTicksExact(c.getAsJsonArray().get(0).getAsString());
                } catch (IllegalArgumentException e) {
                    continue;
                }
                if (at <= ctx.t) {
                    index = i;
                    color = p.palette().resolve(c.getAsJsonArray().get(1).getAsString()).orElse(0xFFFFFF);
                }
            }
            if (index < 0) {
                return;
            }
            State s = ctx.instance.state(ctx.spec, State::new);
            if (index != s.lastIndex) {
                s.previousColor = s.lastIndex < 0 ? color : s.currentColor;
                s.currentColor = color;
                s.transitionStart = ctx.t;
                boolean large = true;
                boolean redDanger = FlashLimiter.isSaturatedRed(color) && large;
                // Un corte duro solo si lo permite la regla (y nunca a rojo saturado)
                s.fadeTransition = fadeMode || redDanger || !ctx.requestFlash(1.0, color);
                s.lastIndex = index;
            }
            int shown = s.currentColor;
            if (s.fadeTransition) {
                shown = lerpColor(s.previousColor, s.currentColor, (ctx.t - s.transitionStart) / fadeTicks);
            }
            double strength = clamp(p.number("strength", 0.45), 0, 1);
            f.tintLight(shown, (float) (strength * ctx.envelope(0, 0.3)));
            f.tintSky(shown, (float) (strength * 1.2 * ctx.envelope(0, 0.3)));
        }
    }

    static final class ChromaticGlitch implements ClientVisual {
        @Override
        public void frame(VisualContext ctx, FrameState f) {
            double strength = clamp(ctx.params.number("strength", 0.8), 0, 1);
            if (strength <= 0) {
                return;
            }
            boolean granted = ctx.instance.state(ctx.spec, FlashGrant::new).ask(ctx, 0.5, 0xFFFFFF);
            double a = strength * ctx.envelope(0.03, 0.2) * (granted ? 1 : 0.4);
            f.glitch = Math.max(f.glitch, (float) a);
            f.glitchSlices |= ctx.params.bool("slices", true) && granted;
        }
    }

    static final class Bloom implements ClientVisual {
        @Override
        public void frame(VisualContext ctx, FrameState f) {
            f.bloom = Math.max(f.bloom, (float) (clamp(ctx.params.range("intensity", ctx.progress, 1), 0, 3) * ctx.envelope(0.5, 1)));
        }
    }

    static final class ScreenOverlay implements ClientVisual {
        @Override
        public void frame(VisualContext ctx, FrameState f) {
            ModuleParams p = ctx.params;
            int color = p.color("color", 0x000000);
            double pulseHz = clamp(p.number("pulse_hz", 0), 0, FlashLimiter.MAX_FLASHES_PER_SECOND);
            double pulse = pulseHz <= 0 ? 1 : 0.75 + 0.25 * Math.sin(ctx.nowNanos / 1e9 * Math.PI * 2 * pulseHz);
            double env = ctx.envelope(0.5, 0.5) * pulse;
            f.addVignette(color, (float) (clamp(p.range("vignette", ctx.progress, 0.4), 0, 1) * env));
            f.addTint(color, (float) (clamp(p.range("tint", ctx.progress, 0), 0, 1) * env));
        }
    }

    static final class LightPulse implements ClientVisual {
        @Override
        public void frame(VisualContext ctx, FrameState f) {
            double hz = clamp(ctx.params.number("hz", 1), 0, FlashLimiter.MAX_FLASHES_PER_SECOND);
            double wave = hz <= 0 ? 1 : 0.5 + 0.5 * Math.sin(ctx.nowNanos / 1e9 * Math.PI * 2 * hz);
            f.tintLight(ctx.params.color("color", 0xFFAA55), (float) (clamp(ctx.params.range("intensity", ctx.progress, 0.5), 0, 1) * 0.3 * wave * ctx.envelope(0.3, 0.3)));
        }
    }

    /** Temblor: amplitud según el rango y la distancia (falloff), con el eje indicado. */
    static final class CameraShake implements ClientVisual {
        @Override
        public void frame(VisualContext ctx, FrameState f) {
            ModuleParams p = ctx.params;
            double amp = clamp(p.range("amplitude", ctx.progress, 0.2), 0, 5);
            double falloff = p.number("falloff", 0);
            if (falloff > 0) {
                amp *= clamp(1 - ctx.distance / falloff, 0, 1);
            }
            String axis = p.string("axis", "both");
            f.addShake((float) (amp * ctx.fade), (float) clamp(p.number("frequency", 8), 0.1, 40),
                    axis.equals("vertical") ? 1 : axis.equals("horizontal") ? 2 : 0);
        }
    }
}
