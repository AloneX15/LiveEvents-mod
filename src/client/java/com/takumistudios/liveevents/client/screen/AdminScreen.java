package com.takumistudios.liveevents.client.screen;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.takumistudios.liveevents.core.time.Durations;
import com.takumistudios.liveevents.util.Texts;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Panel de administración (§25.1). <b>Por dentro solo envía comandos normales</b> ({@code /le start …}, {@code /le
 * panic}…), que el servidor comprueba con permisos: no añade ningún poder nuevo. El botón de pánico está siempre visible.
 */
public final class AdminScreen extends Screen {
    private static final String[] PROFILES = {"gameplay", "safe", "cinematic"};
    private static final int[] DELAYS = {0, 5, 10, 30, 60};
    private static final int PER_PAGE = 6;

    private record EventEntry(String id, String name, String description, String scope, double duration) {
    }

    private record ActiveEntry(int id, String name, double elapsed, double duration, boolean paused) {
    }

    private final List<EventEntry> events = new ArrayList<>();
    private final List<ActiveEntry> active = new ArrayList<>();
    private final boolean canPanic, canStop, canControl;
    private int profile;
    private int delay = 1;
    private int page;

    public AdminScreen(String json) {
        super(Component.translatable("liveevents.gui.title"));
        boolean panic = false, stop = false, control = false;
        try {
            JsonObject root = JsonParser.parseString(json).getAsJsonObject();
            for (JsonElement e : root.getAsJsonArray("events")) {
                JsonObject o = e.getAsJsonObject();
                events.add(new EventEntry(o.get("id").getAsString(), o.get("name").getAsString(), o.get("description").getAsString(),
                        o.get("scope").getAsString(), o.get("duration").getAsDouble()));
                if (events.size() >= 200) {
                    break;
                }
            }
            JsonArray act = root.getAsJsonArray("active");
            for (JsonElement e : act) {
                JsonObject o = e.getAsJsonObject();
                active.add(new ActiveEntry(o.get("id").getAsInt(), o.get("name").getAsString(), o.get("elapsed").getAsDouble(),
                        o.get("duration").getAsDouble(), o.get("paused").getAsBoolean()));
            }
            panic = root.get("can_panic").getAsBoolean();
            stop = root.get("can_stop").getAsBoolean();
            control = root.get("can_control").getAsBoolean();
        } catch (RuntimeException ignored) {
            // Datos inválidos del servidor: el panel se abre vacío
        }
        canPanic = panic;
        canStop = stop;
        canControl = control;
    }

    @Override
    protected void init() {
        int left = 20, y = 58;
        if (canPanic) {
            addRenderableWidget(Button.builder(Component.translatable("liveevents.gui.panic"), b -> command("le panic"))
                    .bounds(width - 130, 8, 120, 20).build());
        }
        addRenderableWidget(Button.builder(profileText(), b -> {
            profile = (profile + 1) % PROFILES.length;
            b.setMessage(profileText());
        }).bounds(left, 20, 140, 20).build());
        addRenderableWidget(Button.builder(delayText(), b -> {
            delay = (delay + 1) % DELAYS.length;
            b.setMessage(delayText());
        }).bounds(left + 144, 20, 120, 20).build());

        int listWidth = Math.min(260, width / 2 - 30);
        for (int i = page * PER_PAGE; i < Math.min(events.size(), (page + 1) * PER_PAGE); i++) {
            EventEntry e = events.get(i);
            addRenderableWidget(Button.builder(Component.literal("▶ ").append(Texts.of(e.name())), b -> {
                command(String.format(Locale.ROOT, "le start %s profile=%s%s", e.id(), PROFILES[profile],
                        DELAYS[delay] > 0 ? " delay " + DELAYS[delay] : ""));
                onClose();
            }).bounds(left, y, listWidth, 20).build());
            y += 24;
        }
        if (events.size() > PER_PAGE) {
            addRenderableWidget(Button.builder(Component.literal("<"), b -> {
                page = Math.max(0, page - 1);
                rebuildWidgets();
            }).bounds(left, y, 20, 20).build());
            addRenderableWidget(Button.builder(Component.literal(">"), b -> {
                page = Math.min((events.size() - 1) / PER_PAGE, page + 1);
                rebuildWidgets();
            }).bounds(left + 24, y, 20, 20).build());
        }

        int rx = width / 2 + 10, ry = 58;
        for (ActiveEntry a : active) {
            if (canControl) {
                addRenderableWidget(Button.builder(Component.translatable(a.paused() ? "liveevents.gui.resume" : "liveevents.gui.pause"),
                        b -> command("le " + (a.paused() ? "resume " : "pause ") + a.id())).bounds(rx, ry + 12, 60, 20).build());
                addRenderableWidget(Button.builder(Component.translatable("liveevents.gui.skip"), b -> command("le skip " + a.id()))
                        .bounds(rx + 64, ry + 12, 60, 20).build());
            }
            if (canStop) {
                addRenderableWidget(Button.builder(Component.translatable("liveevents.gui.stop"), b -> command("le stop " + a.id()))
                        .bounds(rx + 128, ry + 12, 60, 20).build());
            }
            ry += 40;
            if (ry > height - 60) {
                break;
            }
        }
        addRenderableWidget(Button.builder(CommonComponents.GUI_DONE, b -> onClose()).bounds(width / 2 - 100, height - 28, 200, 20).build());
    }

    private Component profileText() {
        return Component.translatable("liveevents.gui.profile", Component.translatable("liveevents.profile." + PROFILES[profile]));
    }

    private Component delayText() {
        return Component.translatable("liveevents.gui.delay", DELAYS[delay]);
    }

    private void command(String command) {
        if (minecraft.getConnection() != null) {
            minecraft.getConnection().sendCommand(command);
        }
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float partial) {
        super.extractRenderState(g, mouseX, mouseY, partial);
        g.text(font, title, 20, 8, 0xFFFFD700, true);
        g.text(font, Component.translatable("liveevents.gui.events", events.size()), 20, 46, 0xFFFFFFFF, true);
        g.text(font, Component.translatable("liveevents.gui.active", active.size()), width / 2 + 10, 46, 0xFFFFFFFF, true);
        int ry = 58;
        for (ActiveEntry a : active) {
            g.text(font, Component.literal("#" + a.id() + " ").append(Texts.of(a.name())).append(" " + Durations.formatClock(a.elapsed())
                    + "/" + Durations.formatClock(a.duration()) + (a.paused() ? " ⏸" : "")), width / 2 + 10, ry, 0xFFDDDDDD, true);
            ry += 40;
            if (ry > height - 60) {
                break;
            }
        }
        // Descripción del evento bajo el ratón
        int y = 58;
        int listWidth = Math.min(260, width / 2 - 30);
        for (int i = page * PER_PAGE; i < Math.min(events.size(), (page + 1) * PER_PAGE); i++) {
            if (mouseX >= 20 && mouseX <= 20 + listWidth && mouseY >= y && mouseY < y + 20) {
                EventEntry e = events.get(i);
                g.text(font, Component.literal(e.id() + " · " + e.scope() + " · " + Durations.formatClock(e.duration())), 20, height - 58, 0xFFAAAAAA, true);
                if (!e.description().isEmpty()) {
                    g.text(font, Texts.of(e.description()), 20, height - 46, 0xFFAAAAAA, true);
                }
            }
            y += 24;
        }
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
