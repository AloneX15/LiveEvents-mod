package com.takumistudios.liveevents.server.schedule;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.reflect.TypeToken;
import com.mojang.brigadier.StringReader;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.takumistudios.liveevents.LiveEvents;
import com.takumistudios.liveevents.core.definition.Profile;
import com.takumistudios.liveevents.core.time.Durations;
import com.takumistudios.liveevents.server.LiveEventsServer;
import com.takumistudios.liveevents.server.instance.EventDirector;
import com.takumistudios.liveevents.util.AtomicFiles;
import com.takumistudios.liveevents.util.Texts;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ExecutorService;

/**
 * Eventos programados con hora real ({@code /le schedule}) y secuencias de eventos y comandos
 * ({@code /le sequence}). Los programados se guardan en {@code <mundo>/liveevents/schedule.json} y sobreviven a los
 * reinicios; si su hora pasó con el servidor apagado, se avisa y no se lanzan solos.
 */
public final class ScheduleService {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    /** Un lanzamiento programado. */
    public static final class Entry {
        public int id;
        public String event_id;
        public String time;
        public String dimension;
        public double x, y, z;
        public String profile;
        public Map<String, Double> params = new LinkedHashMap<>();
        public String actor;
        public boolean warned;
        public boolean missed;
    }

    /** Una secuencia en curso. */
    private static final class Running {
        final String id;
        final List<JsonObject> steps;
        final CommandSourceStack source;
        final long startTick;
        int next;

        Running(String id, List<JsonObject> steps, CommandSourceStack source, long startTick) {
            this.id = id;
            this.steps = steps;
            this.source = source;
            this.startTick = startTick;
        }
    }

    private final MinecraftServer server;
    private final LiveEventsServer services;
    private final Path file;
    private final ExecutorService io;
    private final List<Entry> entries = new ArrayList<>();
    private final List<Running> sequences = new ArrayList<>();
    private int nextId = 1;
    private long ticks;

    public ScheduleService(MinecraftServer server, LiveEventsServer services, Path file, ExecutorService io) {
        this.server = server;
        this.services = services;
        this.file = file;
        this.io = io;
        load();
    }

    // ------------------------------------------------------------------------------------------------ programación

    public Entry add(String eventId, ZonedDateTime when, ServerLevel level, Vec3 pos, Optional<Profile> profile,
                     Map<String, Double> params, String actor) {
        Entry e = new Entry();
        e.id = nextId++;
        e.event_id = eventId;
        e.time = when.toLocalDateTime().toString();
        e.dimension = level.dimension().identifier().toString();
        e.x = pos.x;
        e.y = pos.y;
        e.z = pos.z;
        e.profile = profile.map(Profile::id).orElse(null);
        e.params = new LinkedHashMap<>(params);
        e.actor = actor;
        entries.add(e);
        save();
        return e;
    }

    public boolean cancel(int id) {
        boolean removed = entries.removeIf(e -> e.id == id);
        if (removed) {
            save();
        }
        return removed;
    }

    public List<Entry> list() {
        return List.copyOf(entries);
    }

    public Optional<Entry> next() {
        return entries.stream().filter(e -> !e.missed).min((a, b) -> a.time.compareTo(b.time));
    }

    /** Al arrancar: los programados cuya hora ya pasó se marcan como perdidos y se avisa. */
    public void checkMissed() {
        LocalDateTime now = LocalDateTime.now(services.config().zone());
        boolean changed = false;
        for (Entry e : entries) {
            if (!e.missed && LocalDateTime.parse(e.time).isBefore(now)) {
                e.missed = true;
                changed = true;
                LiveEvents.LOGGER.warn("Scheduled event #{} ({}) at {} was missed while the server was off; it will not start on its own",
                        e.id, e.event_id, e.time);
            }
        }
        if (changed) {
            save();
        }
    }

    public void tick() {
        ticks++;
        if (ticks % 20 == 0 && !entries.isEmpty()) {
            checkEntries();
        }
        if (!sequences.isEmpty()) {
            tickSequences();
        }
    }

    private void checkEntries() {
        LocalDateTime now = LocalDateTime.now(services.config().zone());
        int warnMinutes = services.config().schedule_warning_minutes;
        Iterator<Entry> it = entries.iterator();
        boolean changed = false;
        while (it.hasNext()) {
            Entry e = it.next();
            if (e.missed) {
                continue;
            }
            LocalDateTime when = LocalDateTime.parse(e.time);
            if (!e.warned && warnMinutes > 0 && !now.isBefore(when.minusMinutes(warnMinutes)) && now.isBefore(when)) {
                e.warned = true;
                changed = true;
                Component name = services.registry().get(e.event_id).map(d -> Texts.of(d.displayName())).orElse(Component.literal(e.event_id));
                for (ServerPlayer p : server.getPlayerList().getPlayers()) {
                    p.sendSystemMessage(Component.translatable("liveevents.schedule.warning", name, warnMinutes));
                }
            }
            if (!now.isBefore(when)) {
                it.remove();
                changed = true;
                ServerLevel level = server.getLevel(ResourceKey.create(Registries.DIMENSION, Identifier.parse(e.dimension)));
                if (level == null) {
                    LiveEvents.LOGGER.warn("Scheduled event #{}: dimension {} not found", e.id, e.dimension);
                    continue;
                }
                EventDirector.StartResult r = services.director().start(new EventDirector.StartRequest(e.event_id, level,
                        new Vec3(e.x, e.y, e.z), Profile.parse(e.profile), 1.0, e.params, false, null, true, true,
                        e.actor + " (schedule #" + e.id + ")", 0));
                services.director().notifyAdmins(r.message());
            }
        }
        if (changed) {
            save();
        }
    }

    // ------------------------------------------------------------------------------------------------ secuencias

    public boolean startSequence(String id, CommandSourceStack source) {
        JsonObject seq = services.registry().sequences().get(id);
        if (seq == null) {
            return false;
        }
        List<JsonObject> steps = new ArrayList<>();
        for (JsonElement e : seq.getAsJsonArray("steps")) {
            if (e.isJsonObject()) {
                steps.add(e.getAsJsonObject());
            }
        }
        steps.sort((a, b) -> Double.compare(stepTicks(a), stepTicks(b)));
        sequences.add(new Running(id, steps, source, ticks));
        services.audit().log(source.getTextName(), "sequence_start", id);
        return true;
    }

    public int stopSequences() {
        int n = sequences.size();
        sequences.clear();
        return n;
    }

    public List<String> runningSequences() {
        return sequences.stream().map(r -> r.id).toList();
    }

    private void tickSequences() {
        Iterator<Running> it = sequences.iterator();
        while (it.hasNext()) {
            Running r = it.next();
            double elapsed = ticks - r.startTick;
            while (r.next < r.steps.size() && stepTicks(r.steps.get(r.next)) <= elapsed) {
                runStep(r, r.steps.get(r.next++));
            }
            if (r.next >= r.steps.size()) {
                it.remove();
            }
        }
    }

    private static double stepTicks(JsonObject step) {
        try {
            return step.has("at") ? Durations.parseTicksExact(step.get("at").getAsString()) : 0;
        } catch (IllegalArgumentException e) {
            return 0;
        }
    }

    private void runStep(Running r, JsonObject step) {
        try {
            if (step.has("command")) {
                String command = step.get("command").getAsString();
                if (command.length() > 1024) {
                    LiveEvents.LOGGER.warn("Sequence {}: command too long, skipped", r.id);
                    return;
                }
                // Con los permisos de quien lanzó la secuencia, nunca elevados
                server.getCommands().performPrefixedCommand(r.source, command);
            } else if (step.has("event")) {
                CommandSourceStack src = r.source;
                ServerLevel level = src.getLevel();
                Vec3 pos = src.getPosition();
                if (step.has("on")) {
                    Entity target = EntityArgument.entity().parse(new StringReader(step.get("on").getAsString())).findSingleEntity(src);
                    pos = target.position();
                    if (target.level() instanceof ServerLevel l) {
                        level = l;
                    }
                } else if (step.has("position")) {
                    String[] parts = step.get("position").getAsString().trim().split("\\s+");
                    pos = new Vec3(Double.parseDouble(parts[0]), Double.parseDouble(parts[1]), Double.parseDouble(parts[2]));
                }
                Map<String, Double> params = new LinkedHashMap<>();
                if (step.has("args") && step.get("args").isJsonObject()) {
                    for (Map.Entry<String, JsonElement> a : step.getAsJsonObject("args").entrySet()) {
                        params.put(a.getKey(), a.getValue().getAsDouble());
                    }
                }
                String eventId = step.get("event").getAsString();
                if (!com.takumistudios.liveevents.server.Perms.canStart(src, eventId)) {
                    src.sendFailure(Component.translatable("liveevents.error.no_permission_event", eventId));
                    return;
                }
                EventDirector.StartResult result = services.director().start(new EventDirector.StartRequest(eventId, level, pos,
                        step.has("profile") ? Profile.parse(step.get("profile").getAsString()) : Optional.empty(), 1.0, params,
                        false, null, true, true, src.getTextName() + " (sequence " + r.id + ")", 0));
                src.sendSuccess(result::message, true);
            }
        } catch (CommandSyntaxException | RuntimeException e) {
            LiveEvents.LOGGER.warn("Sequence {}: step failed: {}", r.id, e.getMessage());
            r.source.sendFailure(Component.translatable("liveevents.sequence.step_failed", r.id, String.valueOf(e.getMessage())));
        }
    }

    // ------------------------------------------------------------------------------------------------ disco

    private void load() {
        if (!Files.exists(file)) {
            return;
        }
        try {
            List<Entry> loaded = GSON.fromJson(Files.readString(file, StandardCharsets.UTF_8), new TypeToken<List<Entry>>() {
            }.getType());
            if (loaded != null) {
                for (Entry e : loaded) {
                    if (e != null && e.event_id != null && e.time != null) {
                        entries.add(e);
                        nextId = Math.max(nextId, e.id + 1);
                    }
                }
            }
        } catch (IOException | RuntimeException e) {
            LiveEvents.LOGGER.warn("schedule.json is corrupt; scheduled events were reset", e);
        }
    }

    public void save() {
        String json = GSON.toJson(entries);
        io.execute(() -> {
            try {
                AtomicFiles.writeString(file, json);
            } catch (IOException e) {
                LiveEvents.LOGGER.warn("Could not save scheduled events", e);
            }
        });
    }
}
