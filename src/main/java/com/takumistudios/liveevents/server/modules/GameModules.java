package com.takumistudios.liveevents.server.modules;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.takumistudios.liveevents.LiveEvents;
import com.takumistudios.liveevents.api.module.ServerModule;
import com.takumistudios.liveevents.api.module.ServerModuleContext;
import com.takumistudios.liveevents.core.definition.ModuleParams;
import com.takumistudios.liveevents.core.definition.Scope;
import com.takumistudios.liveevents.core.random.DeterministicRandom;
import com.takumistudios.liveevents.core.time.Durations;
import com.takumistudios.liveevents.server.LiveEventsServer;
import com.takumistudios.liveevents.server.instance.EventInstance;
import com.takumistudios.liveevents.server.journal.BlockChanger;
import com.takumistudios.liveevents.util.Texts;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundSetEntityMotionPacket;
import net.minecraft.network.protocol.game.ClientboundSetSubtitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitlesAnimationPacket;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerBossEvent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.EntityTypeTags;
import net.minecraft.world.BossEvent;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.LightningBolt;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;

import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/** Módulos de juego del servidor: mensajes, daño, efectos, empujones, rayos, clima… */
final class GameModules {
    private GameModules() {
    }

    /** Radio de un módulo: el parámetro, o toda la dimensión (0) en eventos celestes, o el $radius del evento. */
    static double radius(ServerModuleContext ctx) {
        boolean celestial = ((EventInstance) ctx.event()).definition().scope() == Scope.CELESTIAL;
        double fallback = celestial ? 0 : ((EventInstance) ctx.event()).vars().getOrDefault("radius", 128.0);
        return Math.max(0, ctx.params().number("radius", fallback));
    }

    static List<ServerPlayer> viewers(ServerModuleContext ctx) {
        return ((EventInstance) ctx.event()).viewers();
    }

    /** Empuja una entidad y sincroniza la velocidad con el cliente si es un jugador. */
    static void push(Entity entity, Vec3 velocity) {
        Entity target = entity.isPassenger() && entity.getRootVehicle() != null ? entity.getRootVehicle() : entity;
        Vec3 v = velocity;
        if (target instanceof LivingEntity living && living.isFallFlying()) {
            v = v.scale(0.25); // con élitros, empujones suaves para no provocar caídas
        }
        Vec3 next = target.getDeltaMovement().add(v);
        double max = 2.5;
        if (next.lengthSqr() > max * max) {
            next = next.normalize().scale(max);
        }
        target.setDeltaMovement(next);
        if (target instanceof ServerPlayer player) {
            player.connection.send(new ClientboundSetEntityMotionPacket(player));
        }
    }

    static Optional<Holder<MobEffect>> effect(String id) {
        try {
            return BuiltInRegistries.MOB_EFFECT.get(Identifier.parse(id)).map(h -> (Holder<MobEffect>) h);
        } catch (RuntimeException e) {
            return Optional.empty();
        }
    }

    /** Aplica un módulo de juego simple a una sola entidad (lo usa gaze_check con sus "then"). */
    static void applyTo(ServerModuleContext ctx, LivingEntity target, JsonObject sub) {
        ModuleParams p = new ModuleParams(sub, ctx.params().palette(), ((EventInstance) ctx.event()).vars());
        String type = sub.get("type").getAsString();
        float factor = ctx.factorFor(target);
        if (factor <= 0) {
            return;
        }
        switch (type.startsWith("liveevents:") ? type.substring(11) : type) {
            case "status_effect" -> effect(p.string("effect", "minecraft:blindness")).ifPresent(h ->
                    target.addEffect(new MobEffectInstance(h, (int) Math.round(p.ticks("duration", 200) * factor),
                            Math.max(0, Math.min(9, p.integer("amplifier", 0))))));
            case "damage_area" -> ctx.damage(target, Identifier.parse(p.string("damage_type", "liveevents:stellar_burn")),
                    (float) p.range("amount", ctx.progress(), 2));
            case "ignite" -> target.igniteForSeconds((float) (p.number("seconds", 4) * factor));
            case "knockback" -> {
                Vec3 dir = target.position().subtract(ctx.event().center()).multiply(1, 0, 1);
                if (dir.lengthSqr() < 1e-4) {
                    dir = new Vec3(1, 0, 0);
                }
                push(target, dir.normalize().scale(p.range("strength", ctx.progress(), 1) * factor).add(0, 0.3, 0));
            }
            default -> LiveEvents.LOGGER.debug("gaze_check: unsupported sub-module {}", type);
        }
    }

    // ------------------------------------------------------------------------------------------------ mensajes

    static final class ChatBroadcast implements ServerModule {
        @Override
        public void start(ServerModuleContext ctx) {
            Component msg = Texts.of(ctx.params().string("message", ""));
            for (ServerPlayer p : viewers(ctx)) {
                p.sendSystemMessage(msg);
            }
        }
    }

    static final class Title implements ServerModule {
        @Override
        public void start(ServerModuleContext ctx) {
            if (ctx.event().profile().equals("cinematic") && !ctx.params().bool("force", false)) {
                return; // en cinematic no se tapa la imagen con texto
            }
            Component title = Texts.of(ctx.params().string("title", ""));
            String subtitle = ctx.params().string("subtitle", "");
            for (ServerPlayer p : viewers(ctx)) {
                p.connection.send(new ClientboundSetTitlesAnimationPacket(10, 60, 20));
                if (!subtitle.isEmpty()) {
                    p.connection.send(new ClientboundSetSubtitleTextPacket(Texts.of(subtitle)));
                }
                p.connection.send(new ClientboundSetTitleTextPacket(title));
            }
        }
    }

    static final class BossBar implements ServerModule {
        private ServerBossEvent bar;

        @Override
        public void start(ServerModuleContext ctx) {
            EventInstance instance = (EventInstance) ctx.event();
            String text = ctx.params().string("text", instance.definition().displayName());
            BossEvent.BossBarColor color;
            try {
                color = BossEvent.BossBarColor.valueOf(ctx.params().string("color", "red").toUpperCase(Locale.ROOT));
            } catch (IllegalArgumentException e) {
                color = BossEvent.BossBarColor.RED;
            }
            bar = new ServerBossEvent(UUID.randomUUID(), Texts.of(text), color, BossEvent.BossBarOverlay.NOTCHED_10);
            sync(ctx);
        }

        @Override
        public void tick(ServerModuleContext ctx) {
            if (bar != null && ctx.every(10)) {
                sync(ctx);
            }
        }

        private void sync(ServerModuleContext ctx) {
            bar.setProgress((float) Math.max(0, Math.min(1, 1 - ctx.progress())));
            Set<ServerPlayer> now = new HashSet<>(viewers(ctx));
            for (ServerPlayer p : List.copyOf(bar.getPlayers())) {
                if (!now.contains(p)) {
                    bar.removePlayer(p);
                }
            }
            for (ServerPlayer p : now) {
                bar.addPlayer(p);
            }
        }

        @Override
        public void end(ServerModuleContext ctx) {
            if (bar != null) {
                bar.removeAllPlayers();
                bar = null;
            }
        }
    }

    // ------------------------------------------------------------------------------------------------ daño y efectos

    static final class DamageArea implements ServerModule {
        @Override
        public void tick(ServerModuleContext ctx) {
            if (!ctx.every(ctx.params().ticks("interval", 20))) {
                return;
            }
            Identifier type = Identifier.parse(ctx.params().string("damage_type", "liveevents:stellar_burn"));
            float amount = (float) ctx.params().range("amount", ctx.progress(), 2);
            boolean protectedDamage = LiveEventsServer.get().config().damage_in_protected_regions;
            for (LivingEntity e : ctx.affected(radius(ctx))) {
                if (!protectedDamage && BlockChanger.inExcludedRegion(ctx.level(), e.blockPosition())) {
                    continue;
                }
                ctx.damage(e, type, amount);
            }
        }
    }

    static final class StatusEffect implements ServerModule {
        private Holder<MobEffect> effect;

        @Override
        public void start(ServerModuleContext ctx) {
            effect = effect(ctx.params().string("effect", "")).orElse(null);
            if (effect == null) {
                LiveEvents.LOGGER.warn("[{}] unknown effect {}", ctx.event().definitionId(), ctx.params().string("effect", ""));
            }
        }

        @Override
        public void tick(ServerModuleContext ctx) {
            if (effect == null) {
                return;
            }
            double interval = ctx.params().ticks("interval", -1);
            if (interval > 0 ? !ctx.every(interval) : !ctx.every(Double.MAX_VALUE / 4)) {
                return;
            }
            int duration = (int) Math.round(ctx.params().ticks("duration", 200));
            int amplifier = Math.max(0, Math.min(9, ctx.params().integer("amplifier", 0)));
            for (LivingEntity e : ctx.affected(radius(ctx))) {
                float factor = ctx.factorFor(e);
                if (factor > 0) {
                    e.addEffect(new MobEffectInstance(effect, Math.max(1, Math.round(duration * factor)), amplifier));
                }
            }
        }
    }

    static final class Ignite implements ServerModule {
        @Override
        public void tick(ServerModuleContext ctx) {
            if (!ctx.every(ctx.params().ticks("interval", 40))) {
                return;
            }
            double seconds = Math.max(0, Math.min(60, ctx.params().number("seconds", 4)));
            for (LivingEntity e : ctx.affected(radius(ctx))) {
                float factor = ctx.factorFor(e);
                if (factor > 0 && !e.fireImmune()) {
                    e.igniteForSeconds((float) (seconds * factor));
                }
            }
        }
    }

    static final class Knockback implements ServerModule {
        @Override
        public void tick(ServerModuleContext ctx) {
            double interval = ctx.params().ticks("interval", -1);
            if (interval > 0 ? !ctx.every(interval) : !ctx.every(Double.MAX_VALUE / 4)) {
                return;
            }
            double strength = Math.max(0, Math.min(10, ctx.params().range("strength", ctx.progress(), 1)));
            boolean random = ctx.params().bool("random", false);
            DeterministicRandom rng = new DeterministicRandom(DeterministicRandom.derive(ctx.moduleSeed(), (long) ctx.eventTicks()));
            for (LivingEntity e : ctx.affected(radius(ctx))) {
                float factor = ctx.factorFor(e);
                if (factor <= 0) {
                    continue;
                }
                Vec3 dir;
                if (random) {
                    double a = rng.range(0, Math.PI * 2);
                    dir = new Vec3(Math.cos(a), 0, Math.sin(a));
                } else {
                    dir = e.position().subtract(ctx.event().center()).multiply(1, 0, 1);
                    if (dir.lengthSqr() < 1e-4) {
                        dir = new Vec3(1, 0, 0);
                    }
                    dir = dir.normalize();
                }
                push(e, dir.scale(strength * factor * 0.5).add(0, random ? 0.1 : 0.35 * strength * factor, 0));
            }
        }
    }

    static final class GravityPull implements ServerModule {
        @Override
        public void tick(ServerModuleContext ctx) {
            if (!ctx.every(2)) {
                return;
            }
            double strength = Math.max(0, Math.min(2, ctx.params().range("strength", ctx.progress(), 0.1)));
            Vec3 c = ctx.event().center();
            for (LivingEntity e : ctx.affected(radius(ctx))) {
                float factor = ctx.factorFor(e);
                Vec3 to = c.subtract(e.position());
                if (factor <= 0 || to.lengthSqr() < 1) {
                    continue;
                }
                push(e, to.normalize().scale(strength * factor * 2));
            }
        }
    }

    /** Detecta quién mira al ancla del cielo y le aplica los módulos de "then" una vez por ventana. */
    static final class GazeCheck implements ServerModule {
        private final Set<UUID> applied = new HashSet<>();

        @Override
        public void tick(ServerModuleContext ctx) {
            EventInstance instance = (EventInstance) ctx.event();
            double az = Math.toRadians(instance.definition().anchorAzimuth());
            double alt = Math.toRadians(instance.definition().anchorAltitude());
            Vec3 anchor = new Vec3(Math.sin(az) * Math.cos(alt), Math.sin(alt), -Math.cos(az) * Math.cos(alt));
            double cos = Math.cos(Math.toRadians(Math.max(1, Math.min(90, ctx.params().number("cone_deg", 20)))));
            List<JsonElement> then = ctx.params().array("then").asList();
            for (ServerPlayer p : ctx.level().players()) {
                if (applied.contains(p.getUUID()) || !instance.accepts(p) || !ctx.exposed(p)) {
                    continue;
                }
                if (p.getLookAngle().dot(anchor) >= cos) {
                    applied.add(p.getUUID());
                    for (JsonElement sub : then) {
                        if (sub.isJsonObject()) {
                            applyTo(ctx, p, sub.getAsJsonObject());
                        }
                    }
                }
            }
        }
    }

    static final class LightningStorm implements ServerModule {
        private DeterministicRandom random;

        @Override
        public void start(ServerModuleContext ctx) {
            random = new DeterministicRandom(ctx.moduleSeed());
        }

        @Override
        public void tick(ServerModuleContext ctx) {
            EventInstance instance = (EventInstance) ctx.event();
            int cap = Math.min(ctx.params().integer("max", 12), LiveEventsServer.get().config().max_lightning_per_event);
            if (instance.preview() || LightningCounter.get(instance) >= cap) {
                return;
            }
            double interval = Math.max(4, ctx.params().ticks("interval", 30));
            if (!ctx.every(interval * random.range(0.5, 1.5))) {
                return;
            }
            List<ServerPlayer> exposed = ctx.level().players().stream()
                    .filter(p -> instance.accepts(p) && ctx.exposed(p)).toList();
            if (exposed.isEmpty()) {
                return;
            }
            ServerPlayer target = exposed.get(random.nextInt(exposed.size()));
            double dist = Math.max(4, ctx.params().number("distance", 24));
            double a = random.range(0, Math.PI * 2), d = random.range(4, dist);
            int x = (int) Math.floor(target.getX() + Math.cos(a) * d), z = (int) Math.floor(target.getZ() + Math.sin(a) * d);
            ServerLevel level = ctx.level();
            if (!BlockModules.loaded(level, x, z)) {
                return;
            }
            BlockPos pos = new BlockPos(x, level.getHeight(Heightmap.Types.MOTION_BLOCKING, x, z), z);
            if (!level.isLoaded(pos) || BlockChanger.inExcludedRegion(level, pos)) {
                return;
            }
            // Por id en el registro: la constante cambió de clase entre versiones (EntityType → EntityTypes en 26.3)
            Entity created = BuiltInRegistries.ENTITY_TYPE.getValue(Identifier.withDefaultNamespace("lightning_bolt")).create(level, EntitySpawnReason.EVENT);
            if (!(created instanceof LightningBolt bolt)) {
                return;
            }
            bolt.setPos(Vec3.atBottomCenterOf(pos));
            boolean fire = ctx.params().bool("fire", false);
            bolt.setVisualOnly(!fire);
            level.addFreshEntity(bolt);
            LightningCounter.increment(instance);
            if (!fire) {
                // Rayo sin fuego: el daño lo aplica el evento para no quemar construcciones
                for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, bolt.getBoundingBox().inflate(3), e -> true)) {
                    if (instance.accepts(e)) {
                        ctx.damage(e, Identifier.withDefaultNamespace("lightning_bolt"), 5);
                    }
                }
            }
        }
    }

    /** Contador de rayos por instancia (tope global del servidor). */
    static final class LightningCounter {
        private static final java.util.Map<Integer, Integer> COUNTS = new java.util.HashMap<>();

        static synchronized int get(EventInstance i) {
            return COUNTS.getOrDefault(i.instanceId(), 0);
        }

        static synchronized void increment(EventInstance i) {
            COUNTS.merge(i.instanceId(), 1, Integer::sum);
        }

        static synchronized void clear(int instanceId) {
            COUNTS.remove(instanceId);
        }
    }

    /** Cambia el clima y lo restaura al terminar (solo si nadie lo ha cambiado mientras tanto). */
    static final class Weather implements ServerModule {
        private boolean wasRaining;
        private boolean wasThundering;
        private String applied;

        @Override
        public void start(ServerModuleContext ctx) {
            if (ctx.event().preview()) {
                return;
            }
            ServerLevel level = ctx.level();
            wasRaining = level.isRaining();
            wasThundering = level.isThundering();
            String set = ctx.params().string("set", "clear").toLowerCase(Locale.ROOT);
            if (!set.equals("clear") && !set.equals("rain") && !set.equals("thunder")) {
                set = "clear";
            }
            int seconds = (int) Math.ceil(Durations.ticksToSeconds(ctx.spec().durationTicks())) + 5;
            applied = set;
            runCommand(level, "weather " + set + " " + seconds + "s");
        }

        @Override
        public void end(ServerModuleContext ctx) {
            if (applied == null) {
                return;
            }
            ServerLevel level = ctx.level();
            boolean stillOurs = switch (applied) {
                case "clear" -> !level.isRaining();
                case "rain" -> level.isRaining() && !level.isThundering();
                default -> level.isThundering();
            };
            if (stillOurs) {
                runCommand(level, "weather " + (wasThundering ? "thunder" : wasRaining ? "rain" : "clear"));
            }
            applied = null;
        }
    }

    static void runCommand(ServerLevel level, String command) {
        MinecraftServer server = level.getServer();
        try {
            server.getCommands().performPrefixedCommand(server.createCommandSourceStack().withLevel(level).withSuppressedOutput(), command);
        } catch (RuntimeException e) {
            LiveEvents.LOGGER.warn("Internal command failed: {}", command, e);
        }
    }

    /** Parte de servidor del día artificial: los no-muertos bajo el cielo arden. */
    static final class ArtificialDay implements ServerModule {
        @Override
        public void tick(ServerModuleContext ctx) {
            if (!ctx.params().bool("burn_undead", true) || !ctx.every(20)) {
                return;
            }
            ServerLevel level = ctx.level();
            for (ServerPlayer p : level.players()) {
                for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, p.getBoundingBox().inflate(64),
                        e -> e.is(EntityTypeTags.UNDEAD) && !e.fireImmune())) {
                    if (ctx.exposed(e) && !e.hasCustomName() && ctx.factorFor(e) > 0) {
                        e.igniteForSeconds(8);
                    }
                }
            }
        }
    }
}
