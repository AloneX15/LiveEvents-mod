package com.takumistudios.liveevents.network;

import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/**
 * Paquetes de LiveEvents (§9.1). <b>Todos van del servidor al cliente</b>: no existe ningún paquete cliente → servidor
 * con poder de acción. Cada lectura limita el tamaño de los textos para que un servidor malicioso no pueda colgar el
 * cliente; el cliente además valida y recorta los valores al aplicarlos.
 */
public final class Payloads {
    /** Versión del protocolo de red. Cambiarla cuando cambie el formato de cualquier paquete. */
    public static final int PROTOCOL = 1;
    public static final int MAX_ID = 128;
    public static final int MAX_JSON = 32 * 1024;
    public static final int MAX_REASON = 256;
    /** Trozos máximos de DefinitionSync (64 × 32 KB = 2 MB). */
    public static final int MAX_CHUNKS = 64;

    private Payloads() {
    }

    private static <T extends CustomPacketPayload> CustomPacketPayload.Type<T> typeOf(String path) {
        return new CustomPacketPayload.Type<>(Identifier.fromNamespaceAndPath("liveevents", path));
    }

    private static double finite(double v) {
        return Double.isFinite(v) ? v : 0;
    }

    /** Versión del protocolo y del mod (al conectarse). */
    public record Handshake(int protocol, String modVersion, String maxProfile, boolean photosensitivityNotice)
            implements CustomPacketPayload {
        public static final Type<Handshake> TYPE = typeOf("handshake");
        public static final StreamCodec<RegistryFriendlyByteBuf, Handshake> CODEC = CustomPacketPayload.codec(
                (p, b) -> {
                    b.writeVarInt(p.protocol);
                    b.writeUtf(p.modVersion, MAX_ID);
                    b.writeUtf(p.maxProfile, MAX_ID);
                    b.writeBoolean(p.photosensitivityNotice);
                },
                b -> new Handshake(b.readVarInt(), b.readUtf(MAX_ID), b.readUtf(MAX_ID), b.readBoolean()));

        @Override
        public Type<Handshake> type() {
            return TYPE;
        }
    }

    /** Definiciones (JSON resuelto) en trozos, para que el cliente pueda dibujar eventos personalizados. */
    public record DefinitionSync(int index, int count, String chunk) implements CustomPacketPayload {
        public static final Type<DefinitionSync> TYPE = typeOf("definition_sync");
        public static final StreamCodec<RegistryFriendlyByteBuf, DefinitionSync> CODEC = CustomPacketPayload.codec(
                (p, b) -> {
                    b.writeVarInt(p.index);
                    b.writeVarInt(p.count);
                    b.writeUtf(p.chunk, MAX_JSON);
                },
                b -> new DefinitionSync(b.readVarInt(), b.readVarInt(), b.readUtf(MAX_JSON)));

        @Override
        public Type<DefinitionSync> type() {
            return TYPE;
        }
    }

    /**
     * Inicio de una instancia (o estado completo al entrar). El cliente avanza la simulación hasta {@code eventTicks}.
     *
     * @param params   parámetros que difieren de los valores por defecto (JSON)
     * @param preview  solo visual, para el admin que hizo /le preview
     */
    public record EventStart(int instanceId, String definitionId, String dimension, double x, double y, double z,
                             long seed, double eventTicks, String profile, double timescale, String params,
                             boolean preview, boolean paused) implements CustomPacketPayload {
        public static final Type<EventStart> TYPE = typeOf("event_start");
        public static final StreamCodec<RegistryFriendlyByteBuf, EventStart> CODEC = CustomPacketPayload.codec(
                (p, b) -> {
                    b.writeVarInt(p.instanceId);
                    b.writeUtf(p.definitionId, MAX_ID);
                    b.writeUtf(p.dimension, MAX_ID);
                    b.writeDouble(p.x);
                    b.writeDouble(p.y);
                    b.writeDouble(p.z);
                    b.writeLong(p.seed);
                    b.writeDouble(p.eventTicks);
                    b.writeUtf(p.profile, MAX_ID);
                    b.writeDouble(p.timescale);
                    b.writeUtf(p.params, MAX_JSON);
                    b.writeBoolean(p.preview);
                    b.writeBoolean(p.paused);
                },
                b -> new EventStart(b.readVarInt(), b.readUtf(MAX_ID), b.readUtf(MAX_ID), finite(b.readDouble()),
                        finite(b.readDouble()), finite(b.readDouble()), b.readLong(), finite(b.readDouble()),
                        b.readUtf(MAX_ID), finite(b.readDouble()), b.readUtf(MAX_JSON), b.readBoolean(), b.readBoolean()));

        @Override
        public Type<EventStart> type() {
            return TYPE;
        }
    }

    /** Cambio de fase con el tiempo exacto del evento (compensa la latencia). */
    public record EventPhase(int instanceId, int phaseIndex, double eventTicks) implements CustomPacketPayload {
        public static final Type<EventPhase> TYPE = typeOf("event_phase");
        public static final StreamCodec<RegistryFriendlyByteBuf, EventPhase> CODEC = CustomPacketPayload.codec(
                (p, b) -> {
                    b.writeVarInt(p.instanceId);
                    b.writeVarInt(p.phaseIndex);
                    b.writeDouble(p.eventTicks);
                },
                b -> new EventPhase(b.readVarInt(), b.readVarInt(), finite(b.readDouble())));

        @Override
        public Type<EventPhase> type() {
            return TYPE;
        }
    }

    /** Parámetros cambiados en vivo con /le set (JSON con todos los parámetros vigentes). */
    public record EventKeyframe(int instanceId, String params) implements CustomPacketPayload {
        public static final Type<EventKeyframe> TYPE = typeOf("event_keyframe");
        public static final StreamCodec<RegistryFriendlyByteBuf, EventKeyframe> CODEC = CustomPacketPayload.codec(
                (p, b) -> {
                    b.writeVarInt(p.instanceId);
                    b.writeUtf(p.params, MAX_JSON);
                },
                b -> new EventKeyframe(b.readVarInt(), b.readUtf(MAX_JSON)));

        @Override
        public Type<EventKeyframe> type() {
            return TYPE;
        }
    }

    /** Borra todo el estado de eventos del cliente (antes de mandar el estado completo de la dimensión). */
    public record EventReset() implements CustomPacketPayload {
        public static final Type<EventReset> TYPE = typeOf("event_reset");
        public static final StreamCodec<RegistryFriendlyByteBuf, EventReset> CODEC = CustomPacketPayload.codec((p, b) -> {
        }, b -> new EventReset());

        @Override
        public Type<EventReset> type() {
            return TYPE;
        }
    }

    public record EventEnd(int instanceId, String reason) implements CustomPacketPayload {
        public static final Type<EventEnd> TYPE = typeOf("event_end");
        public static final StreamCodec<RegistryFriendlyByteBuf, EventEnd> CODEC = CustomPacketPayload.codec(
                (p, b) -> {
                    b.writeVarInt(p.instanceId);
                    b.writeUtf(p.reason, MAX_REASON);
                },
                b -> new EventEnd(b.readVarInt(), b.readUtf(MAX_REASON)));

        @Override
        public Type<EventEnd> type() {
            return TYPE;
        }
    }

    /** Latido del reloj (cada segundo mientras hay eventos activos). */
    public record EventClock(int instanceId, double eventTicks, double timescale) implements CustomPacketPayload {
        public static final Type<EventClock> TYPE = typeOf("event_clock");
        public static final StreamCodec<RegistryFriendlyByteBuf, EventClock> CODEC = CustomPacketPayload.codec(
                (p, b) -> {
                    b.writeVarInt(p.instanceId);
                    b.writeDouble(p.eventTicks);
                    b.writeDouble(p.timescale);
                },
                b -> new EventClock(b.readVarInt(), finite(b.readDouble()), finite(b.readDouble())));

        @Override
        public Type<EventClock> type() {
            return TYPE;
        }
    }

    public record EventPause(int instanceId, boolean paused, double eventTicks) implements CustomPacketPayload {
        public static final Type<EventPause> TYPE = typeOf("event_pause");
        public static final StreamCodec<RegistryFriendlyByteBuf, EventPause> CODEC = CustomPacketPayload.codec(
                (p, b) -> {
                    b.writeVarInt(p.instanceId);
                    b.writeBoolean(p.paused);
                    b.writeDouble(p.eventTicks);
                },
                b -> new EventPause(b.readVarInt(), b.readBoolean(), finite(b.readDouble())));

        @Override
        public Type<EventPause> type() {
            return TYPE;
        }
    }

    /** /le panic: el cliente corta al instante todos los efectos, sin fundidos. */
    public record PanicStop() implements CustomPacketPayload {
        public static final Type<PanicStop> TYPE = typeOf("panic_stop");
        public static final StreamCodec<RegistryFriendlyByteBuf, PanicStop> CODEC = CustomPacketPayload.codec((p, b) -> {
        }, b -> new PanicStop());

        @Override
        public Type<PanicStop> type() {
            return TYPE;
        }
    }

    /** Secuelas persistentes del cielo (JSON con la lista completa de la dimensión). */
    public record SkyScarState(String json) implements CustomPacketPayload {
        public static final Type<SkyScarState> TYPE = typeOf("sky_scar_state");
        public static final StreamCodec<RegistryFriendlyByteBuf, SkyScarState> CODEC = CustomPacketPayload.codec(
                (p, b) -> b.writeUtf(p.json, MAX_JSON),
                b -> new SkyScarState(b.readUtf(MAX_JSON)));

        @Override
        public Type<SkyScarState> type() {
            return TYPE;
        }
    }

    /** Activa o desactiva el overlay de /le debug timeline para ese jugador. */
    public record DebugOverlay(boolean timeline, boolean perf, String perfText) implements CustomPacketPayload {
        public static final Type<DebugOverlay> TYPE = typeOf("debug_overlay");
        public static final StreamCodec<RegistryFriendlyByteBuf, DebugOverlay> CODEC = CustomPacketPayload.codec(
                (p, b) -> {
                    b.writeBoolean(p.timeline);
                    b.writeBoolean(p.perf);
                    b.writeUtf(p.perfText, MAX_REASON * 4);
                },
                b -> new DebugOverlay(b.readBoolean(), b.readBoolean(), b.readUtf(MAX_REASON * 4)));

        @Override
        public Type<DebugOverlay> type() {
            return TYPE;
        }
    }

    /** Abre el panel de administración en el cliente (solo lo manda /le gui tras comprobar el permiso). */
    public record OpenGui(String eventsJson) implements CustomPacketPayload {
        public static final Type<OpenGui> TYPE = typeOf("open_gui");
        public static final StreamCodec<RegistryFriendlyByteBuf, OpenGui> CODEC = CustomPacketPayload.codec(
                (p, b) -> b.writeUtf(p.eventsJson, MAX_JSON),
                b -> new OpenGui(b.readUtf(MAX_JSON)));

        @Override
        public Type<OpenGui> type() {
            return TYPE;
        }
    }

    public static void register() {
        PayloadTypeRegistry<RegistryFriendlyByteBuf> s2c = PayloadTypeRegistry.clientboundPlay();
        s2c.register(Handshake.TYPE, Handshake.CODEC);
        s2c.register(DefinitionSync.TYPE, DefinitionSync.CODEC);
        s2c.register(EventStart.TYPE, EventStart.CODEC);
        s2c.register(EventPhase.TYPE, EventPhase.CODEC);
        s2c.register(EventKeyframe.TYPE, EventKeyframe.CODEC);
        s2c.register(EventReset.TYPE, EventReset.CODEC);
        s2c.register(EventEnd.TYPE, EventEnd.CODEC);
        s2c.register(EventClock.TYPE, EventClock.CODEC);
        s2c.register(EventPause.TYPE, EventPause.CODEC);
        s2c.register(PanicStop.TYPE, PanicStop.CODEC);
        s2c.register(SkyScarState.TYPE, SkyScarState.CODEC);
        s2c.register(DebugOverlay.TYPE, DebugOverlay.CODEC);
        s2c.register(OpenGui.TYPE, OpenGui.CODEC);
    }
}
