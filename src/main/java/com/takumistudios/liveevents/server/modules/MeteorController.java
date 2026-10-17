package com.takumistudios.liveevents.server.modules;

import com.google.gson.Gson;
import com.takumistudios.liveevents.api.module.ServerModuleContext;
import com.takumistudios.liveevents.core.plan.AnnouncedMeteor;
import com.takumistudios.liveevents.core.plan.MeteorPlan;
import com.takumistudios.liveevents.core.random.DeterministicRandom;
import com.takumistudios.liveevents.network.Payloads;
import com.takumistudios.liveevents.network.ServerNet;
import com.takumistudios.liveevents.server.LiveEventsServer;
import com.takumistudios.liveevents.server.instance.EventInstance;
import com.takumistudios.liveevents.server.journal.BlockChanger;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;

import java.util.*;

/** Server-only targeting and immutable announcements. Never queries an unloaded column. */
public final class MeteorController {
    private static final Gson GSON = new Gson();
    public static final int APPROACH_TICKS = 60;
    private final EventInstance instance;
    private final Map<String, AnnouncedMeteor> announced = new LinkedHashMap<>();
    private final Set<String> applied = new LinkedHashSet<>();
    private final Map<UUID, Double> restedUntil = new HashMap<>();
    private record RestZone(Vec3 center,double until) { }
    private final List<RestZone> restZones = new ArrayList<>();
    private String mode = "random";
    private UUID player;
    private Vec3 coordinates;
    private int minGroup = 3;
    private double groupRadius = 24, spread = 8, groupWeight = 30, randomWeight = 70;
    private int manualIndex = 1000000000;
    private double lastAnnouncement = -100;
    private int launched, throttled;
    public int launched() { return launched; }
    public int throttled() { return throttled; }
    private boolean includeCreative, waitWhenEmpty;
    private double restTicks=200;
    public void targetingPolicy(boolean creative,boolean wait,double restSeconds) {
        if(!Double.isFinite(restSeconds)||restSeconds<0||restSeconds>3600) throw new IllegalArgumentException("rest must be 0..3600s");
        includeCreative=creative;waitWhenEmpty=wait;restTicks=restSeconds*20;
    }

    public MeteorController(EventInstance instance) { this.instance = instance; }

    public String mode() { return mode; }
    public Collection<AnnouncedMeteor> announced() { return Collections.unmodifiableCollection(announced.values()); }
    public boolean hasResidue(double tick) { return announced.values().stream().anyMatch(m->m.tick()+m.lifetimeTicks()>tick); }
    public double lastImpactTick() {
        return announced.values().stream().mapToDouble(AnnouncedMeteor::tick).max().orElse(instance.eventTicks());
    }

    public void configure(String mode, UUID player, Vec3 point, int min, double radius, double spread,
                          double randomWeight, double groupWeight) {
        if (!Set.of("random", "groups", "mixed", "player", "coordinates").contains(mode)
                || min < 3 || min > 64 || !Double.isFinite(radius) || radius < 1 || radius > 128
                || !Double.isFinite(spread) || spread < 0 || spread > 128
                || !Double.isFinite(randomWeight) || randomWeight < 0 || !Double.isFinite(groupWeight) || groupWeight < 0
                || !Double.isFinite(randomWeight+groupWeight) || randomWeight + groupWeight <= 0) throw new IllegalArgumentException("Invalid meteor targeting settings");
        if (mode.equals("player") && (player == null || instance.level().getPlayerByUUID(player) == null
                || instance.level().getPlayerByUUID(player).isSpectator() || !validPoint(instance.level().getPlayerByUUID(player).position())))
            throw new IllegalArgumentException("Player is not in the event dimension");
        if (mode.equals("coordinates") && (point == null || !validPoint(point)))
            throw new IllegalArgumentException("Target must be loaded and within the event area");
        this.mode = mode; this.player = player; this.coordinates = point; this.minGroup = min;
        this.groupRadius = radius; this.spread = spread; this.randomWeight = randomWeight; this.groupWeight = groupWeight;
    }

    private boolean validPoint(Vec3 point) {
        double area = Math.max(8, Math.min(1024, instance.vars().getOrDefault("radius", 128.0)));
        if (Math.hypot(point.x - instance.center().x, point.z - instance.center().z) > area) return false;
        BlockPos pos = BlockPos.containing(point);
        return instance.level().getWorldBorder().isWithinBounds(pos) && BlockModules.loaded(instance.level(), pos.getX(), pos.getZ())
                && pos.getY() >= instance.level().getMinY() && pos.getY() <= instance.level().getMaxY()
                && !BlockChanger.inExcludedRegion(instance.level(), pos)
                && !com.takumistudios.liveevents.server.integration.ProtectionCompat.protectedAt(instance.level(),pos);
    }

    private Vec3 surface(double x, double z) {
        int bx = (int) Math.floor(x), bz = (int) Math.floor(z);
        if (!BlockModules.loaded(instance.level(), bx, bz)) return null;
        Vec3 point = new Vec3(x, instance.level().getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, bx, bz), z);
        return validPoint(point) ? point : null;
    }

    private Vec3 select(MeteorPlan.Impact candidate, long seed) {
        restZones.removeIf(zone -> zone.until()<=instance.eventTicks());
        restedUntil.values().removeIf(until -> until<=instance.eventTicks());
        DeterministicRandom random = new DeterministicRandom(seed);
        String selected = mode;
        if (selected.equals("mixed")) selected = random.nextDouble() * (randomWeight + groupWeight) < groupWeight ? "groups" : "random";
        if (selected.equals("player")) {
            ServerPlayer target = instance.level().getPlayerByUUID(player) instanceof ServerPlayer p ? p : null;
            if (target == null || target.isSpectator() || restedUntil.getOrDefault(target.getUUID(),0.0)>instance.eventTicks()) return null;
            Vec3 at=around(target.position(),random);
            if(at!=null)restedUntil.put(target.getUUID(),instance.eventTicks()+restTicks);
            return at;
        }
        if (selected.equals("coordinates")) return around(coordinates, random);
        if (selected.equals("groups")) {
            List<ServerPlayer> eligible = instance.level().players().stream()
                    .sorted(Comparator.comparing(p -> p.getUUID().toString())).limit(128)
                    .filter(p -> !p.isSpectator() && (includeCreative || !p.isCreative()) && validPoint(p.position())
                            && restZones.stream().noneMatch(zone -> zone.center().distanceToSqr(p.position())<groupRadius*groupRadius))
                    .sorted(Comparator.comparing(p -> p.getUUID().toString())).toList();
            Set<UUID> visited = new HashSet<>();
            List<List<ServerPlayer>> groups = new ArrayList<>();
            for (ServerPlayer anchor : eligible) {
                if (visited.contains(anchor.getUUID())) continue;
                List<ServerPlayer> near = eligible.stream().filter(p -> !visited.contains(p.getUUID())
                        && p.position().distanceToSqr(anchor.position()) <= groupRadius * groupRadius).toList();
                near.forEach(p -> visited.add(p.getUUID()));
                if (near.size() >= minGroup && near.stream().noneMatch(p -> restedUntil.getOrDefault(p.getUUID(), 0.0) > instance.eventTicks())) groups.add(near);
            }
            if (!groups.isEmpty() && restZones.size()<128) {
                List<ServerPlayer> group = groups.get(random.nextInt(groups.size()));
                Vec3 center = Vec3.ZERO;
                for (ServerPlayer p : group) center = center.add(p.position());
                Vec3 target = around(center.scale(1.0 / group.size()), random);
                if (target != null) {
                    group.forEach(p -> restedUntil.put(p.getUUID(), instance.eventTicks() + restTicks));
                    restZones.add(new RestZone(target,instance.eventTicks()+restTicks));
                }
                return target;
            }
            if(waitWhenEmpty) return null;
            // Explicit default fallback: random, never target a non-eligible group.
        }
        return surface(instance.center().x + candidate.dx(), instance.center().z + candidate.dz());
    }

    private Vec3 around(Vec3 point, DeterministicRandom random) {
        double angle = random.range(0, Math.PI * 2), distance = spread * Math.sqrt(random.nextDouble());
        return surface(point.x + Math.cos(angle) * distance, point.z + Math.sin(angle) * distance);
    }

    private String variant(Vec3 point) {
        var state = instance.level().getBlockState(BlockPos.containing(point).below());
        if (state.getFluidState().is(FluidTags.WATER)) return "water";
        return state.is(Blocks.SNOW) || state.is(Blocks.SNOW_BLOCK) || state.is(Blocks.POWDER_SNOW) ? "snow" : "earth";
    }

    private AnnouncedMeteor announce(int module, int index, double tick, Vec3 at, double radius, double power,
                                     double azimuth, double angle, boolean big, long seed, double visualPower) {
        AnnouncedMeteor meteor = new AnnouncedMeteor(module, index, tick, at.x, at.y, at.z,
                Math.max(0.5, Math.min(100, radius)), power, azimuth, angle, big, variant(at), seed,
                visualPower,instance.vars().getOrDefault("cooling_seconds",120.0),com.takumistudios.liveevents.core.plan.MeteorEffects.smokeSeconds(instance.vars().getOrDefault("smoke_seconds",120.0),radius),
                instance.vars().getOrDefault("wind_x",0.2),instance.vars().getOrDefault("wind_z",0.05));
        announced.put(meteor.key(), meteor);
        launched++;
        ServerNet.send(instance.viewers(), new Payloads.Meteor(instance.instanceId(), GSON.toJson(meteor)));
        LiveEventsServer.get().audit().log(instance.actor(), "meteor_announce", "#" + instance.instanceId() + " " + mode + " " + GSON.toJson(meteor));
        return meteor;
    }

    public void prepare(ServerModuleContext ctx, List<MeteorPlan.Impact> plan) {
        announced.values().removeIf(m -> m.tick()+m.lifetimeTicks()<ctx.eventTicks());
        while(announced.size()>512) {
            var oldest=announced.entrySet().stream().filter(e->e.getValue().tick()<ctx.eventTicks()-20).findFirst();
            if(oldest.isEmpty()) break; announced.remove(oldest.get().getKey());
        }
        if (instance.meteorActivityClosed || ctx.eventTicks() >= instance.durationSeconds()*20 - 500) return;
        for (MeteorPlan.Impact candidate : plan) {
            String key = ctx.spec().index() + ":" + candidate.index();
            if (announced.containsKey(key) || applied.contains(key) || candidate.tick() < ctx.eventTicks()) continue;
            if (candidate.tick() - APPROACH_TICKS > ctx.eventTicks()) break;
            while (applied.size() > 4096) applied.remove(applied.iterator().next());
            applied.add(key); // A rejected launch is not retried every tick.
            double brutality = instance.vars().getOrDefault("brutality", 0.5);
            long seed = DeterministicRandom.derive(ctx.moduleSeed(), candidate.index());
            if (new DeterministicRandom(seed).nextDouble() >= com.takumistudios.liveevents.core.plan.MeteorIntensity.retain(brutality)) continue;
            if (ctx.eventTicks() - lastAnnouncement < 4 || announced.values().stream().filter(m -> m.tick() > ctx.eventTicks()).count() >= 24) { throttled++; continue; }
            Vec3 target = select(candidate, seed);
            if (target == null) continue;
            double power = instance.vars().getOrDefault("destructive_power",instance.vars().getOrDefault("power", 0.7));
            announce(ctx.spec().index(), candidate.index(), Math.max(candidate.tick(), ctx.eventTicks() + APPROACH_TICKS), target,
                    (candidate.craterRadius()>=100?100:Math.min(48,candidate.craterRadius() * (0.5 + power))), power, candidate.entryAzimuth(), candidate.entryAngle(), candidate.big(), seed, instance.vars().getOrDefault("visual_power",instance.vars().getOrDefault("power",power)));
            double extraChance=com.takumistudios.liveevents.core.plan.MeteorIntensity.extra(brutality);
            DeterministicRandom burst=new DeterministicRandom(seed ^ 0x6d6574656f72L);
            if(burst.nextDouble()<extraChance && announced.values().stream().filter(m->m.tick()>ctx.eventTicks()).count()<24) {
                double az=burst.range(0,Math.PI*2),distance=Math.sqrt(burst.nextDouble())*instance.vars().getOrDefault("radius",128.0);
                var extraCandidate=new MeteorPlan.Impact(candidate.index()+10000000,candidate.tick()+4,Math.cos(az)*distance,Math.sin(az)*distance,
                        candidate.craterRadius(),candidate.entryAzimuth(),candidate.entryAngle(),burst.nextDouble()<extraChance*0.25);
                Vec3 extraTarget=select(extraCandidate,seed+1);
                if(extraTarget!=null) announce(ctx.spec().index(),extraCandidate.index(),Math.max(extraCandidate.tick(),ctx.eventTicks()+APPROACH_TICKS+4),
                        extraTarget,(extraCandidate.craterRadius()>=100?100:Math.min(48,extraCandidate.craterRadius()*(0.5+power)*(extraCandidate.big()?1.4:1))),power,extraCandidate.entryAzimuth(),extraCandidate.entryAngle(),
                        extraCandidate.big(),seed+1,instance.vars().getOrDefault("visual_power",instance.vars().getOrDefault("power",power)));
            }
            lastAnnouncement = ctx.eventTicks();
        }
    }

    public List<AnnouncedMeteor> due(ServerModuleContext ctx) {
        List<AnnouncedMeteor> due = new ArrayList<>();
        for (AnnouncedMeteor m : announced.values()) {
            String key = "hit:" + m.key();
            if (m.module() == ctx.spec().index() && m.tick() <= ctx.eventTicks() && applied.add(key)) due.add(m);
        }
        return due;
    }

    public AnnouncedMeteor strike(int module, Vec3 at, double power) { return strike(module,at,power,false); }
    public AnnouncedMeteor strike(int module, Vec3 at, double power, boolean giant) {
        if (!Double.isFinite(power) || power < 0 || power > 1 || !validPoint(at)) throw new IllegalArgumentException("Invalid impact target or power");
        if (instance.eventTicks() + APPROACH_TICKS + 30 >= instance.durationSeconds()*20 || announced.values().stream().filter(m -> m.tick() > instance.eventTicks()).count() >= 24)
            throw new IllegalArgumentException("Meteor launch budget exhausted");
        int expectedY=instance.level().getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,(int)Math.floor(at.x),(int)Math.floor(at.z));
        if(Math.abs(at.y-expectedY)>1) throw new IllegalArgumentException("Impact coordinates must identify the loaded surface");
        // Manual targets use the exact requested coordinates, fixed before the warning.
        double physicalPower=instance.vars().getOrDefault("destructive_power",power);
        double visualPower=instance.vars().getOrDefault("visual_power",power);
        AnnouncedMeteor meteor = announce(module, manualIndex++, instance.eventTicks() + APPROACH_TICKS, at,
                giant ? 100 : 2 + physicalPower * 5, physicalPower, 35, 45, giant || visualPower > 0.8, DeterministicRandom.derive(instance.seed(), manualIndex),visualPower);
        return meteor;
    }

    public void sync(ServerPlayer player) {
        for (AnnouncedMeteor m : announced.values()) ServerNet.send(player, new Payloads.Meteor(instance.instanceId(), GSON.toJson(m)));
    }
}
