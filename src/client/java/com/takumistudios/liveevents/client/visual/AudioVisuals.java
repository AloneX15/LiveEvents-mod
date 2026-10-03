package com.takumistudios.liveevents.client.visual;

import com.takumistudios.liveevents.core.definition.ModuleParams;

import static com.takumistudios.liveevents.client.visual.SkyVisuals.clamp;

/** Sonido de los eventos: puntual con retraso por distancia, en bucle con ritmo y silencios (audio duck). */
final class AudioVisuals {
    private AudioVisuals() {
    }

    /** Un sonido puntual se dispara una vez al cruzar su inicio (si se entra tarde, no se reproduce). */
    static final class Fired {
        boolean done;
    }

    static final class Sound implements ClientVisual {
        @Override
        public void tick(VisualContext ctx) {
            Fired fired = ctx.instance.state(ctx.spec, Fired::new);
            if (fired.done) {
                return;
            }
            fired.done = true;
            if (ctx.t - ctx.spec.fromTicks() > 20) {
                return; // entró a mitad de evento: no se reproducen sonidos ya pasados
            }
            ModuleParams p = ctx.params;
            String position = p.string("position", "listener");
            double delay = 0;
            if (position.equals("center") && p.bool("delay_by_distance", true)) {
                delay = VisualDirector.soundDelaySeconds(ctx.distance);
            }
            ctx.director.playSound(p.string("sound", ""), (float) clamp(p.number("volume", 1), 0, 4),
                    (float) clamp(p.number("pitch", 1), 0.5, 2), delay, position, ctx);
        }
    }

    static final class SoundLoop implements ClientVisual {
        static final class Beat {
            double next = Double.NaN;
        }

        @Override
        public void tick(VisualContext ctx) {
            ModuleParams p = ctx.params;
            Beat beat = ctx.instance.state(ctx.spec, Beat::new);
            if (Double.isNaN(beat.next) || beat.next < ctx.t - 40) {
                beat.next = ctx.t; // al entrar o tras un salto, retoma el ritmo desde ahora
            }
            int guard = 0;
            while (ctx.t >= beat.next && guard++ < 4) {
                double bpm = clamp(p.range("bpm", ctx.progress, 60), 1, 600);
                float volume = (float) (clamp(p.range("volume", ctx.progress, 1), 0, 4) * ctx.fade);
                if (volume > 0.01f) {
                    ctx.director.playSound(p.string("sound", ""), volume, (float) clamp(p.range("pitch", ctx.progress, 1), 0.5, 2),
                            0, "listener", ctx);
                }
                beat.next += 1200.0 / bpm;
            }
        }
    }

    static final class AudioDuck implements ClientVisual {
        @Override
        public void frame(VisualContext ctx, FrameState f) {
            f.duck = Math.min(f.duck, (float) clamp(ctx.params.number("level", 0.1), 0, 1));
        }
    }
}
