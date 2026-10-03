package com.takumistudios.liveevents.client.screen;

import com.takumistudios.liveevents.client.config.ClientConfig;
import com.takumistudios.liveevents.client.visual.VisualDirector;
import com.takumistudios.liveevents.core.definition.Profile;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractSliderButton;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;

import java.util.function.DoubleConsumer;
import java.util.function.DoubleFunction;

/**
 * Opciones del cliente (§12.2): calidad, partículas, destellos, temblor, reducir movimiento, volumen, perfil máximo,
 * secuelas y calidad automática. Se abre desde Mod Menu; sin Mod Menu, se edita config/liveevents/client.json.
 */
public final class ConfigScreen extends Screen {
    private final Screen parent;
    private final ClientConfig config = ClientConfig.get();

    public ConfigScreen(Screen parent) {
        super(Component.translatable("liveevents.config.title"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        int w = 150, h = 20, gap = 4;
        int left = width / 2 - w - gap / 2, right = width / 2 + gap / 2;
        int y = 40;
        addRenderableWidget(Button.builder(qualityText(), b -> {
            config.quality = config.quality().ordinal() == ClientConfig.Quality.values().length - 1
                    ? ClientConfig.Quality.LOW.name() : config.quality().higher().name();
            b.setMessage(qualityText());
        }).bounds(left, y, w, h).build());
        addRenderableWidget(Button.builder(profileText(), b -> {
            Profile next = Profile.values()[(config.maxProfile().ordinal() + 1) % Profile.values().length];
            config.max_profile = next.id();
            b.setMessage(profileText());
        }).bounds(right, y, w, h).build());
        y += h + gap;
        addRenderableWidget(toggle("liveevents.config.camera_shake", () -> config.camera_shake, v -> config.camera_shake = v, left, y, w, h));
        addRenderableWidget(toggle("liveevents.config.reduce_motion", () -> config.reduce_motion, v -> config.reduce_motion = v, right, y, w, h));
        y += h + gap;
        addRenderableWidget(toggle("liveevents.config.auto_quality", () -> config.auto_quality, v -> config.auto_quality = v, left, y, w, h));
        addRenderableWidget(toggle("liveevents.config.show_scars", () -> config.show_sky_scars, v -> config.show_sky_scars = v, right, y, w, h));
        y += h + gap;
        addRenderableWidget(new Slider(left, y, w, h, config.max_flash_intensity,
                v -> Component.translatable("liveevents.config.max_flash", Math.round(v * 100)), v -> config.max_flash_intensity = v));
        addRenderableWidget(new Slider(right, y, w, h, config.event_volume,
                v -> Component.translatable("liveevents.config.volume", Math.round(v * 100)), v -> config.event_volume = v));
        y += h + gap;
        addRenderableWidget(new Slider(left, y, w * 2 + gap, h, config.particle_limit / 16000.0,
                v -> Component.translatable("liveevents.config.particles", Math.round(v * 16000)), v -> config.particle_limit = (int) Math.round(v * 16000)));
        y += h + gap * 3;
        addRenderableWidget(Button.builder(CommonComponents.GUI_DONE, b -> onClose()).bounds(width / 2 - 100, y, 200, h).build());
    }

    private Component qualityText() {
        return Component.translatable("liveevents.config.quality", Component.translatable("liveevents.config.quality." + config.quality().name().toLowerCase(java.util.Locale.ROOT)));
    }

    private Component profileText() {
        return Component.translatable("liveevents.config.max_profile", Component.translatable("liveevents.profile." + config.maxProfile().id()));
    }

    private static Button toggle(String key, java.util.function.BooleanSupplier get, java.util.function.Consumer<Boolean> set, int x, int y, int w, int h) {
        return Button.builder(toggleText(key, get.getAsBoolean()), b -> {
            set.accept(!get.getAsBoolean());
            b.setMessage(toggleText(key, get.getAsBoolean()));
        }).bounds(x, y, w, h).build();
    }

    private static Component toggleText(String key, boolean on) {
        return Component.translatable(key, on ? CommonComponents.OPTION_ON : CommonComponents.OPTION_OFF);
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float partial) {
        super.extractRenderState(g, mouseX, mouseY, partial);
        g.centeredText(font, title, width / 2, 15, 0xFFFFFFFF);
        g.centeredText(font, Component.translatable("liveevents.config.hint"), width / 2, 27, 0xFFAAAAAA);
    }

    @Override
    public void onClose() {
        config.validate();
        config.save();
        VisualDirector.get().resetQuality();
        minecraft.setScreenAndShow(parent);
    }

    private static final class Slider extends AbstractSliderButton {
        private final DoubleFunction<Component> text;
        private final DoubleConsumer apply;

        Slider(int x, int y, int w, int h, double value, DoubleFunction<Component> text, DoubleConsumer apply) {
            super(x, y, w, h, text.apply(Math.max(0, Math.min(1, value))), Math.max(0, Math.min(1, value)));
            this.text = text;
            this.apply = apply;
        }

        @Override
        protected void updateMessage() {
            setMessage(text.apply(value));
        }

        @Override
        protected void applyValue() {
            apply.accept(value);
        }
    }
}
