package com.takumistudios.liveevents.server.modules;

import com.takumistudios.liveevents.LiveEvents;
import com.takumistudios.liveevents.api.module.ServerModule;
import com.takumistudios.liveevents.api.module.ServerModuleContext;
import com.takumistudios.liveevents.core.definition.Scope;
import com.takumistudios.liveevents.core.plan.CrackPlan;
import com.takumistudios.liveevents.core.random.DeterministicRandom;
import com.takumistudios.liveevents.core.scheduler.WorkScheduler.Priority;
import com.takumistudios.liveevents.core.scheduler.WorkScheduler.Result;
import com.takumistudios.liveevents.server.BlockPalette;
import com.takumistudios.liveevents.server.LiveEventsServer;
import com.takumistudios.liveevents.server.instance.EventInstance;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;

/** Módulos que cambian bloques. Todo pasa por el planificador (presupuesto) y por el diario (reversión). */
final class BlockModules {
    private BlockModules() {
    }

    static BlockState block(String id, BlockState fallback) {
        try {
            Identifier key = Identifier.parse(id);
            return BuiltInRegistries.BLOCK.containsKey(key) ? BuiltInRegistries.BLOCK.getValue(key).defaultBlockState() : fallback;
        } catch (RuntimeException e) {
            return fallback;
        }
    }

    /** ¿Está cargado el chunk de la columna? Se comprueba antes de leer el heightmap para no forzar su carga. */
    static boolean loaded(ServerLevel level, int x, int z) {
        return level.hasChunk(x >> 4, z >> 4);
    }

    /** Centros sobre los que actúa un módulo: el del evento o, en eventos celestes, los jugadores. */
    static List<Vec3> centers(ServerModuleContext ctx) {
        EventInstance instance = (EventInstance) ctx.event();
        List<Vec3> centers = new ArrayList<>();
        if (instance.definition().scope() == Scope.CELESTIAL) {
            for (ServerPlayer p : ctx.level().players()) {
                boolean near = centers.stream().anyMatch(c -> c.distanceToSqr(p.position()) < 48 * 48);
                if (!near) {
                    centers.add(p.position());
                }
            }
        } else {
            centers.add(instance.center());
        }
        return centers;
    }

    /**
     * Recorre las columnas de un disco en orden pseudoaleatorio (permutación completa), transformando la superficie con
     * una paleta y una probabilidad. Lo usan block_transform y melt.
     */
    static final class Transform implements ServerModule {
        private final String fixedPalette;
        private final AtomicBoolean ended = new AtomicBoolean();

        Transform(String fixedPalette) {
            this.fixedPalette = fixedPalette;
        }

        @Override
        public void start(ServerModuleContext ctx) {
            String paletteId = fixedPalette != null ? fixedPalette : ctx.params().string("palette", "liveevents:scorched");
            Optional<BlockPalette> palette = LiveEventsServer.get().registry().palette(paletteId);
            if (palette.isEmpty()) {
                LiveEvents.LOGGER.warn("[{}] palette {} not found; {} does nothing", ctx.event().definitionId(), paletteId, ctx.spec().shortType());
                return;
            }
            int radius = (int) Math.max(1, Math.min(512, ctx.params().number("radius", 128)));
            double perSecond = Math.max(1, ctx.params().number("per_second", 400));
            int index = 0;
            for (Vec3 c : centers(ctx)) {
                ctx.submit(Priority.BLOCKS, new ColumnSweep(ctx, palette.get(), c, radius, perSecond, DeterministicRandom.derive(ctx.moduleSeed(), index++)));
            }
        }

        @Override
        public void end(ServerModuleContext ctx) {
            ended.set(true);
        }

        private final class ColumnSweep implements com.takumistudios.liveevents.core.scheduler.WorkScheduler.Task {
            private final ServerModuleContext ctx;
            private final BlockPalette palette;
            private final int cx, cz, radius, side;
            private final long size, step;
            private final long seed;
            private final double perTick;
            private long i;
            private double credit;
            private long creditedGameTick=Long.MIN_VALUE;
            private BlockPos pendingTop;
            private int pendingDepth;
            private DeterministicRandom pendingRandom;

            ColumnSweep(ServerModuleContext ctx, BlockPalette palette, Vec3 c, int radius, double perSecond, long seed) {
                this.ctx = ctx;
                this.palette = palette;
                this.cx = (int) Math.floor(c.x);
                this.cz = (int) Math.floor(c.z);
                this.radius = radius;
                this.side = radius * 2 + 1;
                this.size = (long) side * side;
                this.seed = seed;
                this.step = coprimeStep(size, seed);
                this.perTick = perSecond / 20.0;
            }

            @Override
            public Result run(int allowance) {
                if (ended.get() || i >= size && pendingTop==null) {
                    return Result.DONE;
                }
                if(creditedGameTick!=ctx.level().getGameTime()) {
                    creditedGameTick=ctx.level().getGameTime();
                    credit = Math.min(credit + perTick, perTick * 4);
                }
                int changed = 0, scanned=0;
                ServerLevel level = ctx.level();
                double chance = Math.max(0, Math.min(1, ctx.params().range("chance", ctx.progress(), fixedPalette != null ? 1 : 0.15)));
                while (changed < allowance && scanned++<64 && (pendingTop!=null || credit>=1 && i<size)) {
                    if(pendingTop!=null) {
                        BlockPos pos=pendingTop.below(pendingDepth++);
                        if(!level.isLoaded(pos)) {pendingTop=null;continue;}
                        Optional<BlockState> to=palette.apply(level.getBlockState(pos),pendingRandom);
                        if(to.isPresent() && ctx.setBlock(pos,to.get())) changed++;
                        if(pendingDepth>=2) pendingTop=null;
                        continue;
                    }
                    long cell = Math.floorMod(i * step + seed, size);
                    i++;
                    int dx = (int) (cell % side) - radius, dz = (int) (cell / side) - radius;
                    if (dx * dx + dz * dz > radius * radius) {
                        continue;
                    }
                    credit--;
                    DeterministicRandom r = new DeterministicRandom(DeterministicRandom.derive(seed, cell));
                    if (!r.chance(chance)) {
                        continue;
                    }
                    int x = cx + dx, z = cz + dz;
                    if (!loaded(level, x, z)) {
                        continue;
                    }
                    BlockPos top = new BlockPos(x, level.getHeight(Heightmap.Types.WORLD_SURFACE, x, z) - 1, z);
                    pendingTop=top;pendingDepth=0;pendingRandom=r;
                }
                return i >= size && pendingTop==null ? Result.done(changed) : Result.more(Math.max(changed, 0));
            }
        }
    }

    /** Paso coprimo con size para visitar todas las celdas en orden pseudoaleatorio. */
    static long coprimeStep(long size, long seed) {
        long step = Math.floorMod(seed, Math.max(1, size - 1)) + 1;
        while (gcd(step, size) != 1) {
            step++;
        }
        return step;
    }

    private static long gcd(long a, long b) {
        while (b != 0) {
            long t = a % b;
            a = b;
            b = t;
        }
        return Math.abs(a);
    }

    // ------------------------------------------------------------------------------------------------ cráter

    static final class Crater implements ServerModule {
        @Override
        public void start(ServerModuleContext ctx) {
            Vec3 c = ctx.event().center();
            carve(ctx, (int) Math.floor(c.x), (int) Math.floor(c.z), ctx.params().number("radius", 6),
                    ctx.params().number("depth", 0.6), block(ctx.params().string("rim", "minecraft:basalt"), Blocks.BASALT.defaultBlockState()),
                    block(ctx.params().string("core", "minecraft:magma_block"), Blocks.MAGMA_BLOCK.defaultBlockState()), ctx.moduleSeed());
        }
    }

    /**
     * Abre un cráter con forma de cuenco irregular (ruido determinista), fondo de "core" y borde de "rim". Se encola en
     * el planificador columna a columna.
     */
    static void carve(ServerModuleContext ctx, int cx, int cz, double radius, double depthFactor, BlockState rim, BlockState core, long seed) {
        carve(ctx,cx,cz,radius,depthFactor,rim,core,seed,null);
    }

    static void carve(ServerModuleContext ctx, int cx, int cz, double radius, double depthFactor, BlockState rim, BlockState core, long seed, Integer fixedSurfaceY) {
        var future = java.util.concurrent.CompletableFuture.supplyAsync(
                () -> com.takumistudios.liveevents.core.plan.CraterShape.generate(radius, depthFactor, seed, cx, cz),
                LiveEventsServer.get().compute());
        ctx.submit(Priority.BLOCKS, new com.takumistudios.liveevents.core.scheduler.WorkScheduler.Task() {
            private int next;
            private final java.util.ArrayDeque<java.util.Map.Entry<BlockPos,BlockState>> pending = new java.util.ArrayDeque<>();
            @Override public Result run(int allowance) {
                if (!future.isDone()) return Result.more(0);
                var columns = future.join();
                int changed=0, scanned=0;
                while (changed<allowance && scanned++<64) {
                    if (!pending.isEmpty()) {
                        var op=pending.removeFirst();
                        if (ctx.setBlock(op.getKey(),op.getValue())) changed++;
                        continue;
                    }
                    if(next>=columns.size()) return Result.done(changed);
                    var column=columns.get(next++);
                    int x=cx+column.dx(),z=cz+column.dz();
                    if(!loaded(ctx.level(),x,z)) continue;
                    BlockPos top=new BlockPos(x,fixedSurfaceY==null?ctx.level().getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,x,z)-1:fixedSurfaceY-1,z);
                    if(column.rim()) { if(!ctx.level().getBlockState(top).isAir()) pending.add(java.util.Map.entry(top.above(),rim)); }
                    else {
                        for(int y=0;y<column.depth();y++) pending.add(java.util.Map.entry(top.below(y),Blocks.AIR.defaultBlockState()));
                        BlockPos floor=top.below(column.depth());
                        if(!ctx.level().getBlockState(floor).isAir()) pending.add(java.util.Map.entry(floor,column.core()?core:rim));
                    }
                }
                return Result.more(changed);
            }
        });
    }

    // ------------------------------------------------------------------------------------------------ fuego

    static final class FireSpread implements ServerModule {
        @Override
        public void start(ServerModuleContext ctx) {
            Vec3 c = ctx.event().center();
            placeFires(ctx, c.x, c.z, ctx.params().number("radius", 16), ctx.params().integer("count", 20), ctx.moduleSeed());
        }
    }

    static void placeFires(ServerModuleContext ctx, double cx, double cz, double radius, int count, long seed) {
        DeterministicRandom r = new DeterministicRandom(seed);
        int n = Math.max(0, Math.min(2000, count));
        ctx.submit(Priority.BLOCKS, new com.takumistudios.liveevents.core.scheduler.WorkScheduler.Task() {
            private int done;
            private int attempts;

            @Override
            public Result run(int allowance) {
                ServerLevel level = ctx.level();
                int changed = 0;
                while (done < n && changed < allowance && attempts < n * 4) {
                    attempts++;
                    double a = r.range(0, Math.PI * 2), d = radius * Math.sqrt(r.nextDouble());
                    int x = (int) Math.floor(cx + Math.cos(a) * d), z = (int) Math.floor(cz + Math.sin(a) * d);
                    if (!loaded(level, x, z)) {
                        continue;
                    }
                    BlockPos pos = new BlockPos(x, level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z), z);
                    if (!level.isLoaded(pos) || !level.getBlockState(pos).isAir()) {
                        continue;
                    }
                    BlockState fire = Blocks.FIRE.defaultBlockState();
                    if (fire.canSurvive(level, pos) && ctx.setBlock(pos, fire)) {
                        changed++;
                        done++;
                    }
                }
                return done >= n || attempts >= n * 4 ? Result.done(changed) : Result.more(changed);
            }
        });
    }

    // ------------------------------------------------------------------------------------------------ grieta

    /** Grieta que "corre" por el paisaje a lo largo del módulo, siguiendo una falla determinista. */
    static final class Crack implements ServerModule {
        private List<CrackPlan.Point> points;
        private int next;

        @Override
        public void start(ServerModuleContext ctx) {
            points = CrackPlan.generate(ctx.moduleSeed(), ctx.params().integer("length", 96), ctx.params().integer("width", 2),
                    ctx.params().integer("depth", 6), ctx.params().integer("branches", 3));
        }

        @Override
        public void tick(ServerModuleContext ctx) {
            if (points == null || next >= points.size() || !ctx.every(2)) {
                return;
            }
            int target = (int) Math.ceil(points.size() * Math.min(1, ctx.progress() * 1.5));
            if (target <= next) {
                return;
            }
            List<CrackPlan.Point> batch = new ArrayList<>(points.subList(next, Math.min(target, points.size())));
            next = target;
            Vec3 c = ctx.event().center();
            int cx = (int) Math.floor(c.x), cz = (int) Math.floor(c.z);
            ctx.submit(Priority.BLOCKS, new com.takumistudios.liveevents.core.scheduler.WorkScheduler.Task() {
                private int i;

                @Override
                public Result run(int allowance) {
                    ServerLevel level = ctx.level();
                    int changed = 0;
                    while (i < batch.size() && changed < allowance) {
                        CrackPlan.Point p = batch.get(i++);
                        int half = p.width() / 2;
                        for (int ox = -half; ox <= half; ox++) {
                            for (int oz = -half; oz <= half; oz++) {
                                int x = cx + p.dx() + ox, z = cz + p.dz() + oz;
                                if (!loaded(level, x, z)) {
                                    continue;
                                }
                                int surface = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z) - 1;
                                for (int y = 0; y < p.depth(); y++) {
                                    BlockPos pos = new BlockPos(x, surface - y, z);
                                    BlockState s = level.getBlockState(pos);
                                    if (!s.getFluidState().isEmpty()) {
                                        break; // no abrir lagos de lava ni vaciar agua
                                    }
                                    if (!s.isAir() && ctx.setBlock(pos, Blocks.AIR.defaultBlockState())) {
                                        changed++;
                                    }
                                }
                            }
                        }
                    }
                    return i >= batch.size() ? Result.done(changed) : Result.more(changed);
                }
            });
        }
    }
}
