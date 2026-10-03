package com.takumistudios.liveevents.network;

import com.takumistudios.liveevents.LiveEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;

import java.util.Collection;

/**
 * Envío servidor → cliente. Antes de enviar se comprueba que el cliente tiene el canal: los jugadores sin el mod no
 * reciben nada y no se les desconecta.
 */
public final class ServerNet {
    private ServerNet() {
    }

    public static void send(ServerPlayer player, CustomPacketPayload payload) {
        try {
            if (ServerPlayNetworking.canSend(player, payload.type())) {
                ServerPlayNetworking.send(player, payload);
            }
        } catch (RuntimeException e) {
            LiveEvents.LOGGER.warn("Could not send {} to {}", payload.type().id(), player.getScoreboardName(), e);
        }
    }

    public static void send(Collection<ServerPlayer> players, CustomPacketPayload payload) {
        for (ServerPlayer p : players) {
            send(p, payload);
        }
    }

    public static boolean hasMod(ServerPlayer player) {
        return ServerPlayNetworking.canSend(player, Payloads.Handshake.TYPE);
    }

    /** Divide el JSON de definiciones en trozos que caben en un paquete. */
    public static void sendDefinitions(ServerPlayer player, String json) {
        int chunk = Payloads.MAX_JSON / 4; // margen para caracteres de varios bytes
        int count = Math.max(1, (json.length() + chunk - 1) / chunk);
        if (count > Payloads.MAX_CHUNKS) {
            // Un JSON cortado no se podría leer: mejor no sincronizar y avisar
            LiveEvents.LOGGER.warn("Too many event definitions to sync ({} chunks, max {}); clients will not see custom events",
                    count, Payloads.MAX_CHUNKS);
            return;
        }
        for (int i = 0; i < count; i++) {
            int from = i * chunk, to = Math.min(json.length(), from + chunk);
            send(player, new Payloads.DefinitionSync(i, count, json.substring(from, to)));
        }
    }
}
