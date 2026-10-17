package com.takumistudios.liveevents.test;

import com.takumistudios.liveevents.LiveEvents;
import com.takumistudios.liveevents.core.definition.ValidationIssue;
import com.takumistudios.liveevents.core.scheduler.WorkScheduler;
import com.takumistudios.liveevents.server.LiveEventsServer;
import com.takumistudios.liveevents.server.instance.EventDirector;
import com.takumistudios.liveevents.server.instance.EventInstance;
import com.takumistudios.liveevents.server.journal.WorldJournal;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;

import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Gametests de servidor: ./gradlew :26.3:runGameTest (y con -PcompatPack en la CI). Cada función del servidor tiene
 * su prueba: definiciones, diario y reversión, modo no letal, presupuesto por tick, comandos y pánico.
 */
public class LiveEventsGameTests {
    private static LiveEventsServer services() {
        LiveEventsServer s = LiveEventsServer.get();
        s.config().max_concurrent_events = 16; // los gametests se ejecutan en paralelo
        return s;
    }

    private static EventInstance start(GameTestHelper helper, String id, Vec3 relative) {
        EventDirector.StartResult result = services().director().start(new EventDirector.StartRequest(id, helper.getLevel(),
                helper.absoluteVec(relative), Optional.empty(), 1.0, Map.of(), false, null, true, true, "gametest", 0));
        if (result.instance() == null) {
            throw helper.assertionException("No se pudo lanzar " + id + ": " + result.message().getString());
        }
        return result.instance();
    }

    @GameTest(maxTicks=140, skyAccess=true)
    public void announcedWaterImpactStaysFixedAndDoesNotCarve(GameTestHelper helper) {
        for(int x=1;x<8;x++) for(int z=1;z<8;z++) {
            helper.setBlock(new BlockPos(x,0,z),Blocks.STONE);
            helper.setBlock(new BlockPos(x,1,z),Blocks.WATER);
        }
        EventInstance event=start(helper,"liveevents_test:meteor",new Vec3(4.5,2,4.5));
        int module=event.definition().allModules().stream().filter(m->m.shortType().equals("meteor_impacts")).findFirst().orElseThrow().index();
        Vec3 target=helper.absoluteVec(new Vec3(4.5,2,4.5));
        var announced=event.meteors.strike(module,target,0.4);
        if(!announced.surface().equals("water") || announced.tick()!=60) throw helper.assertionException("Incorrect immutable announcement");
        services().director().set(event,"power",1);
        if(announced.power()!=0.4 || announced.x()!=target.x) throw helper.assertionException("Announced power/position changed");
        helper.runAfterDelay(90,()->{
            helper.assertBlockPresent(Blocks.STONE,new BlockPos(4,0,4));
            services().director().finish(event,"test",true); helper.succeed();
        });
    }

    @GameTest(maxTicks=140, skyAccess=true)
    public void targetedMeteorCarvesAndRecordsJournal(GameTestHelper helper) {
        for(int x=0;x<9;x++) for(int z=0;z<9;z++) for(int y=0;y<4;y++) helper.setBlock(new BlockPos(x,y,z),Blocks.STONE);
        EventInstance event=start(helper,"liveevents_test:meteor",new Vec3(4.5,4,4.5));
        int module=event.definition().allModules().stream().filter(m->m.shortType().equals("meteor_impacts")).findFirst().orElseThrow().index();
        event.meteors.strike(module,helper.absoluteVec(new Vec3(4.5,4,4.5)),0.4);
        helper.runAfterDelay(30,()->helper.setBlock(new BlockPos(4,6,4),Blocks.STONE));
        services().director().set(event,"remaining",1);
        if(event.durationSeconds()<25) throw helper.assertionException("Closure omitted the announced impact");
        helper.runAfterDelay(100,()->{
            helper.assertBlockPresent(Blocks.AIR,new BlockPos(4,3,4));
            helper.assertBlockPresent(Blocks.STONE,new BlockPos(4,6,4)); // later construction must not move the announced height
            if(event.journal()==null || services().journal().forInstance(event.instanceId()).map(m->m.entries).orElse(0)==0) throw helper.assertionException("Crater not journaled");
            services().director().finish(event,"test",true);helper.succeed();
        });
    }

    @GameTest(maxTicks=300, skyAccess=true)
    public void meteorCoolsOnlyItsOwnBlocks(GameTestHelper helper) {
        for(int x=0;x<9;x++)for(int z=0;z<9;z++)for(int y=0;y<4;y++)helper.setBlock(new BlockPos(x,y,z),Blocks.STONE);
        helper.setBlock(new BlockPos(8,3,8),Blocks.MAGMA_BLOCK);
        EventInstance event=start(helper,"liveevents_test:meteor",new Vec3(4.5,4,4.5));
        event.vars().put("brutality",0.0);event.vars().put("cooling_seconds",1.0);event.vars().put("smoke_seconds",1.0);
        int module=event.definition().allModules().stream().filter(m->m.shortType().equals("meteor_impacts")).findFirst().orElseThrow().index();
        var meteor=event.meteors.strike(module,helper.absoluteVec(new Vec3(4.5,4,4.5)),0.4);
        if(meteor.coolingSeconds()!=1) throw helper.assertionException("Cooling snapshot="+meteor.coolingSeconds());
        helper.succeedWhen(()->{
            helper.assertBlockPresent(Blocks.OBSIDIAN,new BlockPos(4,1,4));
            helper.assertBlockPresent(Blocks.MAGMA_BLOCK,new BlockPos(8,3,8));
            services().director().finish(event,"test",true);
        });
    }

    @GameTest(maxTicks=400, skyAccess=true)
    public void persistedMeteorCoolingRecoversAndRemainsRevertible(GameTestHelper helper) {
        BlockPos relative=new BlockPos(4,1,4),absolute=helper.absolutePos(relative);
        helper.setBlock(relative,Blocks.MAGMA_BLOCK);
        var journal=services().journal().open("liveevents_test:meteor",900000,false);
        journal.record(helper.getLevel().dimension().identifier().toString(),absolute.asLong(),
                net.minecraft.commands.arguments.blocks.BlockStateParser.serialize(Blocks.STONE.defaultBlockState()),
                net.minecraft.commands.arguments.blocks.BlockStateParser.serialize(Blocks.MAGMA_BLOCK.defaultBlockState()));
        services().journal().close(journal);
        var cell=new com.takumistudios.liveevents.server.modules.MeteorCoolingRecovery.Cell(journal.id(),
                helper.getLevel().dimension().identifier().toString(),"liveevents_test:meteor",900000,absolute.asLong(),false);
        var path=services().worldDir().resolve("cooling-recovery-test.json");
        var recovery=new com.takumistudios.liveevents.server.modules.MeteorCoolingRecovery(services(),path);
        services().io().execute(()->{
            try { com.takumistudios.liveevents.util.AtomicFiles.writeString(path,new com.google.gson.Gson().toJson(List.of(cell)));recovery.restore(); }
            catch(java.io.IOException e) { throw new IllegalStateException(e); }
        });
        boolean[] reverting={false};
        helper.succeedWhen(()->{
            if(!reverting[0]) {
                helper.assertBlockPresent(Blocks.OBSIDIAN,relative);
                services().director().revert(journal.meta(),null);reverting[0]=true;
            }
            helper.assertBlockPresent(Blocks.STONE,relative);
        });
    }

    @GameTest
    public void meteorApiRejectsOutOfRangePower(GameTestHelper helper) {
        var result=services().director().start(new EventDirector.StartRequest("liveevents:meteor_shower",helper.getLevel(),
                helper.absoluteVec(new Vec3(4,2,4)),Optional.empty(),1,Map.of("power",2.0),false,null,false,true,"test",0));
        if(result.ok()) throw helper.assertionException("Out-of-range API power accepted");
        helper.succeed();
    }

    @GameTest
    public void modIsLoaded(GameTestHelper helper) {
        if (!FabricLoader.getInstance().isModLoaded(LiveEvents.MOD_ID)) {
            throw helper.assertionException("LiveEvents no está cargado");
        }
        helper.succeed();
    }

    @GameTest
    public void officialEventsAreLoadedWithoutErrors(GameTestHelper helper) {
        var registry = services().registry();
        for (String id : List.of("liveevents:supernova", "liveevents:meteor_shower", "liveevents:earthquake", "liveevents_test:scorch")) {
            if (registry.get(id).isEmpty()) {
                throw helper.assertionException("Falta la definición " + id);
            }
        }
        List<ValidationIssue> errors = registry.issues().stream()
                .filter(i -> i.isError() && i.eventId().startsWith("liveevents")).toList();
        if (!errors.isEmpty()) {
            throw helper.assertionException("Errores de validación: " + errors);
        }
        if (registry.palette("liveevents:scorched").isEmpty() || registry.palette("liveevents:melt").isEmpty()) {
            throw helper.assertionException("Faltan las paletas oficiales");
        }
        helper.succeed();
    }

    /** Cambios de bloques: pasan por el diario y /le revert los deshace (§8.5). Necesita cielo: sin él hay techo de barreras. */
    @GameTest(maxTicks = 400, skyAccess = true)
    public void blockChangesAreJournaledAndReverted(GameTestHelper helper) {
        BlockPos grass = new BlockPos(4, 1, 4);
        for (int dx = -2; dx <= 2; dx++) {
            for (int dz = -2; dz <= 2; dz++) {
                helper.setBlock(grass.offset(dx, 0, dz), Blocks.GRASS_BLOCK);
            }
        }
        EventInstance instance = start(helper, "liveevents_test:scorch", new Vec3(4.5, 2, 4.5));
        int id = instance.instanceId();
        helper.runAfterDelay(30, () -> helper.assertBlockPresent(Blocks.COARSE_DIRT, grass));
        helper.runAfterDelay(80, () -> {
            Optional<WorldJournal.Meta> meta = services().journal().forInstance(id);
            if (meta.isEmpty() || meta.get().entries == 0) {
                throw helper.assertionException("El evento no dejó cambios en el diario");
            }
            services().director().revert(meta.get(), null);
        });
        helper.runAfterDelay(90, () -> helper.succeedWhen(() -> helper.assertBlockPresent(Blocks.GRASS_BLOCK, grass)));
    }

    /** lethal: false nunca baja a nadie de medio corazón (§8.6). */
    @GameTest(maxTicks = 200)
    public void nonLethalEventNeverKills(GameTestHelper helper) {
        for (int dx = 0; dx < 8; dx++) {
            for (int dz = 0; dz < 8; dz++) {
                helper.setBlock(new BlockPos(dx, 0, dz), Blocks.STONE);
            }
        }
        @SuppressWarnings("unchecked")
        EntityType<LivingEntity> pigType = (EntityType<LivingEntity>) BuiltInRegistries.ENTITY_TYPE.getValue(Identifier.withDefaultNamespace("pig"));
        LivingEntity pig = helper.spawn(pigType, new BlockPos(4, 1, 4));
        if (pig instanceof net.minecraft.world.entity.Mob mob) {
            mob.setNoAi(true); // que no salga del radio deambulando
        }
        start(helper, "liveevents_test:harm", new Vec3(4.5, 1, 4.5));
        helper.runAfterDelay(70, () -> {
            if (!pig.isAlive()) {
                throw helper.assertionException("El evento no letal ha matado a la entidad");
            }
            if (pig.getHealth() >= pig.getMaxHealth()) {
                throw helper.assertionException("El evento no ha hecho daño");
            }
            helper.succeed();
        });
    }

    /** El trabajo de bloques respeta el presupuesto por tick (§14.2) y el tope de cambios. */
    @GameTest(maxTicks = 200, skyAccess = true)
    public void blockWorkRespectsTickBudget(GameTestHelper helper) {
        EventInstance instance = start(helper, "liveevents_test:big", new Vec3(4.5, 2, 4.5));
        int maxBlocks = services().config().max_block_changes_per_tick;
        double budget = services().config().tick_budget_ms;
        double[] worst = {0};
        double[] samples = new double[58];
        for (int t = 2; t < 60; t++) {
            int index = t - 2;
            helper.runAfterDelay(t, () -> {
                WorkScheduler.Stats s = services().scheduler().stats();
                worst[0] = Math.max(worst[0], s.lastTickMs());
                samples[index] = s.lastSliceStartMs();
                if (s.lastTickBlocks() > maxBlocks) {
                    throw helper.assertionException("Se cambiaron " + s.lastTickBlocks() + " bloques en un tick (tope " + maxBlocks + ")");
                }
            });
        }
        helper.runAfterDelay(61, () -> {
            services().director().stop(instance.instanceId(), "gametest");
            double[] sorted = samples.clone();
            java.util.Arrays.sort(sorted);
            double max = sorted[sorted.length - 1];
            LiveEvents.LOGGER.info("[gametest] planificador: último trozo empezado como muy tarde a {} ms; peor tick total {} ms (presupuesto {} ms)",
                    String.format("%.3f", max), String.format("%.3f", worst[0]), budget);
            // Nunca se empieza un trozo con el presupuesto agotado (el total puede pasarse por un setBlock vanilla)
            if (max > budget) {
                throw helper.assertionException("Se empezó trabajo a los " + max + " ms (presupuesto " + budget + " ms)");
            }
            helper.succeed();
        });
    }

    /** Los comandos funcionan desde la consola y nunca lanzan excepciones. */
    @GameTest
    public void commandsRunFromConsole(GameTestHelper helper) {
        var server = helper.getLevel().getServer();
        var source = server.createCommandSourceStack().withSuppressedOutput();
        for (String cmd : List.of("le list", "le list active", "le info supernova", "le validate", "le revert list",
                "le scars list", "le schedule list", "le sequence list", "le debug perf", "le start not_an_event",
                "le stop 9999", "le info", "le start supernova at nonsense")) {
            server.getCommands().performPrefixedCommand(source, cmd);
        }
        helper.succeed();
    }

    /** /le panic detiene todo y vacía la cola de bloques (§21.5). Se ejecuta tarde para no cortar las demás pruebas. */
    @GameTest(maxTicks = 600)
    public void panicStopsEverything(GameTestHelper helper) {
        helper.runAfterDelay(400, () -> {
            start(helper, "liveevents_test:visual", new Vec3(4.5, 2, 4.5));
            start(helper, "liveevents_test:big", new Vec3(4.5, 2, 4.5));
        });
        helper.runAfterDelay(410, () -> {
            services().director().panic("gametest");
            if (!services().director().active().isEmpty()) {
                throw helper.assertionException("Quedan eventos activos tras /le panic");
            }
            if (services().scheduler().pending() != 0) {
                throw helper.assertionException("Quedan tareas de bloques tras /le panic");
            }
            helper.succeed();
        });
    }
}
