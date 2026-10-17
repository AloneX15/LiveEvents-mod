package com.takumistudios.liveevents.server.modules;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import com.takumistudios.liveevents.LiveEvents;
import com.takumistudios.liveevents.api.LiveEventCallbacks;
import com.takumistudios.liveevents.api.LiveEventInfo;
import com.takumistudios.liveevents.core.scheduler.WorkScheduler;
import com.takumistudios.liveevents.server.LiveEventsServer;
import com.takumistudios.liveevents.server.instance.EventInstance;
import com.takumistudios.liveevents.server.journal.BlockChanger;
import com.takumistudios.liveevents.server.journal.WorldJournal;
import com.takumistudios.liveevents.util.AtomicFiles;
import net.minecraft.commands.arguments.blocks.BlockStateParser;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

/** Crash/cancellation recovery for owned hot cells. It never reloads an event or forces a chunk to load. */
public final class MeteorCoolingRecovery {
    public record Cell(String journal,String dimension,String event,int instance,long pos,boolean fire) {
        boolean valid() {
            return dimension!=null && dimension.length()<=128 && Identifier.tryParse(dimension)!=null
                    && event!=null && event.length()<=128 && (journal==null || journal.matches("[a-zA-Z0-9_.-]{1,256}"));
        }
    }
    private static final Gson GSON=new Gson();
    private final LiveEventsServer services;
    private final Path file;
    private final ArrayDeque<Cell> pending=new ArrayDeque<>();
    private final Map<String,WorldJournal.Journal> journals=new HashMap<>();
    private boolean loading=true,scheduled,failed,lastEmpty;
    public MeteorCoolingRecovery(LiveEventsServer services,Path file) { this.services=services;this.file=file; }

    public void restore() {
        services.io().execute(()->{
            List<Cell> loaded=List.of();
            try {
                if(Files.isRegularFile(file) && Files.size(file)<=64L*1024*1024) {
                    List<Cell> parsed=GSON.fromJson(Files.readString(file),new TypeToken<List<Cell>>(){}.getType());
                    if(parsed!=null && parsed.size()<=1_000_000) loaded=parsed.stream().filter(Objects::nonNull).filter(Cell::valid).toList();
                }
            } catch(Exception e) { LiveEvents.LOGGER.warn("Could not read meteor cooling recovery",e); }
            List<Cell> snapshot=loaded;
            services.server().execute(()->{pending.addAll(snapshot);loading=false;tick();});
        });
    }

    public void adopt(EventInstance instance) {
        for(MeteorAftermath thermal:instance.thermal) pending.addAll(thermal.snapshot());
        instance.thermal.clear();
        tick();
    }
    public void cancelJournal(String id) {
        pending.removeIf(cell->id.equals(cell.journal()));
        WorldJournal.Journal journal=journals.remove(id);if(journal!=null)services.journal().close(journal);
    }
    public void resetScheduling() { scheduled=false; }

    public void tick() {
        if(loading || failed || scheduled || pending.isEmpty()) return;
        scheduled=true;
        services.scheduler().submit("meteor-cooling-recovery",WorkScheduler.Priority.CLEANUP,allowance->{
            int changed=0,scanned=0;
            Cell active=null;
            try {
                while(!pending.isEmpty() && changed<allowance && scanned++<64) {
                    Cell cell=pending.removeFirst();active=cell;
                    if(cell.journal()!=null && services.journal().find(cell.journal()).map(meta->meta.reverted).orElse(false)) continue;
                    var level=services.server().getLevel(ResourceKey.create(Registries.DIMENSION,Identifier.parse(cell.dimension())));
                    BlockPos pos=BlockPos.of(cell.pos());
                    if(level==null) continue;
                    if(!level.isLoaded(pos)) { pending.addLast(cell);continue; }
                    var state=level.getBlockState(pos);
                    if(cell.fire()?!state.is(Blocks.FIRE):!state.is(Blocks.MAGMA_BLOCK)) continue;
                    if(!level.getWorldBorder().isWithinBounds(pos) || !BlockChanger.canChange(level,pos,state)) continue;
                    var cooled=cell.fire()?Blocks.AIR.defaultBlockState():Blocks.OBSIDIAN.defaultBlockState();
                    LiveEventInfo info=info(cell,Vec3.atCenterOf(pos));
                    if(!LiveEventCallbacks.BEFORE_BLOCK_CHANGE.invoker().allowBlockChange(info,level,pos,state,cooled)) continue;
                    if(level.setBlock(pos,cooled,Block.UPDATE_ALL,512)) {
                        changed++;
                        WorldJournal.Journal journal=cell.journal()==null?null:journals.computeIfAbsent(cell.journal(),id->services.journal().resume(id).orElse(null));
                        if(journal!=null) journal.record(cell.dimension(),cell.pos(),BlockStateParser.serialize(state),BlockStateParser.serialize(cooled));
                    }
                }
                if(pending.isEmpty()) {
                    scheduled=false;journals.values().forEach(j->services.journal().close(j));journals.clear();
                    return WorkScheduler.Result.done(changed);
                }
                return WorkScheduler.Result.more(changed);
            } catch(RuntimeException e) {
                if(active!=null)pending.addFirst(active);
                failed=true;scheduled=false;LiveEvents.LOGGER.error("Meteor cooling recovery disabled; pending cells remain persisted",e);
                return WorkScheduler.Result.done(changed);
            }
        });
    }

    public void save(Collection<EventInstance> events) {
        if(loading) return;
        List<Cell> snapshot=new ArrayList<>(pending);
        for(EventInstance instance:events) for(MeteorAftermath thermal:instance.thermal) snapshot.addAll(thermal.snapshot());
        if(snapshot.isEmpty() && lastEmpty) return;
        lastEmpty=snapshot.isEmpty();
        services.io().execute(()->{
            try { AtomicFiles.writeString(file,GSON.toJson(snapshot)); }
            catch(Exception e) { LiveEvents.LOGGER.warn("Could not persist meteor cooling recovery",e); }
        });
    }

    private static LiveEventInfo info(Cell cell,Vec3 center) {
        return new LiveEventInfo() {
            public int instanceId(){return cell.instance();}
            public String definitionId(){return cell.event();}
            public Identifier dimension(){return Identifier.parse(cell.dimension());}
            public Vec3 center(){return center;}
            public long seed(){return 0;}
            public String profile(){return "safe";}
            public double elapsedSeconds(){return 0;}
            public double durationSeconds(){return 0;}
            public Optional<String> currentPhase(){return Optional.empty();}
            public boolean paused(){return false;}
            public boolean preview(){return false;}
        };
    }
}
