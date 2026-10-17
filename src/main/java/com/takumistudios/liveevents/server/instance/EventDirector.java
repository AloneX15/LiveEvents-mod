package com.takumistudios.liveevents.server.instance;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.google.gson.reflect.TypeToken;
import com.takumistudios.liveevents.LiveEvents;
import com.takumistudios.liveevents.api.LiveEventCallbacks;
import com.takumistudios.liveevents.content.LEContent;
import com.takumistudios.liveevents.core.definition.EventDefinition;
import com.takumistudios.liveevents.core.definition.ModuleParams;
import com.takumistudios.liveevents.core.definition.ModuleSpec;
import com.takumistudios.liveevents.core.definition.NightOverride;
import com.takumistudios.liveevents.core.definition.Palette;
import com.takumistudios.liveevents.core.definition.PhaseDef;
import com.takumistudios.liveevents.core.definition.Profile;
import com.takumistudios.liveevents.core.definition.QueuePolicy;
import com.takumistudios.liveevents.core.definition.Scope;
import com.takumistudios.liveevents.core.definition.WorldMode;
import com.takumistudios.liveevents.core.random.DeterministicRandom;
import com.takumistudios.liveevents.core.scheduler.WorkScheduler;
import com.takumistudios.liveevents.core.time.Durations;
import com.takumistudios.liveevents.network.Payloads;
import com.takumistudios.liveevents.network.ServerNet;
import com.takumistudios.liveevents.server.LethalGuard;
import com.takumistudios.liveevents.server.LiveEventsServer;
import com.takumistudios.liveevents.server.Perms;
import com.takumistudios.liveevents.server.SkyScarManager;
import com.takumistudios.liveevents.server.journal.WorldJournal;
import com.takumistudios.liveevents.util.AtomicFiles;
import com.takumistudios.liveevents.util.Texts;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.arguments.blocks.BlockStateParser;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundSetActionBarTextPacket;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.permissions.PermissionLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * Crea, pausa, reanuda, cancela y lista instancias (§4.2). Aplica límites de concurrencia, reglas de mezcla (§7.12),
 * enfriamientos y el watchdog. Todo en el hilo del servidor.
 */
public final class EventDirector {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    /** Petición de lanzamiento (comando, API, programación o secuencia). */
    public record StartRequest(String eventId, ServerLevel level, Vec3 center, Optional<Profile> profile, double timescale,
                               Map<String, Double> params, boolean preview, ServerPlayer previewViewer, boolean queue,
                               boolean confirm, String actor, int delaySeconds) {
        public StartRequest withDelay(int seconds) {
            return new StartRequest(eventId, level, center, profile, timescale, params, preview, previewViewer, queue, confirm, actor, seconds);
        }
    }

    /** Resultado: la instancia creada, o un mensaje traducible con el motivo. */
    public record StartResult(EventInstance instance, Component message, boolean queued, boolean delayed) {
        public boolean ok() {
            return instance != null || queued || delayed;
        }

        static StartResult fail(String key, Object... args) {
            return new StartResult(null, Component.translatable(key, args), false, false);
        }
    }

    private record Delayed(StartRequest request, long fireTick) {
    }

    private final MinecraftServer server;
    private final LiveEventsServer services;
    private final Map<Integer, EventInstance> instances = new LinkedHashMap<>();
    private final List<EventInstance> draining = new ArrayList<>();
    private final ArrayDeque<StartRequest> queue = new ArrayDeque<>();
    private final List<Delayed> delayed = new ArrayList<>();
    private final Map<String, Long> cooldownUntil = new HashMap<>();
    private final Map<UUID, Long> graceUntil = new HashMap<>();
    private final Set<UUID> debugTimeline = new HashSet<>();
    private final Set<UUID> debugPerf = new HashSet<>();
    private int nextId = 1;
    private long ticks;
    private boolean autoPaused;
    private int lastPlayerCount;
    private int watchdogHighSeconds;
    private int watchdogLowSeconds;
    private boolean watchdogPaused;

    public EventDirector(MinecraftServer server, LiveEventsServer services) {
        this.server = server;
        this.services = services;
    }

    // ================================================================================================ lanzar

    public StartResult start(StartRequest req) {
        Optional<EventDefinition> found = services.registry().get(req.eventId());
        if (found.isEmpty()) {
            return StartResult.fail("liveevents.error.unknown_event", req.eventId());
        }
        EventDefinition def = found.get();
        String dim = req.level().dimension().identifier().toString();
        if (!def.allowedDimensions().contains(dim) || !services.config().allowed_dimensions.contains(dim) && !req.preview()) {
            return StartResult.fail("liveevents.error.dimension", Texts.of(def.displayName()), dim);
        }
        if (def.scope() == Scope.CELESTIAL && req.level().dimension() == Level.NETHER) {
            return StartResult.fail("liveevents.error.no_sky", Texts.of(def.displayName()));
        }
        if (def.nightOverride() == NightOverride.REQUIRE && !req.level().isDarkOutside()) {
            return StartResult.fail("liveevents.error.night_required", Texts.of(def.displayName()));
        }
        if (req.delaySeconds() > 0) {
            delayed.add(new Delayed(req.withDelay(0), ticks + req.delaySeconds() * 20L));
            return new StartResult(null, Component.translatable("liveevents.start.delayed", Texts.of(def.displayName()), req.delaySeconds()), false, true);
        }
        if (!req.preview()) {
            long active = instances.values().stream().filter(i -> !i.preview).count();
            if (active >= services.config().max_concurrent_events) {
                return queueOrFail(req, def, "liveevents.error.max_concurrent", services.config().max_concurrent_events);
            }
            if (def.scope() == Scope.CELESTIAL) {
                for (EventInstance other : instances.values()) {
                    if (!other.preview && other.def.scope() == Scope.CELESTIAL && other.level == req.level()) {
                        return queueOrFail(req, def, "liveevents.error.celestial_conflict", Texts.of(other.def.displayName()));
                    }
                }
            }
            Long until = cooldownUntil.get(def.id());
            if (until != null && ticks < until) {
                return StartResult.fail("liveevents.error.cooldown", Texts.of(def.displayName()), (until - ticks + 19) / 20);
            }
            if (server.isHardcore() && def.lethal() && def.dealsDamage() && !req.confirm()) {
                return StartResult.fail("liveevents.error.hardcore_confirm", Texts.of(def.displayName()));
            }
        }

        Profile requested = req.profile().or(def::defaultProfile).orElse(services.config().defaultProfile());
        Profile profile = requested.min(services.config().maxProfile());
        Map<String, Double> overrides = new LinkedHashMap<>(req.params());
        Map<String, Double> vars = EventInstance.mergeVars(def, overrides);
        if (!Double.isFinite(req.timescale()) || vars.values().stream().anyMatch(v -> v==null || !Double.isFinite(v)))
            return StartResult.fail("liveevents.error.options","Non-finite event parameter");
        if (def.id().equals("liveevents:meteor_shower")) {
            for (String key : List.of("power","brutality","visual_power","destructive_power"))
                if (vars.containsKey(key) && (vars.get(key)<0 || vars.get(key)>1)) return StartResult.fail("liveevents.error.options",key+" must be 0..1");
            if(vars.getOrDefault("duration",90.0)<3 || vars.getOrDefault("duration",90.0)>86400)
                return StartResult.fail("liveevents.error.options","duration must be 3..86400s");
            for(String key:List.of("cooling_seconds","smoke_seconds"))
                if(vars.containsKey(key) && (vars.get(key)<0 || vars.get(key)>600)) return StartResult.fail("liveevents.error.options",key+" must be 0..600s");
            for(String key:List.of("wind_x","wind_z"))
                if(vars.containsKey(key) && Math.abs(vars.get(key))>4) return StartResult.fail("liveevents.error.options",key+" must be -4..4");
            if(vars.getOrDefault("radius",128.0)<8 || vars.getOrDefault("radius",128.0)>1024)
                return StartResult.fail("liveevents.error.options","radius must be 8..1024");
        }
        JsonObject overridesJson = new JsonObject();
        overrides.forEach(overridesJson::addProperty);
        WorldMode worldMode = def.worldMode().orElse(services.config().defaultWorldMode());
        int id = nextId++;
        WorldJournal.Journal journal = req.preview() || !def.modifiesWorld() || worldMode == WorldMode.PERMANENT
                ? null : services.journal().open(def.id(), id, worldMode == WorldMode.REVERT_ON_END);
        long seed = req.params().containsKey("seed") ? req.params().get("seed").longValue() : DeterministicRandom.derive(System.nanoTime() ^ req.level().getGameTime(), def.id());
        EventInstance instance = new EventInstance(id, def, req.level(), req.center(), seed, profile,
                Math.max(0.1, Math.min(2.0, req.timescale())), vars, overridesJson, req.actor(), req.preview(),
                req.previewViewer() == null ? null : req.previewViewer().getUUID(), worldMode, journal, 0);

        if (!req.preview() && !LiveEventCallbacks.START.invoker().allowStart(instance)) {
            if (journal != null) {
                services.journal().close(journal);
            }
            return StartResult.fail("liveevents.error.cancelled_by_mod", Texts.of(def.displayName()));
        }
        instances.put(id, instance);
        if (!req.preview() && def.cooldownTicks() > 0) {
            cooldownUntil.put(def.id(), ticks + def.cooldownTicks()); // tiempo mínimo entre dos lanzamientos
        }
        if (!def.lethal() && !req.preview()) {
            LethalGuard.protect(req.level(), req.level().getGameTime() + (long) def.durationTicks() + 60 * 20L);
        }
        if (def.clearWeather() && !req.preview()) {
            instance.wasRaining = req.level().isRaining();
            instance.wasThundering = req.level().isThundering();
            instance.weatherCleared = true;
            runCommand(req.level(), "weather clear " + ((int) Durations.ticksToSeconds(def.durationTicks()) + 5) + "s");
        }
        ServerNet.send(instance.viewers(), startPayload(instance));
        if (!req.preview()) {
            services.audit().log(req.actor(), "start", def.id() + " #" + id + " in " + dim + " at "
                    + fmt(req.center()) + " profile=" + profile.id() + " params=" + overridesJson);
            services.discord().notify("🌌 **" + plain(def.displayName()) + "** started in " + dim + " (" + profile.id() + ")");
            notifyAdmins(Component.translatable("liveevents.notify.started", Texts.of(def.displayName()), id, req.actor()));
        }
        return new StartResult(instance, Component.translatable(req.preview() ? "liveevents.preview.started" : "liveevents.start.ok",
                Texts.of(def.displayName()), id, profile.id()), false, false);
    }

    private StartResult queueOrFail(StartRequest req, EventDefinition def, String key, Object arg) {
        if (req.queue() || def.queuePolicy() == QueuePolicy.QUEUE) {
            queue.addLast(req);
            return new StartResult(null, Component.translatable("liveevents.start.queued", Texts.of(def.displayName()), queue.size()), true, false);
        }
        return StartResult.fail(key, arg);
    }

    static Payloads.EventStart startPayload(EventInstance i) {
        return new Payloads.EventStart(i.id, i.def.id(), i.level.dimension().identifier().toString(), i.center.x, i.center.y,
                i.center.z, i.seed, i.ticksIncludingAftermath(), i.profile.id(), i.timescale, i.paramOverrides.toString(), i.preview, i.paused);
    }

    // ================================================================================================ tick

    public void tick() {
        ticks++;
        services.cooling().tick();
        processDelayed();
        // pause_when_empty: se pausa cuando el servidor SE QUEDA vacío y se reanuda al entrar alguien. Un evento
        // lanzado desde la consola sin nadie conectado sigue en marcha.
        int players = server.getPlayerCount();
        if (services.config().pause_when_empty) {
            if (lastPlayerCount > 0 && players == 0 && !autoPaused) {
                autoPaused = true;
                instances.values().forEach(i -> setPaused(i, true));
            } else if (autoPaused && players > 0) {
                autoPaused = false;
                instances.values().forEach(i -> setPaused(i, false));
            }
        }
        lastPlayerCount = players;
        if (instances.isEmpty() && draining.isEmpty() && queue.isEmpty()) {
            services.scheduler().tick(); // reversiones pendientes
            if(ticks%20==0) {services.journal().flushAll();saveCooling();}
            if (ticks % 200 == 0) {
                expireScars();
            }
            return;
        }
        for (EventInstance instance : new ArrayList<>(instances.values())) {
            try {
                tickInstance(instance);
            } catch (RuntimeException e) {
                LiveEvents.LOGGER.error("Event #{} ({}) failed and was stopped", instance.id, instance.def.id(), e);
                finish(instance, "error", true);
            }
        }
        try {
            services.scheduler().tick();
        } catch (WorkScheduler.TaskFailure failure) {
            LiveEvents.LOGGER.error("A block task of {} failed; that task was dropped", failure.owner(), failure.getCause());
        }
        Iterator<EventInstance> it = draining.iterator();
        while (it.hasNext()) {
            EventInstance i = it.next();
            if (services.scheduler().pending(i.owner()) == 0 && !i.meteors.hasResidue(i.ticksIncludingAftermath())) {
                it.remove();
                closeJournal(i);
            }
        }
        if (ticks % 20 == 0) {
            services.journal().flushAll();
            saveCooling();
            watchdog();
            sendPerf();
        }
        if (ticks % 200 == 0) {
            expireScars();
            graceUntil.values().removeIf(t -> t < ticks);
        }
    }

    private void tickInstance(EventInstance i) {
        if (i.paused) {
            return;
        }
        i.eventTicks += i.timescale;
        double phaseTime=i.eventTicks;
        if(i.def.id().equals("liveevents:meteor_shower")) {
            if(i.eventTicks>=i.durationSeconds()*20-500) i.meteorActivityClosed=true;
            phaseTime=com.takumistudios.liveevents.core.plan.MeteorTimeline.phaseTime(i.eventTicks,i.durationSeconds()*20);
        }
        int phase = i.def.phaseIndexAt(phaseTime);
        if(i.meteorActivityClosed) phase=Math.max(phase,i.phaseIndex);
        if (phase != i.phaseIndex && phase >= 0) {
            i.phaseIndex = phase;
            ServerNet.send(i.viewers(), new Payloads.EventPhase(i.id, phase, i.eventTicks));
            String phaseId = i.def.phases().get(phase).id();
            if (!i.preview) {
                LiveEventCallbacks.PHASE_CHANGE.invoker().onPhaseChange(i, phaseId);
            }
        }
        for (EventInstance.ModuleRuntime rt : i.modules) {
            if (rt.disabled || rt.ended) {
                continue;
            }
            try {
                if (!rt.started && i.eventTicks >= rt.spec.fromTicks()) {
                    i.ensureImpl(rt);
                    rt.started = true;
                    rt.impl.start(rt);
                }
                if (rt.started) {
                    rt.impl.tick(rt);
                    if (i.eventTicks >= (rt.spec.shortType().equals("meteor_impacts") ? i.durationSeconds()*20 : rt.spec.toTicks())) {
                        rt.ended = true;
                        rt.impl.end(rt);
                    }
                }
            } catch (RuntimeException e) {
                disableModule(i, rt, e);
            }
        }
        if (ticks % 20 == 0) {
            ServerNet.send(i.viewers(), new Payloads.EventClock(i.id, i.eventTicks, i.timescale));
        }
        if (i.eventTicks >= i.durationSeconds() * 20) {
            finish(i, "completed", false);
        }
    }

    private void disableModule(EventInstance i, EventInstance.ModuleRuntime rt, RuntimeException e) {
        rt.disabled = true;
        LiveEvents.LOGGER.error("Module {} of event #{} ({}) failed and was disabled; the rest of the event continues",
                rt.spec.type(), i.id, i.def.id(), e);
        notifyAdmins(Component.translatable("liveevents.notify.module_failed", rt.spec.shortType(), i.id));
        if (rt.impl != null && rt.started && !rt.ended) {
            try {
                rt.impl.end(rt);
            } catch (RuntimeException ignored) {
                LiveEvents.LOGGER.debug("Module {} also failed while ending", rt.spec.type());
            }
        }
    }

    private void processDelayed() {
        if (delayed.isEmpty()) {
            return;
        }
        Iterator<Delayed> it = delayed.iterator();
        while (it.hasNext()) {
            Delayed d = it.next();
            long remaining = d.fireTick - ticks;
            if (remaining <= 0) {
                it.remove();
                StartResult result = start(d.request);
                if (!result.ok()) {
                    notifyAdmins(result.message());
                }
            } else if (remaining % 20 == 0) {
                Optional<EventDefinition> def = services.registry().get(d.request.eventId());
                Component text = Component.translatable("liveevents.countdown", def.map(x -> Texts.of(x.displayName()))
                        .orElse(Component.literal(d.request.eventId())), remaining / 20);
                for (ServerPlayer p : d.request.level().players()) {
                    if (!d.request.preview() || p == d.request.previewViewer()) {
                        p.connection.send(new ClientboundSetActionBarTextPacket(text));
                    }
                }
            }
        }
    }

    // ================================================================================================ terminar

    /**
     * Termina una instancia.
     *
     * @param reason    completed, stopped, panic, error, shutdown
     * @param cancelWork true para descartar las tareas de bloques pendientes (stop, panic)
     */
    public void finish(EventInstance i, String reason, boolean cancelWork) {
        if (instances.remove(i.id) == null) {
            return;
        }
        i.finishedAtGameTime=i.level.getGameTime();
        for (EventInstance.ModuleRuntime rt : i.modules) {
            if (rt.started && !rt.ended && !rt.disabled && rt.impl != null) {
                rt.ended = true;
                try {
                    rt.impl.end(rt);
                } catch (RuntimeException e) {
                    LiveEvents.LOGGER.warn("Module {} failed while ending", rt.spec.type(), e);
                }
            }
        }
        com.takumistudios.liveevents.server.modules.ModuleHooks.clearLightning(i.id);
        if (!reason.equals("panic")) {
            ServerNet.send(i.viewers(), new Payloads.EventEnd(i.id, i.worldMode==WorldMode.REVERT_ON_END?"reverted":reason));
        }
        if (i.weatherCleared) {
            runCommand(i.level, "weather " + (i.wasThundering ? "thunder" : i.wasRaining ? "rain" : "clear"));
        }
        if (cancelWork || i.worldMode==WorldMode.REVERT_ON_END) {
            if(i.worldMode!=WorldMode.REVERT_ON_END)services.cooling().adopt(i);
            services.scheduler().cancel(i.owner());
            closeJournal(i);
        } else {
            draining.add(i); // el diario se cierra cuando terminen sus tareas de bloques
        }
        if (i.preview) {
            return;
        }
        if (reason.equals("completed")) {
            createScars(i);
        }
        LiveEventCallbacks.END.invoker().onEnd(i, reason);
        services.audit().log(i.actor, "end", i.def.id() + " #" + i.id + " reason=" + reason);
        services.discord().notify("✅ **" + plain(i.def.displayName()) + "** ended (" + reason + ")");
        tryQueued();
    }

    private void closeJournal(EventInstance i) {
        if (i.journal == null) {
            return;
        }
        services.journal().close(i.journal);
        if (i.worldMode == WorldMode.REVERT_ON_END && i.journal.meta().entries > 0) {
            revert(i.journal.meta(), null);
        }
    }

    private void tryQueued() {
        int n = queue.size();
        for (int k = 0; k < n && !queue.isEmpty(); k++) {
            StartRequest req = queue.pollFirst();
            StartResult r = start(new StartRequest(req.eventId(), req.level(), req.center(), req.profile(), req.timescale(),
                    req.params(), req.preview(), req.previewViewer(), false, req.confirm(), req.actor(), 0));
            if (r.instance() == null) {
                queue.addLast(req); // sigue sin caber
            } else {
                notifyAdmins(r.message());
            }
        }
    }

    /** Secuelas persistentes: nebulosa y púlsar en la posición del ancla, con los colores de sus módulos. */
    private void createScars(EventInstance i) {
        if (i.def.aftermath().isEmpty() || i.def.scope() != Scope.CELESTIAL) {
            return;
        }
        long duration = i.def.aftermathTicks();
        for (String kind : i.def.aftermath()) {
            SkyScarManager.Scar scar = new SkyScarManager.Scar();
            scar.kind = kind;
            scar.dimension = i.level.dimension().identifier().toString();
            scar.event_id = i.def.id();
            scar.azimuth = i.def.anchorAzimuth();
            scar.altitude = i.def.anchorAltitude();
            scar.seed = DeterministicRandom.derive(i.seed, kind);
            scar.created_game_time = i.level.getGameTime();
            scar.expires_game_time = duration >= Durations.MAX_TICKS ? -1 : i.level.getGameTime() + duration;
            for (ModuleSpec m : i.def.allModules()) {
                if (m.shortType().equals(kind)) {
                    ModuleParams p = new ModuleParams(ModuleParams.forProfile(m.raw(), i.profile), i.def.palette(), i.vars);
                    if (kind.equals("nebula")) {
                        for (Integer c : p.colors("colors", List.of(0xFF0000, 0x00CED1, 0xC8B400))) {
                            scar.colors.add(Palette.toHex(c));
                        }
                        scar.span_deg = p.number("span_deg", 180);
                    } else {
                        scar.colors.add(Palette.toHex(p.color("color", 0x7DF9FF)));
                    }
                }
            }
            services.scars().add(scar);
        }
        sendScars(i.level);
    }

    // ================================================================================================ control

    /** Olvida los enfriamientos (para pruebas y para /le reload). */
    public void clearCooldowns() {
        cooldownUntil.clear();
    }

    public Collection<EventInstance> active() {
        return List.copyOf(instances.values());
    }

    public Optional<EventInstance> get(int id) {
        return Optional.ofNullable(instances.get(id));
    }

    public int queued() {
        return queue.size();
    }

    public boolean stop(int id, String actor) {
        EventInstance i = instances.get(id);
        if (i == null) {
            var iterator=draining.iterator();
            while(iterator.hasNext()) {
                EventInstance residue=iterator.next();
                if(residue.id==id) {
                    services.cooling().adopt(residue);
                    services.scheduler().cancel(residue.owner());
                    if(residue.journal!=null)services.journal().close(residue.journal);
                    ServerNet.send(residue.viewers(),new Payloads.EventEnd(id,"stopped"));iterator.remove();
                    services.audit().log(actor,"stop_aftermath","#"+id);return true;
                }
            }
            return false;
        }
        finish(i, "stopped", true);
        services.audit().log(actor, "stop", i.def.id() + " #" + id);
        return true;
    }

    public int stopAll(String actor) {
        int n = instances.size();
        for (EventInstance i : new ArrayList<>(instances.values())) {
            finish(i, "stopped", true);
        }
        for(EventInstance residue:new ArrayList<>(draining)) { if(stop(residue.id,actor))n++; }
        queue.clear();
        delayed.clear();
        services.audit().log(actor, "stop_all", n + " instances");
        return n;
    }

    /**
     * /le panic (§21.5): detiene todo, corta al instante los efectos de cliente, vacía la cola de bloques y quita los
     * efectos de estado del mod. No revierte el mundo.
     */
    public int panic(String actor) {
        int n = instances.size();
        for (EventInstance i : new ArrayList<>(instances.values())) {
            finish(i, "panic", true);
        }
        queue.clear();
        delayed.clear();
        for(EventInstance i:draining) services.cooling().adopt(i);
        services.scheduler().cancel(null);
        services.cooling().resetScheduling();
        for (EventInstance i : new ArrayList<>(draining)) {
            closeJournal(i);
        }
        draining.clear();
        for (ServerPlayer p : server.getPlayerList().getPlayers()) {
            ServerNet.send(p, new Payloads.PanicStop());
            if (LEContent.RADIATION_BURN != null) {
                p.removeEffect(LEContent.RADIATION_BURN);
            }
        }
        LethalGuard.clear();
        services.audit().log(actor, "panic", n + " instances stopped");
        services.discord().notify("🛑 LiveEvents emergency stop (" + n + " events)");
        return n;
    }

    public void setPaused(EventInstance i, boolean paused) {
        if (i.paused == paused) {
            return;
        }
        i.paused = paused;
        ServerNet.send(i.viewers(), new Payloads.EventPause(i.id, paused, i.eventTicks));
    }

    /** Salta a la fase siguiente o a una concreta. */
    public boolean skip(EventInstance i, String phaseId) {
        Optional<PhaseDef> target;
        if (phaseId == null) {
            int idx = i.def.phaseIndexAt(i.eventTicks);
            target = idx + 1 < i.def.phases().size() ? Optional.of(i.def.phases().get(idx + 1)) : Optional.empty();
        } else {
            target = i.def.phase(phaseId);
        }
        if (target.isEmpty()) {
            return false;
        }
        seek(i, target.get().fromTicks());
        return true;
    }

    /** Salta a un momento: los módulos cuyo tramo ya pasó se dan por terminados sin ejecutarse. */
    public void seek(EventInstance i, double eventTicks) {
        double t = Math.max(0, Math.min(i.def.durationTicks() - 1, eventTicks));
        i.eventTicks = t;
        for (EventInstance.ModuleRuntime rt : i.modules) {
            if (rt.spec.toTicks() <= t && !rt.ended) {
                if (rt.started && rt.impl != null && !rt.disabled) {
                    try {
                        rt.impl.end(rt);
                    } catch (RuntimeException e) {
                        LiveEvents.LOGGER.debug("Module {} failed while ending on seek", rt.spec.type());
                    }
                }
                rt.ended = true;
            } else if (rt.spec.fromTicks() > t && (rt.started || rt.ended)) {
                // Salto hacia atrás: el módulo se podrá volver a ejecutar
                if (rt.started && !rt.ended && rt.impl != null) {
                    try {
                        rt.impl.end(rt);
                    } catch (RuntimeException e) {
                        LiveEvents.LOGGER.debug("Module {} failed while ending on seek", rt.spec.type());
                    }
                }
                rt.started = false;
                rt.ended = false;
                rt.impl = null;
            }
        }
        i.phaseIndex = -1;
        // EventPause fija el reloj del cliente con exactitud (EventClock solo corrige poco a poco)
        ServerNet.send(i.viewers(), new Payloads.EventPause(i.id, i.paused, i.eventTicks));
    }

    /** /le set: cambia un parámetro en vivo. */
    public void set(EventInstance i, String param, double value) {
        if (!Double.isFinite(value)) throw new IllegalArgumentException("Non-finite parameter");
        if ((param.equals("power") || param.equals("visual_power") || param.equals("destructive_power") || param.equals("brutality")) && (value < 0 || value > 1)) throw new IllegalArgumentException("Expected 0..1");
        if ((param.equals("cooling_seconds")||param.equals("smoke_seconds")) && (value<0||value>600)) throw new IllegalArgumentException("Expected 0..600s");
        if ((param.equals("wind_x")||param.equals("wind_z")) && Math.abs(value)>4) throw new IllegalArgumentException("Expected -4..4 blocks/s");
        if (param.equals("remaining") || param.equals("duration")) {
            double requested = param.equals("remaining") ? i.eventTicks / 20 + value : value;
            if (value < 0 || requested > 86400) throw new IllegalArgumentException("Duration out of range");
            double minimum = Math.max(i.eventTicks + 500, i.meteors.lastImpactTick() + 30) / 20;
            value = Math.max(requested, minimum);
            param = "duration";
        }
        i.vars.put(param, value);
        i.paramOverrides.addProperty(param, value);
        for (EventInstance.ModuleRuntime rt : i.modules) {
            rt.refreshParams();
        }
        ServerNet.send(i.viewers(), new Payloads.EventKeyframe(i.id, i.paramOverrides.toString()));
    }

    public void setTimescale(EventInstance i, double timescale) {
        i.timescale = Math.max(0.1, Math.min(2.0, timescale));
        ServerNet.send(i.viewers(), new Payloads.EventClock(i.id, i.eventTicks, i.timescale));
    }

    // ================================================================================================ jugadores

    /** Estado completo al entrar, reaparecer o cambiar de dimensión (§9.3). */
    public void syncPlayer(ServerPlayer player) {
        if (!ServerNet.hasMod(player)) {
            return;
        }
        ServerNet.send(player, new Payloads.EventReset());
        for (EventInstance i : instances.values()) {
            if (i.viewers().contains(player)) {
                ServerNet.send(player, startPayload(i));
                i.meteors.sync(player);
            }
        }
        for(EventInstance i:draining) {
            if(i.meteors.hasResidue(i.ticksIncludingAftermath()) && i.viewers().contains(player)) {
                ServerNet.send(player,startPayload(i));i.meteors.sync(player);
                ServerNet.send(player,new Payloads.EventEnd(i.id,"completed"));
            }
        }
        ServerNet.send(player, new Payloads.SkyScarState(services.scars().json(player.level().dimension().identifier().toString())));
    }

    public void onJoin(ServerPlayer player) {
        if (!ServerNet.hasMod(player)) {
            String message=services.config().missing_mod_message;
            player.connection.disconnect(message.isBlank() ? Component.translatable("liveevents.client.update_required",LiveEvents.version(),
                    "https://github.com/AloneX15/LiveEvents-mod/releases/tag/dev") : Component.literal(message));
            return;
        }
        ServerNet.send(player, new Payloads.Handshake(Payloads.PROTOCOL, LiveEvents.version(), services.config().max_profile,
                services.config().photosensitivity_notice));
        ServerNet.sendDefinitions(player, services.registry().syncJson());
        syncPlayer(player);
    }

    /** Segundos de gracia al reaparecer en mitad de una fase dañina (§24). */
    public void onRespawn(ServerPlayer player) {
        graceUntil.put(player.getUUID(), ticks + services.config().respawn_grace_seconds * 20L);
        syncPlayer(player);
    }

    public boolean inGrace(ServerPlayer player) {
        Long until = graceUntil.get(player.getUUID());
        return until != null && ticks < until;
    }

    public void onLeave(ServerPlayer player) {
        debugTimeline.remove(player.getUUID());
        debugPerf.remove(player.getUUID());
        graceUntil.remove(player.getUUID());
    }

    /** Tras /reload o /le reload: definiciones nuevas a todos. */
    public void broadcastDefinitions() {
        for (ServerPlayer p : server.getPlayerList().getPlayers()) {
            ServerNet.sendDefinitions(p, services.registry().syncJson());
        }
    }

    public void sendScars(ServerLevel level) {
        String json = services.scars().json(level.dimension().identifier().toString());
        ServerNet.send(level.players(), new Payloads.SkyScarState(json));
    }

    private void expireScars() {
        for (ServerLevel level : server.getAllLevels()) {
            if (!services.scars().expire(level.dimension().identifier().toString(), level.getGameTime()).isEmpty()) {
                sendScars(level);
            }
        }
    }

    public boolean toggleDebugTimeline(ServerPlayer player) {
        boolean on = debugTimeline.add(player.getUUID()) || !debugTimeline.remove(player.getUUID());
        ServerNet.send(player, new Payloads.DebugOverlay(on, debugPerf.contains(player.getUUID()), ""));
        return on;
    }

    public boolean toggleDebugPerf(ServerPlayer player) {
        boolean on = debugPerf.add(player.getUUID()) || !debugPerf.remove(player.getUUID());
        ServerNet.send(player, new Payloads.DebugOverlay(debugTimeline.contains(player.getUUID()), on, perfText()));
        return on;
    }

    private void sendPerf() {
        if (debugPerf.isEmpty()) {
            return;
        }
        String text = perfText();
        for (UUID id : debugPerf) {
            ServerPlayer p = server.getPlayerList().getPlayer(id);
            if (p != null) {
                ServerNet.send(p, new Payloads.DebugOverlay(debugTimeline.contains(id), true, text));
            }
        }
    }

    public String perfText() {
        WorkScheduler.Stats s = services.scheduler().stats();
        return String.format(java.util.Locale.ROOT, "LiveEvents %.2f ms/tick | %d tasks | %d blocks | queue %d | load %.0f%% | MSPT %.1f | events %d",
                s.lastTickMs(), s.lastTickTasks(), s.lastTickBlocks(), s.pending(), services.scheduler().loadFactor() * 100,
                server.getAverageTickTimeNanos() / 1_000_000.0, instances.size());
    }

    // ================================================================================================ watchdog

    /**
     * Si el MSPT supera el umbral durante la ventana, reduce el presupuesto al mínimo; si aun así no se recupera, pausa
     * los eventos y avisa. Nunca cancela ni revierte por su cuenta (§21.4).
     */
    private void watchdog() {
        double mspt = server.getAverageTickTimeNanos() / 1_000_000.0;
        int window = services.config().watchdog_seconds;
        if (mspt > services.config().watchdog_mspt) {
            watchdogHighSeconds++;
            watchdogLowSeconds = 0;
            if (watchdogHighSeconds == window) {
                services.scheduler().setLoadFactor(0.1);
                notifyAdmins(Component.translatable("liveevents.watchdog.reduced", String.format(java.util.Locale.ROOT, "%.0f", mspt)));
            } else if (watchdogHighSeconds == window * 2 && !watchdogPaused) {
                watchdogPaused = true;
                for (EventInstance i : instances.values()) {
                    setPaused(i, true);
                }
                notifyAdmins(Component.translatable("liveevents.watchdog.paused", String.format(java.util.Locale.ROOT, "%.0f", mspt)));
                services.audit().log("watchdog", "pause_all", "mspt=" + mspt);
            }
        } else {
            watchdogLowSeconds++;
            watchdogHighSeconds = 0;
            if (watchdogLowSeconds >= window && services.scheduler().loadFactor() < 1) {
                services.scheduler().setLoadFactor(services.scheduler().loadFactor() + 0.1);
            }
        }
    }

    // ================================================================================================ reversión

    /**
     * Revierte un diario de forma gradual con el presupuesto por tick. Solo restaura las posiciones que siguen con el
     * estado que puso el evento: si un jugador construyó encima, no se toca.
     */
    public void revert(WorldJournal.Meta meta, CommandSourceStack source) {
        services.cooling().cancelJournal(meta.id);
        var remaining=draining.iterator();
        while(remaining.hasNext()) {
            EventInstance i=remaining.next();
            if(i.journal!=null && i.journal.id().equals(meta.id)) {
                services.scheduler().cancel(i.owner());services.journal().close(i.journal);
                ServerNet.send(i.viewers(),new Payloads.EventEnd(i.id,"reverted"));remaining.remove();
            }
        }
        services.audit().log(source == null ? "revert_on_end" : source.getTextName(), "revert", meta.id);
        services.journal().readAll(meta.id).thenAccept(entries -> server.execute(() -> {
            HolderLookup<Block> blocks = server.registryAccess().lookupOrThrow(Registries.BLOCK);
            Map<String, BlockState> cache = new HashMap<>();
            int[] stats = new int[3]; // restaurados, modificados por jugadores, en chunks descargados
            int[] index = {entries.size() - 1};
            services.scheduler().submit("revert-" + meta.id, WorkScheduler.Priority.BLOCKS, allowance -> {
                int changed = 0;
                while (index[0] >= 0 && changed < allowance) {
                    WorldJournal.Entry e = entries.get(index[0]--);
                    ServerLevel level = server.getLevel(ResourceKey.create(Registries.DIMENSION, Identifier.parse(e.dimension())));
                    BlockPos pos = BlockPos.of(e.pos());
                    if (level == null || !level.isLoaded(pos)) {
                        stats[2]++;
                        continue;
                    }
                    if (!BlockStateParser.serialize(level.getBlockState(pos)).equals(e.newState())) {
                        stats[1]++;
                        continue;
                    }
                    BlockState old = cache.computeIfAbsent(e.oldState(), s -> parse(blocks, s));
                    if (old != null && level.setBlock(pos, old, Block.UPDATE_ALL, 512)) {
                        stats[0]++;
                        changed++;
                    }
                }
                if (index[0] < 0) {
                    if (stats[2] == 0) {
                        services.journal().markReverted(meta.id);
                    }
                    Component msg = Component.translatable(stats[2] == 0 ? "liveevents.revert.done" : "liveevents.revert.partial",
                            stats[0], stats[1], stats[2]);
                    if (source != null) {
                        source.sendSuccess(() -> msg, true);
                    } else {
                        notifyAdmins(msg);
                    }
                    return WorkScheduler.Result.done(changed);
                }
                return WorkScheduler.Result.more(changed);
            });
        }));
    }

    private static BlockState parse(HolderLookup<Block> blocks, String s) {
        try {
            return BlockStateParser.parseForBlock(blocks, s, false).blockState();
        } catch (Exception e) {
            LiveEvents.LOGGER.warn("Unknown block state in journal: {}", s);
            return null;
        }
    }

    // ================================================================================================ reinicios

    /** Estado de una instancia guardado al cerrar el servidor (para on_restart = resume). */
    private static final class Saved {
        String event_id;
        String dimension;
        double x, y, z;
        long seed;
        String profile;
        double timescale;
        double event_ticks;
        Map<String, Double> params;
        String actor;
    }

    private void saveCooling() {
        List<EventInstance> events=new ArrayList<>(instances.values());events.addAll(draining);
        services.cooling().save(events);
    }

    public void shutdown() {
        services.journal().flushAll();saveCooling();
        List<Saved> saved = new ArrayList<>();
        for (EventInstance i : instances.values()) {
            if (i.preview) {
                continue;
            }
            Saved s = new Saved();
            s.event_id = i.def.id();
            s.dimension = i.level.dimension().identifier().toString();
            s.x = i.center.x;
            s.y = i.center.y;
            s.z = i.center.z;
            s.seed = i.seed;
            s.profile = i.profile.id();
            s.timescale = i.timescale;
            s.event_ticks = i.eventTicks;
            s.params = new HashMap<>();
            i.paramOverrides.entrySet().forEach(e -> s.params.put(e.getKey(), e.getValue().getAsDouble()));
            s.actor = i.actor;
            saved.add(s);
        }
        for (EventInstance i : new ArrayList<>(instances.values())) {
            finish(i, "shutdown", false);
        }
        for (EventInstance i : new ArrayList<>(draining)) {
            closeJournal(i);
        }
        draining.clear();
        Path file = services.worldDir().resolve("active.json");
        try {
            if (saved.isEmpty()) {
                Files.deleteIfExists(file);
            } else {
                AtomicFiles.writeString(file, GSON.toJson(saved));
            }
        } catch (IOException e) {
            LiveEvents.LOGGER.warn("Could not save active events", e);
        }
    }

    /** Al arrancar: on_restart = cancel (por defecto) descarta; resume relanza desde el segundo guardado. */
    public void restore() {
        services.cooling().restore();
        Path file = services.worldDir().resolve("active.json");
        if (!Files.exists(file)) {
            return;
        }
        try {
            List<Saved> saved = GSON.fromJson(Files.readString(file, StandardCharsets.UTF_8), new TypeToken<List<Saved>>() {
            }.getType());
            Files.deleteIfExists(file);
            if (saved == null || saved.isEmpty()) {
                return;
            }
            if (!services.config().on_restart.equals("resume")) {
                LiveEvents.LOGGER.info("{} events were active when the server stopped; on_restart=cancel, they were not resumed", saved.size());
                return;
            }
            for (Saved s : saved) {
                ServerLevel level = server.getLevel(ResourceKey.create(Registries.DIMENSION, Identifier.parse(s.dimension)));
                if (level == null) {
                    continue;
                }
                StartResult r = start(new StartRequest(s.event_id, level, new Vec3(s.x, s.y, s.z), Profile.parse(s.profile),
                        s.timescale, s.params == null ? Map.of() : s.params, false, null, true, true, s.actor + " (resume)", 0));
                if (r.instance() != null) {
                    seek(r.instance(), s.event_ticks);
                    LiveEvents.LOGGER.info("Resumed {} at {}s", s.event_id, (int) Durations.ticksToSeconds(s.event_ticks));
                }
            }
        } catch (IOException | RuntimeException e) {
            LiveEvents.LOGGER.warn("Could not restore active events", e);
        }
    }

    // ================================================================================================ utilidades

    public void notifyAdmins(Component message) {
        for (ServerPlayer p : server.getPlayerList().getPlayers()) {
            if (Perms.check(p, Perms.NOTIFY, PermissionLevel.GAMEMASTERS)) {
                p.sendSystemMessage(Component.literal("[LiveEvents] ").append(message));
            }
        }
    }

    private static void runCommand(ServerLevel level, String command) {
        com.takumistudios.liveevents.server.modules.ModuleHooks.runCommand(level, command);
    }

    private static String fmt(Vec3 v) {
        return String.format(java.util.Locale.ROOT, "%.0f %.0f %.0f", v.x, v.y, v.z);
    }

    private static String plain(String displayName) {
        return Texts.isKey(displayName) ? displayName.substring(displayName.lastIndexOf('.') + 1) : displayName;
    }

    public long ticks() {
        return ticks;
    }
}
