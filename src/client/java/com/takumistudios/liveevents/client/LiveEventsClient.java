package com.takumistudios.liveevents.client;

import com.takumistudios.liveevents.LiveEvents;
import com.takumistudios.liveevents.client.config.ClientConfig;
import com.takumistudios.liveevents.client.render.DebugOverlay;
import com.takumistudios.liveevents.client.render.HudRenderer;
import com.takumistudios.liveevents.client.screen.AdminScreen;
import com.takumistudios.liveevents.client.state.ClientEventState;
import com.takumistudios.liveevents.client.visual.VisualDirector;
import com.takumistudios.liveevents.network.Payloads;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.minecraft.ChatFormatting;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

import java.util.function.Consumer;

/**
 * Punto de entrada del cliente: recibe los paquetes (todos servidor → cliente), dibuja los efectos en el HUD, ejecuta
 * partículas y sonidos en el tick y registra la tecla de pánico. Cada callback va protegido: un error desactiva solo
 * esa función.
 */
public final class LiveEventsClient implements ClientModInitializer {
    private static KeyMapping panicKey;
    private static boolean hudEnabled = true;
    private static boolean tickEnabled = true;
    private static boolean mismatchWarned;

    @Override
    public void onInitializeClient() {
        ClientConfig.get();
        ClientEventState state = ClientEventState.get();

        receive(Payloads.Handshake.TYPE, p -> {
            state.onHandshake(p);
            Minecraft mc = Minecraft.getInstance();
            if (state.protocolMismatch() && !mismatchWarned && mc.player != null) {
                mismatchWarned = true;
                mc.player.sendSystemMessage(Component.translatable("liveevents.client.protocol_mismatch", p.modVersion(), LiveEvents.version())
                        .withStyle(ChatFormatting.RED));
            }
        });
        receive(Payloads.DefinitionSync.TYPE, state::onDefinitionChunk);
        receive(Payloads.EventStart.TYPE, p -> state.onStart(p, System.nanoTime()));
        receive(Payloads.EventPhase.TYPE, p -> state.onPhase(p, System.nanoTime()));
        receive(Payloads.EventKeyframe.TYPE, state::onKeyframe);
        receive(Payloads.EventReset.TYPE, p -> state.onReset());
        receive(Payloads.EventEnd.TYPE, p -> state.onEnd(p, System.nanoTime()));
        receive(Payloads.EventClock.TYPE, p -> state.onClock(p, System.nanoTime()));
        receive(Payloads.EventPause.TYPE, p -> state.onPause(p, System.nanoTime()));
        receive(Payloads.PanicStop.TYPE, p -> {
            state.onPanic();
            VisualDirector.get().panic();
        });
        receive(Payloads.SkyScarState.TYPE, state::onScars);
        receive(Payloads.DebugOverlay.TYPE, state::onDebug);
        receive(Payloads.OpenGui.TYPE, p -> Minecraft.getInstance().setScreenAndShow(new AdminScreen(p.eventsJson())));

        HudRenderer hud = new HudRenderer();
        HudElementRegistry.addFirst(Identifier.fromNamespaceAndPath(LiveEvents.MOD_ID, "effects"), (g, delta) -> {
            if (!hudEnabled) {
                return;
            }
            try {
                hud.render(g, delta);
            } catch (RuntimeException e) {
                LiveEvents.LOGGER.error("LiveEvents HUD effects failed and were disabled", e);
                hudEnabled = false;
            }
        });
        DebugOverlay debug = new DebugOverlay();
        HudElementRegistry.addLast(Identifier.fromNamespaceAndPath(LiveEvents.MOD_ID, "debug"), (g, delta) -> {
            try {
                debug.render(g, delta);
            } catch (RuntimeException e) {
                LiveEvents.LOGGER.debug("Debug overlay failed", e);
            }
        });

        // Tecla de pánico (§7.7): sin asignar por defecto, desactiva al instante todos los efectos de pantalla
        panicKey = KeyMappingHelper.registerKeyMapping(new KeyMapping("key.liveevents.panic",
                -1, new KeyMapping.Category(Identifier.fromNamespaceAndPath(LiveEvents.MOD_ID, "main"))));

        ClientTickEvents.END_CLIENT_TICK.register(mc -> {
            if (!tickEnabled) {
                return;
            }
            try {
                while (panicKey.consumeClick()) {
                    state.togglePanic();
                    VisualDirector.get().panic();
                    if (mc.player != null) {
                        mc.player.sendOverlayMessage(Component.translatable(state.panicked() ? "liveevents.client.panic_on" : "liveevents.client.panic_off"));
                    }
                }
                VisualDirector.get().tick();
            } catch (RuntimeException e) {
                LiveEvents.LOGGER.error("LiveEvents client tick failed; client effects are disabled until restart", e);
                tickEnabled = false;
            }
        });
        ClientPlayConnectionEvents.DISCONNECT.register((handler, mc) -> {
            state.onDisconnect();
            VisualDirector.get().onDisconnect();
            mismatchWarned = false;
        });
    }

    /** Registra un receptor que se ejecuta en el hilo del cliente y nunca propaga excepciones. */
    private static <T extends CustomPacketPayload> void receive(CustomPacketPayload.Type<T> type, Consumer<T> handler) {
        ClientPlayNetworking.registerGlobalReceiver(type, (payload, context) -> context.client().execute(() -> {
            try {
                handler.accept(payload);
            } catch (RuntimeException e) {
                LiveEvents.LOGGER.debug("Ignoring invalid {} payload", type.id(), e);
            }
        }));
    }
}
