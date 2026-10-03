package com.takumistudios.liveevents.client.render;

import com.takumistudios.liveevents.client.state.ClientEventState;
import com.takumistudios.liveevents.client.state.ClientInstance;
import com.takumistudios.liveevents.client.visual.VisualDirector;
import com.takumistudios.liveevents.core.definition.ModuleSpec;
import com.takumistudios.liveevents.core.definition.PhaseDef;
import com.takumistudios.liveevents.core.time.Durations;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;

import java.util.Locale;

/**
 * Overlay para quien crea eventos (§20.8): segundo actual, fase, módulos activos, perfil, destellos del último segundo
 * y calidad; y, con /le debug perf, el coste del mod en el servidor. Se dibuja encima del HUD.
 */
public final class DebugOverlay {
    private final StringBuilder sb = new StringBuilder(256);

    public void render(GuiGraphicsExtractor g, DeltaTracker delta) {
        ClientEventState state = ClientEventState.get();
        if (!state.debugTimeline() && !state.debugPerf()) {
            return;
        }
        Font font = Minecraft.getInstance().font;
        int y = 4;
        if (state.debugTimeline()) {
            g.text(font, Component.translatable("liveevents.debug.title"), 4, y, 0xFFFFD700, true);
            y += 11;
            if (state.instances().isEmpty()) {
                g.text(font, Component.translatable("liveevents.debug.none"), 4, y, 0xFFAAAAAA, true);
                y += 11;
            }
            for (ClientInstance i : state.instances()) {
                PhaseDef phase = i.def.phaseAt(i.t).orElse(null);
                sb.setLength(0);
                sb.append('#').append(i.id).append(' ').append(i.def.id()).append("  ")
                        .append(Durations.formatClock(i.t / 20)).append('/').append(Durations.formatClock(i.def.durationTicks() / 20))
                        .append(String.format(Locale.ROOT, " (%.1fs)", i.t / 20)).append("  ").append(i.profile.id())
                        .append(i.clock.paused() ? "  PAUSED" : "").append(i.preview ? "  PREVIEW" : "");
                g.text(font, sb.toString(), 4, y, 0xFFFFFFFF, true);
                y += 10;
                if (phase != null) {
                    g.text(font, "  ▶ " + phase.id(), 4, y, 0xFF55FFFF, true);
                    y += 10;
                }
                sb.setLength(0);
                sb.append("  ");
                int shown = 0;
                for (ModuleSpec m : i.def.allModules()) {
                    if (m.activeAt(i.t) && shown < 8) {
                        sb.append(m.shortType()).append(m.side().onServer() && m.side().onClient() ? "*" : m.side().onServer() ? "ˢ" : "").append(' ');
                        shown++;
                    }
                }
                g.text(font, sb.toString(), 4, y, 0xFFBBBBBB, true);
                y += 11;
            }
            g.text(font, String.format(Locale.ROOT, "flashes/s: %d  quality: %s  exposure: %.2f",
                    VisualDirector.get().recentFlashes(), VisualDirector.get().quality(), VisualDirector.get().frame().exposure), 4, y, 0xFFAAAAAA, true);
            y += 12;
        }
        if (state.debugPerf() && !state.perfText().isEmpty()) {
            g.text(font, state.perfText(), 4, y, 0xFF88FF88, true);
        }
    }
}
