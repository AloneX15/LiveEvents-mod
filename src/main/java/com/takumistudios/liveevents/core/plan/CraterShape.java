package com.takumistudios.liveevents.core.plan;
import com.takumistudios.liveevents.core.random.DeterministicRandom;
import java.util.*;
/** Pure geometry preparation: no access to a world or chunk from the worker. */
public final class CraterShape {
    public record Column(int dx,int dz,int depth,boolean rim,boolean core) { }
    private CraterShape() { }
    public static List<Column> generate(double radius,double depthFactor,long seed,int cx,int cz) {
        if(!Double.isFinite(radius)||!Double.isFinite(depthFactor)) throw new IllegalArgumentException("Non-finite crater");
        radius=Math.max(1,Math.min(48,radius));
        int r=(int)Math.ceil(radius);
        double depth=Math.max(1,radius*Math.max(0.1,Math.min(2,depthFactor)));
        List<Column> out=new ArrayList<>();
        for(int dx=-r-1;dx<=r+1;dx++) for(int dz=-r-1;dz<=r+1;dz++) {
            double d=Math.hypot(dx,dz),edge=radius*(1+0.18*DeterministicRandom.noise2(seed,(cx+dx)*0.35,(cz+dz)*0.35));
            if(d<=edge) out.add(new Column(dx,dz,(int)Math.round(depth*(1-d*d/(edge*edge))),false,d<edge*0.5));
            else if(d<=edge+1.5) out.add(new Column(dx,dz,0,true,false));
        }
        return List.copyOf(out);
    }
}
