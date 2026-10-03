package com.takumistudios.liveevents.server.integration;

import com.takumistudios.liveevents.core.definition.PhaseDef;
import com.takumistudios.liveevents.core.time.Durations;
import com.takumistudios.liveevents.server.LiveEventsServer;
import com.takumistudios.liveevents.server.instance.EventInstance;
import com.takumistudios.liveevents.server.schedule.ScheduleService;
import com.takumistudios.liveevents.util.Texts;
import eu.pb4.placeholders.api.PlaceholderResult;
import eu.pb4.placeholders.api.Placeholders;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;

import java.util.Optional;

/**
 * Integración opcional con Text Placeholder API (§25.6). Es la única clase que referencia esa API y solo se carga si
 * el mod está instalado.
 * <ul>
 *   <li>{@code %liveevents:active%}: nombre del evento activo</li>
 *   <li>{@code %liveevents:time_left%}: tiempo restante (mm:ss)</li>
 *   <li>{@code %liveevents:phase%}: título del segmento actual</li>
 *   <li>{@code %liveevents:next_scheduled%}: próximo evento programado</li>
 * </ul>
 */
public final class PlaceholderCompat {
    private PlaceholderCompat() {
    }

    public static void init() {
        Placeholders.registerServer(id("active"), (ctx, arg) -> current()
                .map(i -> PlaceholderResult.value(Texts.of(i.definition().displayName())))
                .orElseGet(() -> PlaceholderResult.value(Component.translatable("liveevents.placeholder.none"))));
        Placeholders.registerServer(id("time_left"), (ctx, arg) -> current()
                .map(i -> PlaceholderResult.value(Durations.formatClock(i.durationSeconds() - i.elapsedSeconds())))
                .orElseGet(() -> PlaceholderResult.value("--:--")));
        Placeholders.registerServer(id("phase"), (ctx, arg) -> current()
                .flatMap(i -> i.definition().phaseAt(i.eventTicks()).map(PhaseDef::title))
                .map(t -> PlaceholderResult.value(Texts.of(t)))
                .orElseGet(() -> PlaceholderResult.value("")));
        Placeholders.registerServer(id("next_scheduled"), (ctx, arg) -> {
            if (!LiveEventsServer.active()) {
                return PlaceholderResult.value("");
            }
            Optional<ScheduleService.Entry> next = LiveEventsServer.get().schedule().next();
            return next.map(e -> PlaceholderResult.value(e.event_id + " @ " + e.time.replace('T', ' ')))
                    .orElseGet(() -> PlaceholderResult.value(Component.translatable("liveevents.placeholder.none")));
        });
    }

    private static Optional<EventInstance> current() {
        if (!LiveEventsServer.active()) {
            return Optional.empty();
        }
        return LiveEventsServer.get().director().active().stream().filter(i -> !i.preview()).findFirst();
    }

    private static Identifier id(String path) {
        return Identifier.fromNamespaceAndPath("liveevents", path);
    }
}
