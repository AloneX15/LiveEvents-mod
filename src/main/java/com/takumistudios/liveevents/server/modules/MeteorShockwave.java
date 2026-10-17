package com.takumistudios.liveevents.server.modules;

import com.takumistudios.liveevents.api.module.ServerModuleContext;
import com.takumistudios.liveevents.core.plan.AnnouncedMeteor;
import com.takumistudios.liveevents.core.plan.MeteorEffects;
import com.takumistudios.liveevents.core.scheduler.WorkScheduler;
import com.takumistudios.liveevents.content.LEContent;
import com.takumistudios.liveevents.server.instance.EventInstance;
import com.takumistudios.liveevents.server.integration.ProtectionCompat;
import com.takumistudios.liveevents.server.journal.BlockChanger;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import java.util.*;

/** Bounded gameplay slices, using the event clock and the same propagation as the client. */
public final class MeteorShockwave {
    private MeteorShockwave() { }
    public static void launch(ServerModuleContext ctx, AnnouncedMeteor meteor) {
        if (ctx.event().preview() || meteor.power()<=0) return;
        EventInstance event=(EventInstance)ctx.event();
        Vec3 origin=new Vec3(meteor.x(),meteor.y(),meteor.z());
        ctx.submit(WorkScheduler.Priority.GAMEPLAY,new WorkScheduler.Task() {
            private final Set<UUID> hit=new HashSet<>();
            private List<LivingEntity> candidates=List.of();
            private int next;
            private double refreshed=-100, scanned=-1;
            @Override public WorkScheduler.Result run(int allowance) {
                double age=event.ticksIncludingAftermath()-meteor.tick();
                if (age<0) return WorkScheduler.Result.more(0);
                if (age>80) return WorkScheduler.Result.DONE;
                if (next>=candidates.size()) {
                    if(age==scanned) return WorkScheduler.Result.more(0);
                    if(age-refreshed>=5) {
                        candidates=ctx.level().getEntitiesOfClass(LivingEntity.class,new AABB(origin,origin).inflate(300),e->!hit.contains(e.getUUID()));
                        refreshed=age;
                    }
                    next=0;scanned=age;
                }
                double radius=MeteorEffects.waveRadius(age);
                for(int n=0;n<16 && next<candidates.size();n++) {
                    LivingEntity entity=candidates.get(next++);
                    double distance=entity.position().distanceTo(origin);
                    if(entity.isRemoved() || distance>radius || !hit.add(entity.getUUID())) continue;
                    if(!event.accepts(entity) || ProtectionCompat.protectedAt(ctx.level(),entity.blockPosition())
                            || BlockChanger.inExcludedRegion(ctx.level(),entity.blockPosition())) continue;
                    double falloff=MeteorEffects.waveFalloff(distance);
                    double profile=event.profile().equals("safe")?0.5:event.profile().equals("gameplay")?0.75:1;
                    ctx.damage(entity,LEContent.METEOR_IMPACT.identifier(),(float)(40*meteor.power()*falloff*profile));
                    Vec3 direction=entity.position().subtract(origin);
                    direction=direction.lengthSqr()<1e-4?new Vec3(0,1,0):direction.normalize();
                    double force=2.5*meteor.power()*falloff*profile*ctx.factorFor(entity);
                    GameModules.push(entity,direction.scale(force).add(0,force*0.25,0));
                }
                return WorkScheduler.Result.more(0);
            }
        });
    }
}
