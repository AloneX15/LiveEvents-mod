package com.takumistudios.liveevents.core.plan;
import com.takumistudios.liveevents.core.definition.ModuleSpec;
/** Stretch the activity and move the 25-second closing segment without restarting the timeline. */
public final class MeteorTimeline {
    private MeteorTimeline() { }
    public static ModuleSpec visual(ModuleSpec spec,double durationTicks) {
        double delta=durationTicks-1800,from=spec.fromTicks(),to=spec.toTicks();
        if(spec.phaseId().equals("smoking_scars")) {from=Math.max(0,from+delta);to=Math.max(from+1,to+delta);}
        else if(spec.phaseId().equals("judgement")) to=Math.max(from+1,to+delta);
        else if(to>=1800) to=Math.max(from+1,to+delta);
        return new ModuleSpec(spec.type(),spec.side(),spec.phaseId(),spec.index(),from,to,spec.raw());
    }
    public static double phaseTime(double actualTicks,double durationTicks) {
        double closing=Math.max(0,durationTicks-500);
        if(actualTicks>=closing) return 1300+actualTicks-closing;
        return actualTicks>=500?Math.min(1299,actualTicks):actualTicks;
    }
}
