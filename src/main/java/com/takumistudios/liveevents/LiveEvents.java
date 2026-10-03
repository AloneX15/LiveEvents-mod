package com.takumistudios.liveevents;

import com.takumistudios.liveevents.api.LiveEventsAddon;
import com.takumistudios.liveevents.content.LEContent;
import com.takumistudios.liveevents.core.module.ModuleTypeRegistry;
import com.takumistudios.liveevents.network.Payloads;
import com.takumistudios.liveevents.server.LiveEventsServer;
import com.takumistudios.liveevents.server.command.LiveEventsCommand;
import com.takumistudios.liveevents.server.integration.PlaceholderCompat;
import com.takumistudios.liveevents.server.modules.ServerModules;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.entity.event.v1.ServerEntityLevelChangeEvents;
import net.fabricmc.fabric.api.entity.event.v1.ServerPlayerEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.loader.api.FabricLoader;
import net.fabricmc.loader.api.entrypoint.EntrypointContainer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Punto de entrada común (servidor y cliente). Cada evento registrado aquí va protegido con try/catch para que un
 * error de LiveEvents nunca tumbe el juego: se registra y se desactiva solo esa función.
 */
public final class LiveEvents implements ModInitializer {
    public static final String MOD_ID = "liveevents";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);
    private static boolean tickEnabled = true;

    @Override
    public void onInitialize() {
        LEContent.register();
        Payloads.register();
        loadAddons();
        ModuleTypeRegistry.freeze();

        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> {
            try {
                LiveEventsCommand.register(dispatcher);
            } catch (RuntimeException e) {
                LOGGER.error("Could not register /liveevents", e);
            }
        });

        ServerLifecycleEvents.SERVER_STARTING.register(server -> {
            try {
                LiveEventsServer.start(server);
            } catch (RuntimeException e) {
                LOGGER.error("LiveEvents could not start; events are disabled on this server", e);
            }
        });
        ServerLifecycleEvents.SERVER_STARTED.register(server -> guard("server started", () -> {
            LiveEventsServer s = LiveEventsServer.get();
            s.registry().reload(server);
            s.director().restore();
            s.schedule().checkMissed();
        }));
        ServerLifecycleEvents.END_DATA_PACK_RELOAD.register((server, resources, success) -> guard("data pack reload", () -> {
            if (success && LiveEventsServer.active()) {
                LiveEventsServer.get().registry().reload(server);
                LiveEventsServer.get().director().broadcastDefinitions();
            }
        }));
        ServerLifecycleEvents.SERVER_STOPPING.register(server -> guard("server stopping", () -> {
            if (LiveEventsServer.active()) {
                LiveEventsServer.get().stop();
            }
        }));

        ServerTickEvents.END_SERVER_TICK.register(server -> {
            if (!tickEnabled || !LiveEventsServer.active()) {
                return;
            }
            try {
                LiveEventsServer s = LiveEventsServer.get();
                s.director().tick();
                s.schedule().tick();
            } catch (RuntimeException e) {
                LOGGER.error("Error in the LiveEvents tick; the event system is disabled until restart", e);
                tickEnabled = false;
            }
        });

        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> guard("player join", () -> {
            if (LiveEventsServer.active()) {
                LiveEventsServer.get().director().onJoin(handler.getPlayer());
            }
        }));
        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> guard("player leave", () -> {
            if (LiveEventsServer.active()) {
                LiveEventsServer.get().director().onLeave(handler.getPlayer());
            }
        }));
        ServerEntityLevelChangeEvents.AFTER_PLAYER_CHANGE_LEVEL.register((player, origin, destination) -> guard("dimension change", () -> {
            if (LiveEventsServer.active()) {
                LiveEventsServer.get().director().syncPlayer(player);
            }
        }));
        ServerPlayerEvents.AFTER_RESPAWN.register((oldPlayer, newPlayer, alive) -> guard("respawn", () -> {
            if (LiveEventsServer.active()) {
                LiveEventsServer.get().director().onRespawn(newPlayer);
            }
        }));

        if (FabricLoader.getInstance().isModLoaded("placeholder-api")) {
            try {
                PlaceholderCompat.init();
            } catch (Throwable t) {
                LOGGER.warn("Text Placeholder API integration disabled", t);
            }
        }

        LOGGER.info("LiveEvents {} cargado. Creado por TakumiStudios.", version());
    }

    /** Addons: entrypoint "liveevents" (§20.6). Un addon roto se desactiva sin afectar a los demás. */
    private static void loadAddons() {
        for (EntrypointContainer<LiveEventsAddon> container : FabricLoader.getInstance().getEntrypointContainers(MOD_ID, LiveEventsAddon.class)) {
            String modId = container.getProvider().getMetadata().getId();
            try {
                container.getEntrypoint().registerModules((type, serverFactory) -> {
                    ModuleTypeRegistry.register(type);
                    if (serverFactory != null) {
                        ServerModules.registerAddon(type.id(), serverFactory);
                    }
                });
                LOGGER.info("Loaded LiveEvents addon {}", modId);
            } catch (Throwable t) {
                LOGGER.error("LiveEvents addon {} failed to register and was skipped", modId, t);
            }
        }
    }

    private static void guard(String what, Runnable action) {
        try {
            action.run();
        } catch (RuntimeException e) {
            LOGGER.error("Error in LiveEvents ({})", what, e);
        }
    }

    public static String version() {
        return FabricLoader.getInstance().getModContainer(MOD_ID)
                .map(container -> container.getMetadata().getVersion().getFriendlyString())
                .orElse("desconocida");
    }
}
