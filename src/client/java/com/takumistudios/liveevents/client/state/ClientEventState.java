package com.takumistudios.liveevents.client.state;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.takumistudios.liveevents.LiveEvents;
import com.takumistudios.liveevents.client.config.ClientConfig;
import com.takumistudios.liveevents.core.definition.DefinitionLoader;
import com.takumistudios.liveevents.core.definition.EventDefinition;
import com.takumistudios.liveevents.core.definition.Palette;
import com.takumistudios.liveevents.core.definition.Profile;
import com.takumistudios.liveevents.network.Payloads;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Estado de LiveEvents en el cliente: definiciones sincronizadas, instancias activas, secuelas del cielo y opciones del
 * servidor. Todos los valores recibidos se validan y recortan: un servidor malicioso no puede colgar el cliente.
 */
public final class ClientEventState {
    /** Instancias simultáneas como máximo en el cliente. */
    public static final int MAX_INSTANCES = 16;
    public static final int MAX_SCARS = 32;

    /** Secuela del cielo recibida del servidor. */
    public record Scar(int id, String kind, double azimuth, double altitude, long seed, int[] colors, double spanDeg) {
    }

    private static final ClientEventState INSTANCE = new ClientEventState();

    private final Map<String, EventDefinition> definitions = new HashMap<>();
    private final Map<Integer, ClientInstance> instances = new LinkedHashMap<>();
    private final List<ClientInstance> ending = new ArrayList<>();
    private final List<ClientInstance> residues = new ArrayList<>();
    private final List<Scar> scars = new ArrayList<>();
    private String[] chunks;
    private int serverProtocol = -1;
    private String serverVersion = "";
    private Profile serverMaxProfile = Profile.CINEMATIC;
    private boolean photosensitivityNotice;
    private boolean debugTimeline;
    private boolean debugPerf;
    private String perfText = "";
    /** Tecla de pánico del cliente: corta todos los efectos de pantalla hasta volver a pulsarla. */
    private boolean panicked;

    private ClientEventState() {
    }

    public static ClientEventState get() {
        return INSTANCE;
    }

    // ------------------------------------------------------------------------------------------------ paquetes

    public void onHandshake(Payloads.Handshake p) {
        serverProtocol = p.protocol();
        serverVersion = p.modVersion();
        serverMaxProfile = Profile.parse(p.maxProfile()).orElse(Profile.CINEMATIC);
        photosensitivityNotice = p.photosensitivityNotice();
    }

    public boolean protocolMismatch() {
        return serverProtocol >= 0 && serverProtocol != Payloads.PROTOCOL;
    }

    public String serverVersion() {
        return serverVersion;
    }

    public void onDefinitionChunk(Payloads.DefinitionSync p) {
        if (p.count() <= 0 || p.count() > Payloads.MAX_CHUNKS || p.index() < 0 || p.index() >= p.count()) {
            LiveEvents.LOGGER.debug("Ignoring invalid DefinitionSync chunk {}/{}", p.index(), p.count());
            return;
        }
        if (chunks == null || chunks.length != p.count() || p.index() == 0) {
            chunks = new String[p.count()];
        }
        chunks[p.index()] = p.chunk();
        for (String c : chunks) {
            if (c == null) {
                return;
            }
        }
        String json = String.join("", chunks);
        chunks = null;
        Map<String, EventDefinition> parsed = new HashMap<>();
        try {
            JsonArray array = JsonParser.parseString(json).getAsJsonArray();
            for (JsonElement e : array) {
                if (e.isJsonObject()) {
                    DefinitionLoader.parseResolved(e.getAsJsonObject(), "server").ifPresent(d -> parsed.put(d.id(), d));
                }
            }
        } catch (RuntimeException e) {
            LiveEvents.LOGGER.warn("Received invalid event definitions from the server; custom events will not render");
            return;
        }
        definitions.clear();
        definitions.putAll(parsed);
    }

    public void onStart(Payloads.EventStart p, long nowNanos) {
        EventDefinition def = definitions.get(p.definitionId());
        if (def == null) {
            LiveEvents.LOGGER.debug("Unknown event definition {} (not synced)", p.definitionId());
            return;
        }
        if (instances.size() >= MAX_INSTANCES && !instances.containsKey(p.instanceId())) {
            return;
        }
        Map<String, Double> overrides = parseParams(p.params());
        Profile requested = Profile.parse(p.profile()).orElse(Profile.GAMEPLAY);
        Profile effective = Profile.resolve(requested, serverMaxProfile, ClientConfig.get().maxProfile());
        double start = Math.max(0, Math.min(1728000+12000, p.eventTicks()));
        double timescale = Math.max(0.05, Math.min(4, p.timescale()));
        Vec3 center = new Vec3(clampCoord(p.x()), Math.max(-4096, Math.min(4096, p.y())), clampCoord(p.z()));
        instances.put(p.instanceId(), new ClientInstance(p.instanceId(), def, p.dimension(), center, p.seed(), effective,
                p.preview(), start, timescale, overrides, p.paused(), nowNanos));
    }

    public void onMeteor(Payloads.Meteor packet) {
        ClientInstance instance = instances.get(packet.instanceId());
        if (instance == null || protocolMismatch()) return;
        var meteor = new com.google.gson.Gson().fromJson(packet.json(), com.takumistudios.liveevents.core.plan.AnnouncedMeteor.class);
        if (meteor == null || !meteor.valid()) return;
        instance.meteors.values().removeIf(m -> m.tick()+m.lifetimeTicks()<instance.t);
        if(instance.meteors.size()>=512 && !instance.meteors.containsKey(meteor.key())) {
            var oldest=instance.meteors.entrySet().stream().filter(e->e.getValue().tick()<instance.t-20).findFirst();
            oldest.ifPresent(e->instance.meteors.remove(e.getKey()));
        }
        if (instance.meteors.size() < 512 || instance.meteors.containsKey(meteor.key())) instance.meteors.put(meteor.key(), meteor);
    }

    public void onPhase(Payloads.EventPhase p, long nowNanos) {
        ClientInstance i = instances.get(p.instanceId());
        if (i != null) {
            i.clock.sync(p.eventTicks(), nowNanos);
        }
    }

    public void onClock(Payloads.EventClock p, long nowNanos) {
        ClientInstance i = instances.get(p.instanceId());
        if (i != null) {
            i.timescale = Math.max(0.05, Math.min(4, p.timescale()));
            i.clock.setTimescale(i.timescale);
            i.clock.sync(p.eventTicks(), nowNanos);
        }
    }

    public void onPause(Payloads.EventPause p, long nowNanos) {
        ClientInstance i = instances.get(p.instanceId());
        if (i != null) {
            i.clock.setPaused(p.paused(), p.eventTicks(), nowNanos);
        }
    }

    public void onKeyframe(Payloads.EventKeyframe p) {
        ClientInstance i = instances.get(p.instanceId());
        if (i != null) {
            i.setOverrides(parseParams(p.params()));
        }
    }

    public void onEnd(Payloads.EventEnd p, long nowNanos) {
        residues.removeIf(i->i.id==p.instanceId());
        ending.removeIf(i->i.id==p.instanceId());
        ClientInstance i = instances.remove(p.instanceId());
        if(i!=null && p.reason().equals("completed") && !i.meteors.isEmpty()) {
            if(residues.size()>=MAX_INSTANCES) residues.removeFirst();
            residues.add(i);
        }
        if (i != null) {
            i.endedAt = nowNanos;
            ending.add(i); // fundido corto de salida
        }
    }

    public void onReset() {
        instances.clear();
        ending.clear();
        residues.clear();
    }

    /** /le panic del servidor: corte instantáneo, sin fundidos. */
    public void onPanic() {
        instances.clear();
        ending.clear();
        residues.clear();
    }

    public void onScars(Payloads.SkyScarState p) {
        scars.clear();
        try {
            JsonArray array = JsonParser.parseString(p.json()).getAsJsonArray();
            for (JsonElement e : array) {
                if (scars.size() >= MAX_SCARS || !e.isJsonObject()) {
                    break;
                }
                JsonObject o = e.getAsJsonObject();
                String kind = o.get("kind").getAsString();
                if (!kind.equals("nebula") && !kind.equals("pulsar")) {
                    continue;
                }
                JsonArray cols = o.has("colors") ? o.getAsJsonArray("colors") : new JsonArray();
                int[] colors = new int[Math.min(cols.size(), 8)];
                for (int k = 0; k < colors.length; k++) {
                    colors[k] = Palette.parseHex(cols.get(k).getAsString()).orElse(0xFFFFFF);
                }
                scars.add(new Scar(o.get("id").getAsInt(), kind, o.get("azimuth").getAsDouble(),
                        Math.max(0, Math.min(90, o.get("altitude").getAsDouble())), o.get("seed").getAsLong(), colors,
                        o.has("span_deg") ? Math.max(10, Math.min(360, o.get("span_deg").getAsDouble())) : 180));
            }
        } catch (RuntimeException e) {
            LiveEvents.LOGGER.debug("Ignoring invalid sky scar state");
        }
    }

    public void onDebug(Payloads.DebugOverlay p) {
        debugTimeline = p.timeline();
        debugPerf = p.perf();
        perfText = p.perfText();
    }

    public void onDisconnect() {
        instances.clear();
        ending.clear();
        residues.clear();
        scars.clear();
        definitions.clear();
        chunks = null;
        serverProtocol = -1;
        debugTimeline = false;
        debugPerf = false;
    }

    // ------------------------------------------------------------------------------------------------ consultas

    public Collection<ClientInstance> instances() {
        return instances.values();
    }

    public List<ClientInstance> meteorInstances(long now) {
        residues.removeIf(i->{
            double tick=i.clock.advance(now);
            return i.meteors.values().stream().noneMatch(m->m.tick()+m.lifetimeTicks()>tick);
        });
        List<ClientInstance> visible=new ArrayList<>(instances.values());visible.addAll(residues);return visible;
    }

    public List<ClientInstance> ending() {
        return ending;
    }

    public List<Scar> scars() {
        return scars;
    }

    public Map<String, EventDefinition> definitions() {
        return definitions;
    }

    public boolean photosensitivityNotice() {
        return photosensitivityNotice;
    }

    public boolean debugTimeline() {
        return debugTimeline;
    }

    public boolean debugPerf() {
        return debugPerf;
    }

    public String perfText() {
        return perfText;
    }

    public boolean panicked() {
        return panicked;
    }

    public void togglePanic() {
        panicked = !panicked;
    }

    public boolean active() {
        return !instances.isEmpty() || !ending.isEmpty() || !scars.isEmpty();
    }

    private static double clampCoord(double v) {
        return Math.max(-30_000_000, Math.min(30_000_000, v));
    }

    private static Map<String, Double> parseParams(String json) {
        Map<String, Double> out = new HashMap<>();
        try {
            JsonObject o = JsonParser.parseString(json).getAsJsonObject();
            for (Map.Entry<String, JsonElement> e : o.entrySet()) {
                if (out.size() >= 64) {
                    break;
                }
                if (e.getKey().matches("[a-z0-9_]{1,32}") && e.getValue().isJsonPrimitive() && e.getValue().getAsJsonPrimitive().isNumber()) {
                    out.put(e.getKey(), e.getValue().getAsDouble());
                }
            }
        } catch (RuntimeException ignored) {
            LiveEvents.LOGGER.debug("Ignoring invalid event parameters");
        }
        return out;
    }
}
