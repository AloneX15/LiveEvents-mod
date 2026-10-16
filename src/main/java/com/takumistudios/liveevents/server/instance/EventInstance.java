package com.takumistudios.liveevents.server.instance;

import com.google.gson.JsonObject;
import com.takumistudios.liveevents.LiveEvents;
import com.takumistudios.liveevents.api.LiveEventCallbacks;
import com.takumistudios.liveevents.api.LiveEventInfo;
import com.takumistudios.liveevents.api.module.ServerModule;
import com.takumistudios.liveevents.api.module.ServerModuleContext;
import com.takumistudios.liveevents.content.LEContent;
import com.takumistudios.liveevents.core.definition.EventDefinition;
import com.takumistudios.liveevents.core.definition.ModuleParams;
import com.takumistudios.liveevents.core.definition.ModuleSpec;
import com.takumistudios.liveevents.core.definition.PhaseDef;
import com.takumistudios.liveevents.core.definition.Profile;
import com.takumistudios.liveevents.core.definition.Scope;
import com.takumistudios.liveevents.core.definition.WorldMode;
import com.takumistudios.liveevents.core.random.DeterministicRandom;
import com.takumistudios.liveevents.core.scheduler.WorkScheduler;
import com.takumistudios.liveevents.core.time.Durations;
import com.takumistudios.liveevents.server.LethalGuard;
import com.takumistudios.liveevents.server.LiveEventsServer;
import com.takumistudios.liveevents.server.Perms;
import com.takumistudios.liveevents.server.journal.BlockChanger;
import com.takumistudios.liveevents.server.journal.WorldJournal;
import com.takumistudios.liveevents.server.modules.ServerModules;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.TamableAnimal;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/** Una ejecución concreta de un evento: definición, centro, dimensión, semilla, reloj y estado de sus módulos. */
public final class EventInstance implements LiveEventInfo {
    /** Radio alrededor de cada jugador en el que se buscan mobs en eventos celestes. */
    private static final double CELESTIAL_MOB_RADIUS = 96;

    final int id;
    final EventDefinition def;
    final ServerLevel level;
    final Vec3 center;
    final long seed;
    final Profile profile;
    public final com.takumistudios.liveevents.server.modules.MeteorController meteors = new com.takumistudios.liveevents.server.modules.MeteorController(this);
    double timescale;
    final Map<String, Double> vars;
    final JsonObject paramOverrides;
    final String actor;
    final boolean preview;
    final UUID previewViewer;
    final WorldMode worldMode;
    final long startGameTime;
    final WorldJournal.Journal journal;
    double eventTicks;
    boolean paused;
    int phaseIndex = -1;
    public boolean meteorActivityClosed;
    boolean weatherCleared;
    boolean wasRaining;
    boolean wasThundering;
    final List<ModuleRuntime> modules = new ArrayList<>();

    EventInstance(int id, EventDefinition def, ServerLevel level, Vec3 center, long seed, Profile profile, double timescale,
                  Map<String, Double> vars, JsonObject paramOverrides, String actor, boolean preview, UUID previewViewer,
                  WorldMode worldMode, WorldJournal.Journal journal, double startTicks) {
        this.id = id;
        this.def = def;
        this.level = level;
        this.center = center;
        this.seed = seed;
        this.profile = profile;
        this.timescale = timescale;
        this.vars = vars;
        this.paramOverrides = paramOverrides;
        this.actor = actor;
        this.preview = preview;
        this.previewViewer = previewViewer;
        this.worldMode = worldMode;
        this.journal = journal;
        this.eventTicks = startTicks;
        this.startGameTime = level.getGameTime();
        for (ModuleSpec spec : def.allModules()) {
            if (spec.side().onServer()) {
                modules.add(new ModuleRuntime(spec));
            }
        }
    }

    // ------------------------------------------------------------------------------------------------ LiveEventInfo

    @Override
    public int instanceId() {
        return id;
    }

    @Override
    public String definitionId() {
        return def.id();
    }

    @Override
    public Identifier dimension() {
        return level.dimension().identifier();
    }

    @Override
    public Vec3 center() {
        return center;
    }

    @Override
    public long seed() {
        return seed;
    }

    @Override
    public String profile() {
        return profile.id();
    }

    @Override
    public double elapsedSeconds() {
        return Durations.ticksToSeconds(eventTicks);
    }

    @Override
    public double durationSeconds() {
        return vars.getOrDefault("duration", Durations.ticksToSeconds(def.durationTicks()));
    }

    @Override
    public Optional<String> currentPhase() {
        return phaseIndex>=0 && phaseIndex<def.phases().size() ? Optional.of(def.phases().get(phaseIndex).id()) : def.phaseAt(eventTicks).map(PhaseDef::id);
    }

    @Override
    public boolean paused() {
        return paused;
    }

    @Override
    public boolean preview() {
        return preview;
    }

    public EventDefinition definition() {
        return def;
    }

    public ServerLevel level() {
        return level;
    }

    public double eventTicks() {
        return eventTicks;
    }

    public double timescale() {
        return timescale;
    }

    public String actor() {
        return actor;
    }

    public Map<String, Double> vars() {
        return vars;
    }

    public WorldJournal.Journal journal() {
        return journal;
    }

    /** Jugadores que ven el evento: la misma dimensión (y radio de interés), o solo el admin en una preview. */
    public List<ServerPlayer> viewers() {
        List<ServerPlayer> out = new ArrayList<>();
        double interest = LiveEventsServer.get().config().interest_radius;
        for (ServerPlayer p : level.players()) {
            if (preview) {
                if (p.getUUID().equals(previewViewer)) {
                    out.add(p);
                }
            } else if (interest <= 0 || def.scope() == Scope.CELESTIAL || p.position().distanceTo(center) <= interest + vars.getOrDefault("radius", 128.0)) {
                out.add(p);
            }
        }
        return out;
    }

    // ------------------------------------------------------------------------------------------------ módulos

    /** Estado de un módulo de servidor dentro de la instancia. */
    final class ModuleRuntime implements ServerModuleContext {
        final ModuleSpec spec;
        ModuleParams params;
        final long moduleSeed;
        ServerModule impl;
        boolean started;
        boolean ended;
        boolean disabled;
        private final Map<Double, Double> nextEvery = new HashMap<>();

        ModuleRuntime(ModuleSpec spec) {
            this.spec = spec;
            JsonObject raw = ModuleParams.forProfile(spec.raw(), profile);
            this.params = new ModuleParams(raw, def.palette(), vars);
            this.moduleSeed = DeterministicRandom.derive(seed, spec.index());
        }

        /** Tras /le set: vuelve a resolver los parámetros con las variables nuevas. */
        void refreshParams() {
            this.params = new ModuleParams(ModuleParams.forProfile(spec.raw(), profile), def.palette(), vars);
        }

        @Override
        public LiveEventInfo event() {
            return EventInstance.this;
        }

        @Override
        public ModuleSpec spec() {
            return spec;
        }

        @Override
        public ModuleParams params() {
            return params;
        }

        @Override
        public ServerLevel level() {
            return level;
        }

        @Override
        public double eventTicks() {
            return eventTicks;
        }

        @Override
        public double progress() {
            return spec.progress(eventTicks);
        }

        @Override
        public long moduleSeed() {
            return moduleSeed;
        }

        @Override
        public boolean every(double intervalTicks) {
            double interval = Math.max(1, intervalTicks);
            double local = eventTicks - spec.fromTicks();
            Double next = nextEvery.get(interval);
            if (next == null || local >= next) {
                nextEvery.put(interval, (next == null ? local : next) + interval);
                return true;
            }
            return false;
        }

        @Override
        public List<LivingEntity> affected(double radius) {
            return EventInstance.this.affected(radius, params.bool("requires_sky", false), spec.type());
        }

        @Override
        public boolean exposed(LivingEntity entity) {
            return EventInstance.exposed(level, entity);
        }

        @Override
        public float factorFor(LivingEntity entity) {
            return LiveEventCallbacks.BEFORE_ENTITY_AFFECTED.invoker().modify(EventInstance.this, entity, spec.type());
        }

        @Override
        public void damage(LivingEntity entity, Identifier damageType, float amount) {
            float factor = factorFor(entity);
            if (factor <= 0 || amount <= 0) {
                return;
            }
            Optional<net.minecraft.world.damagesource.DamageSource> source = LEContent.damageSource(level, damageType);
            if (source.isEmpty()) {
                LiveEvents.LOGGER.debug("Unknown damage type {}; using liveevents:stellar_burn", damageType);
                source = LEContent.damageSource(level, LEContent.STELLAR_BURN.identifier());
            }
            source.ifPresent(s -> LethalGuard.hurt(level, entity, s, amount * factor));
        }

        @Override
        public boolean setBlock(BlockPos pos, BlockState state) {
            if (preview) return false;
            if (spec.shortType().equals("meteor_impacts") && Math.hypot(pos.getX()-center.x,pos.getZ()-center.z)>vars.getOrDefault("radius",128.0)) return false;
            return BlockChanger.set(EventInstance.this, level, pos, state);
        }

        @Override
        public void submit(WorkScheduler.Priority priority, WorkScheduler.Task task) {
            LiveEventsServer.get().scheduler().submit(owner(), priority, task);
        }
    }

    String owner() {
        return "instance-" + id;
    }

    void ensureImpl(ModuleRuntime rt) {
        if (rt.impl == null && !rt.disabled) {
            rt.impl = ServerModules.create(rt.spec);
        }
    }

    // ------------------------------------------------------------------------------------------------ entidades

    public static boolean exposed(ServerLevel level, LivingEntity e) {
        int top = level.getHeight(Heightmap.Types.MOTION_BLOCKING, e.getBlockX(), e.getBlockZ());
        return e.getEyeY() >= top;
    }

    List<LivingEntity> affected(double radius, boolean requiresSky, String effectType) {
        if (preview) {
            return List.of();
        }
        Set<LivingEntity> found = new LinkedHashSet<>();
        boolean whole = def.scope() == Scope.CELESTIAL && (radius <= 0 || radius >= 4096);
        if (whole) {
            for (ServerPlayer p : level.players()) {
                found.add(p);
                if (wantsMobs()) {
                    found.addAll(level.getEntitiesOfClass(LivingEntity.class, p.getBoundingBox().inflate(CELESTIAL_MOB_RADIUS), e -> !(e instanceof ServerPlayer)));
                }
            }
        } else {
            double r = Math.max(0, radius);
            AABB box = new AABB(center.x - r, level.getMinY(), center.z - r, center.x + r, level.getMaxY() + 1, center.z + r);
            double r2 = r * r;
            for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, box, e -> true)) {
                double dx = e.getX() - center.x, dz = e.getZ() - center.z;
                if (dx * dx + dz * dz <= r2) {
                    found.add(e);
                }
            }
        }
        List<LivingEntity> out = new ArrayList<>(found.size());
        for (LivingEntity e : found) {
            if (!e.isAlive() || !accepts(e)) {
                continue;
            }
            if (requiresSky && !exposed(level, e)) {
                continue;
            }
            out.add(e);
        }
        return out;
    }

    private boolean wantsMobs() {
        Set<String> a = def.affects();
        return a.contains("hostile") || a.contains("passive") || a.contains("pets") || a.contains("named");
    }

    /** Filtros de "affects" (§8.3): por defecto jugadores y mobs hostiles; nunca mascotas ni entidades con nombre. */
    public boolean accepts(LivingEntity e) {
        Set<String> a = def.affects();
        if (e instanceof ServerPlayer p) {
            if (!a.contains("players") || p.isCreative() || p.isSpectator() || LiveEventsServer.get().director().inGrace(p)) {
                return false;
            }
            return !Perms.bypassDamage(p);
        }
        if (e.hasCustomName() && !a.contains("named")) {
            return false;
        }
        if (e instanceof TamableAnimal t && t.isTame()) {
            return a.contains("pets");
        }
        if (e instanceof Enemy) {
            return a.contains("hostile");
        }
        if (e instanceof Animal) {
            return a.contains("passive");
        }
        return a.contains("passive");
    }

    /** Parámetros vigentes que difieren de la definición (para EventStart). */
    JsonObject overridesJson() {
        return paramOverrides;
    }

    static Map<String, Double> mergeVars(EventDefinition def, Map<String, Double> overrides) {
        Map<String, Double> vars = new LinkedHashMap<>(def.params());
        vars.putAll(overrides);
        vars.putIfAbsent("duration", Durations.ticksToSeconds(def.durationTicks()));
        return vars;
    }
}
