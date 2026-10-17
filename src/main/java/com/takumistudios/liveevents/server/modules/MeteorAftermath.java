package com.takumistudios.liveevents.server.modules;

import com.takumistudios.liveevents.api.LiveEventInfo;
import com.takumistudios.liveevents.api.module.ServerModuleContext;
import com.takumistudios.liveevents.core.definition.ModuleParams;
import com.takumistudios.liveevents.core.definition.ModuleSpec;
import com.takumistudios.liveevents.core.plan.AnnouncedMeteor;
import com.takumistudios.liveevents.core.scheduler.WorkScheduler;
import com.takumistudios.liveevents.server.instance.EventInstance;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import java.util.ArrayDeque;
import java.util.List;
import java.util.function.Consumer;

/** Tracks only cells actually placed by this impact, then cools them without scanning unrelated terrain. */
public final class MeteorAftermath implements ServerModuleContext {
    private record Cell(BlockPos pos,BlockState state) { }
    private final ServerModuleContext delegate;
    private final ArrayDeque<Cell> cells=new ArrayDeque<>();
    private int writers;
    private MeteorAftermath(ServerModuleContext delegate) {
        this.delegate=delegate;((EventInstance)delegate.event()).thermal.add(this);
    }
    public List<MeteorCoolingRecovery.Cell> snapshot() {
        EventInstance instance=(EventInstance)delegate.event();
        String journal=instance.journal()==null?null:instance.journal().id();
        return cells.stream().map(cell->new MeteorCoolingRecovery.Cell(journal,instance.dimension().toString(),instance.definitionId(),instance.instanceId(),cell.pos().asLong(),cell.state().is(Blocks.FIRE))).toList();
    }

    static void create(ServerModuleContext context,AnnouncedMeteor meteor,Consumer<ServerModuleContext> work) {
        MeteorAftermath tracked=new MeteorAftermath(context);
        work.accept(tracked);
        EventInstance instance=(EventInstance)context.event();
        double due=meteor.tick()+meteor.coolingSeconds()*20;
        context.submit(WorkScheduler.Priority.CLEANUP,allowance->{
            if(instance.ticksIncludingAftermath()<due) return WorkScheduler.Result.more(0);
            int changed=0,scanned=0;
            while(!tracked.cells.isEmpty() && changed<allowance && scanned++<64) {
                Cell cell=tracked.cells.removeFirst();
                if(!context.level().isLoaded(cell.pos())) { tracked.cells.addLast(cell);continue; }
                BlockState current=context.level().getBlockState(cell.pos());
                if(cell.state().is(Blocks.FIRE)?!current.is(Blocks.FIRE):current!=cell.state()) continue;
                BlockState cooled=cell.state().is(Blocks.FIRE)?Blocks.AIR.defaultBlockState():Blocks.OBSIDIAN.defaultBlockState();
                if(context.setBlock(cell.pos(),cooled)) changed++;
            }
            if(tracked.writers==0 && tracked.cells.isEmpty()) {
                instance.thermal.remove(tracked);return WorkScheduler.Result.done(changed);
            }
            return WorkScheduler.Result.more(changed);
        });
    }

    @Override public boolean setBlock(BlockPos pos,BlockState state) {
        if(!delegate.setBlock(pos,state)) return false;
        if(state.is(Blocks.MAGMA_BLOCK)||state.is(Blocks.FIRE)) cells.addLast(new Cell(pos.immutable(),state));
        return true;
    }
    @Override public void submit(WorkScheduler.Priority priority,WorkScheduler.Task task) {
        writers++;
        delegate.submit(priority,allowance->{
            try {
                var result=task.run(allowance);
                if(result.done()) writers--;
                return result;
            } catch(RuntimeException e) { writers--;throw e; }
        });
    }
    @Override public LiveEventInfo event() { return delegate.event(); }
    @Override public ModuleSpec spec() { return delegate.spec(); }
    @Override public ModuleParams params() { return delegate.params(); }
    @Override public ServerLevel level() { return delegate.level(); }
    @Override public double eventTicks() { return delegate.eventTicks(); }
    @Override public double progress() { return delegate.progress(); }
    @Override public long moduleSeed() { return delegate.moduleSeed(); }
    @Override public boolean every(double ticks) { return delegate.every(ticks); }
    @Override public List<LivingEntity> affected(double radius) { return delegate.affected(radius); }
    @Override public boolean exposed(LivingEntity entity) { return delegate.exposed(entity); }
    @Override public float factorFor(LivingEntity entity) { return delegate.factorFor(entity); }
    @Override public void damage(LivingEntity entity,Identifier type,float amount) { delegate.damage(entity,type,amount); }
}
