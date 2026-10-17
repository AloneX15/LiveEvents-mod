package com.takumistudios.liveevents.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.takumistudios.liveevents.LiveEvents;
import com.takumistudios.liveevents.client.state.ClientEventState;
import com.takumistudios.liveevents.client.visual.VisualDirector;
import com.takumistudios.liveevents.core.plan.AnnouncedMeteor;
import com.takumistudios.liveevents.core.plan.MeteorEffects;
import com.takumistudios.liveevents.core.random.DeterministicRandom;
import net.fabricmc.fabric.api.client.rendering.v1.RenderStateDataKey;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderEvents;
//? if >=26.2 {
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelExtractionEvents;
//?}
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.resources.Identifier;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;
import java.util.*;

/** World-space geometry, extracted independently of HUD visibility. No raw graphics-backend calls. */
public final class MeteorWorldRenderer {
    private static final Identifier ROCK = Identifier.fromNamespaceAndPath("liveevents", "textures/effect/meteor_rock.png");
    private static com.takumistudios.liveevents.core.plan.MeteorMesh mesh;
    private static double meshRadius=1;
    private static final Identifier CLOUD = Identifier.fromNamespaceAndPath("liveevents", "textures/effect/meteor_smoke.png");
    private static final Identifier GLOW = Identifier.fromNamespaceAndPath("liveevents", "textures/effect/glow.png");
    private static final RenderStateDataKey<List<Quad>> DATA = RenderStateDataKey.create(() -> "liveevents:meteors");
    private static boolean enabled = true;
    private static final int MAX_QUADS = 2048;
    private static long extractionNanos, extractionFrames;
    private static int peakQuads;
    public static void resetMetrics() { extractionNanos=0;extractionFrames=0;peakQuads=0; }
    public static double averageExtractionMs() { return extractionFrames==0?0:extractionNanos/1e6/extractionFrames; }
    public static int peakQuads() { return peakQuads; }
    private record Quad(Vec3 a, Vec3 b, Vec3 c, Vec3 d, int color, int layer, float u0,float v0,float u1,float v1) {
        Quad(Vec3 a,Vec3 b,Vec3 c,Vec3 d,int color,int layer) { this(a,b,c,d,color,layer,0,0,1,1); }
    }
    private MeteorWorldRenderer() { }

    /** Shared by all smoke LODs; reads terrain depth but never writes billboard depth. */
    public static net.minecraft.client.renderer.rendertype.RenderType smokeMaterial() {
        return RenderTypes.entityTranslucentEmissive(CLOUD);
    }

    public static void register() {
        try (var stream = MeteorWorldRenderer.class.getResourceAsStream("/assets/liveevents/meteor/meteor_mesh.json")) {
            if(stream == null) throw new java.io.IOException("Missing meteor mesh");
            mesh = com.takumistudios.liveevents.core.plan.MeteorMesh.read(new java.io.InputStreamReader(stream,java.nio.charset.StandardCharsets.UTF_8));
            meshRadius=0;
            for(float[] point:mesh.vertices()) meshRadius=Math.max(meshRadius,Math.sqrt(point[0]*point[0]+point[1]*point[1]+point[2]*point[2]));
            meshRadius=Math.max(0.001,meshRadius);
        } catch (java.io.IOException | RuntimeException e) { LiveEvents.LOGGER.warn("Meteor mesh unavailable; using incandescent cores",e); }
        //? if >=26.2 {
        LevelExtractionEvents.END_EXTRACTION.register(context -> {
        //?} else {
        /*LevelRenderEvents.END_EXTRACTION.register(context -> {
        *///?}
            try {
                VisualDirector.get().computeFrame();
                long start=System.nanoTime();
                List<Quad> quads=extract(context.levelState().cameraRenderState.pos,context.levelState().cameraRenderState.orientation);
                context.levelState().setData(DATA,quads);
                if(!quads.isEmpty()) { extractionNanos+=System.nanoTime()-start;extractionFrames++;peakQuads=Math.max(peakQuads,quads.size()); }
            } catch (RuntimeException e) {
                enabled = false;
                context.levelState().setData(DATA, List.of());
                LiveEvents.LOGGER.error("Meteor extraction disabled after failure", e);
            }
        });
        LevelRenderEvents.COLLECT_SUBMITS.register(context -> {
            if (!enabled) return;
            List<Quad> quads = context.levelState().getDataOrDefault(DATA, List.of());
            if (quads.isEmpty()) return;
            try {
                context.submitNodeCollector().submitCustomGeometry(context.poseStack(), RenderTypes.entityTranslucentEmissive(GLOW),
                        (pose, vertices) -> draw(quads, 0, pose, vertices));
                context.submitNodeCollector().submitCustomGeometry(context.poseStack(), RenderTypes.entityCutout(ROCK),
                        (pose, vertices) -> draw(quads, 2, pose, vertices));
                context.submitNodeCollector().submitCustomGeometry(context.poseStack(), smokeMaterial(),
                        (pose, vertices) -> draw(quads, 1, pose, vertices));
            } catch (RuntimeException e) {
                enabled = false; LiveEvents.LOGGER.error("Meteor rendering disabled after failure", e);
            }
        });
    }

    private static List<Quad> extract(Vec3 camera, org.joml.Quaternionf orientation) {
        if (!enabled || ClientEventState.get().panicked() || Minecraft.getInstance().level == null) return List.of();
        String dim = Minecraft.getInstance().level.dimension().identifier().toString();
        List<Quad> out = new ArrayList<>();
        Vector3f r = new Vector3f(1,0,0).rotate(orientation), u = new Vector3f(0,1,0).rotate(orientation);
        Vec3 right = new Vec3(r.x,r.y,r.z), up = new Vec3(u.x,u.y,u.z);
        long now = System.nanoTime();
        int detail = VisualDirector.get().quality().ordinal();
        var visibleInstances=ClientEventState.get().meteorInstances(now);
        int warningSegments=Math.max(4,Math.min(24,1800/Math.max(1,ClientEventState.get().instances().size()*24)));
        // Reserve warnings and a minimal trajectory for every announced hazard before decoration.
        for (var instance : visibleInstances) {
            if (!instance.dimension.equals(dim)) continue;
            double tick=instance.clock.advance(now);
            for(var m:instance.meteors.values()) {
                double remaining=m.tick()-tick;
                if(remaining<=0 || remaining>60 || out.size()>MAX_QUADS-32) continue;
                Vec3 target=new Vec3(m.x(),m.y(),m.z()).subtract(camera);
                if(remaining<=30 && target.lengthSqr()<256*256) ring(out,target.add(0,0.12,0),m.radius()+1,0.15,0xDCFF7A1A,warningSegments);
                double az=Math.toRadians(m.azimuth()),el=Math.toRadians(m.angle());
                Vec3 direction=new Vec3(Math.sin(az)*Math.cos(el),Math.sin(el),-Math.cos(az)*Math.cos(el));
                Vec3 head=target.add(direction.scale(remaining*5));
                ribbon(out,head,head.add(direction.scale(20)),right,0.2,0xFFFFF4D6);
            }
        }
        for (var instance : visibleInstances) {
            if (!instance.dimension.equals(dim)) continue;
            double tick = instance.clock.advance(now);
            if(instance.def.id().equals("liveevents:meteor_shower")) for(var original:instance.def.allModules()) {
                var spec=instance.visualSpec(original);
                if(!spec.shortType().equals("meteor_trail") || !spec.activeAt(tick)) continue;
                double rate=Math.max(0,Math.min(12,instance.params(spec).range("rate",spec.progress(tick),1)));
                if(rate<=0) continue;
                double seconds=tick/20,slot=1/rate;
                for(long k=(long)Math.floor((seconds-1.2)/slot);k<=(long)Math.floor(seconds/slot);k++) {
                    DeterministicRandom r0=new DeterministicRandom(DeterministicRandom.derive(instance.moduleSeed(spec),k));
                    double age=seconds-k*slot;
                    if(age<0 || age>1.2 || out.size()>MAX_QUADS-160) continue;
                    double az=r0.range(0,Math.PI*2);
                    Vec3 start=instance.center.add(Math.sin(az)*400,r0.range(160,260),Math.cos(az)*400).subtract(camera);
                    Vec3 direction=new Vec3(-Math.sin(az)*55,-45,-Math.cos(az)*55);
                    Vec3 head=start.add(direction.scale(age));
                    ribbon(out,head,head.subtract(direction.scale(0.25)),right,0.25,0xA0FF7A1A);
                }
            }
            for (AnnouncedMeteor m : instance.meteors.values()) {
                if (out.size() > MAX_QUADS - 160) break;
                double remaining = m.tick() - tick;
                double bodySize = m.bodyRadius();
                if (remaining > 60 || remaining < -Math.max(40,m.lifetimeTicks())) continue;
                Vec3 target = new Vec3(m.x(),m.y(),m.z());
                double distance = camera.distanceTo(target);
                if (distance > 1024) continue;
                Vec3 origin = target.subtract(camera);
                if (remaining > 0) {
                    double az = Math.toRadians(m.azimuth()), el = Math.toRadians(m.angle());
                    Vec3 direction = new Vec3(Math.sin(az)*Math.cos(el), Math.sin(el), -Math.cos(az)*Math.cos(el));
                    int fragments = !m.giant() && m.big() && remaining < 20 ? 3 : 1;
                    for (int fragment=0;fragment<fragments;fragment++) {
                        double split = fragments == 1 ? 0 : fragment-1;
                        Vec3 endpoint = origin.add(split*m.radius()*0.55,0,split*m.radius()*0.3);
                        Vec3 head = endpoint.add(direction.scale(remaining*5));
                        Vec3 tail = head.add(direction.scale(Math.min(80, (60-remaining)*4)));
                        ribbon(out, head, tail, right, bodySize*0.75, 0xCAFF7A1A);
                        ribbon(out, head, head.add(direction.scale(8)), right, bodySize*0.28, 0xFFFFF4D6);
                        puff(out, head, right, up, bodySize*1.2, 0xFFFFF4D6, false);
                        if(mesh != null && distance < (m.giant()?768:192) && out.size() < MAX_QUADS-400) body(out,head,bodySize/meshRadius,tick*(0.025+(m.seed()&15)*0.002)+(m.seed()&255), 1);
                        // One gradual green accent, not a repeating strobe.
                        if (remaining > 25 && remaining < 38) puff(out, head, right, up, bodySize*2, 0x457CFF6B, false);
                        if (detail > 0) for(int j=1;j<=5;j++) puff(out, head.add(direction.scale(j*6)), right, up, bodySize*(0.7+j*0.22), 0x38685D52, true);
                    }

                } else {
                    double visualRadius=m.giant()?100:m.radius()*(0.4+m.visualPower())/(0.4+m.power());
                    double seconds = -remaining/20, life = Math.min(1, seconds/2.8);
                    boolean residual=seconds>12;
                    double fade=Math.max(0,1-seconds/Math.max(1,m.smokeSeconds()))*(residual?0.25:1-0.75*seconds/12);
                    if(seconds>m.smokeSeconds()) fade=0;
                    boolean water = m.surface().equals("water"), snow = m.surface().equals("snow");
                    int color = water ? 0xB8DAEF : snow ? 0xEDF4FF : 0x827365;
                    if(m.giant() && seconds<=3.75) {
                        double wave=MeteorEffects.waveRadius(seconds*20);
                        int alpha=(int)(90*(1-seconds/3.75));
                        for(int lat=-2;lat<=2;lat++) {
                            double angle=lat*Math.PI/6;
                            ring(out,origin.add(0,wave*Math.sin(angle),0),wave*Math.cos(angle),1.5,(alpha<<24)|0xFFE3B0,24);
                        }
                        ring(out,origin.add(0,0.2,0),wave,4,(alpha<<24)|color,32);
                    }
                    if (!m.giant() && seconds < 2) ring(out, origin.add(0,0.12,0), (visualRadius+seconds*10), 0.3,
                            ((int)(120*(1-seconds/2))<<24)|(water?0xA7DFFF:color), 40);
                    DeterministicRandom random = new DeterministicRandom(m.seed());
                    // Use the same depth-read-only smoke material at every LOD. Baked billboard
                    // atlases previously introduced depth-writing rectangles over water and clouds.
                    int puffs = fade<=0 ? 0 : m.giant() ? (distance>384?20:detail<=1?28:48) : distance>192 ? 8 : residual ? (detail<=1?3:6) : detail<=1 ? 20 : 44;
                    double glow = seconds<1.2 ? Math.sin(Math.PI*seconds/1.2)
                            *com.takumistudios.liveevents.client.config.ClientConfig.get().max_flash_intensity : 0;
                    if (glow>0) {
                        Vec3 core = origin.add(0,visualRadius*0.2,0);
                        puff(out,core,right,up,Math.min(m.giant()?90:Double.MAX_VALUE,visualRadius*(0.6+seconds*3)),((int)(210*glow)<<24)|0xFFF4D6,false);
                        puff(out,core,right,up,Math.min(m.giant()?150:Double.MAX_VALUE,visualRadius*(1.2+seconds*6)),((int)(95*glow)<<24)|0xFF8A35,false);
                    }
                    for(int j=0;j<puffs;j++) {
                        boolean cap = j>=puffs/3;
                        double angle = random.range(0,Math.PI*2);
                        double radius = random.range(0.2,1) * visualRadius * (cap ? 1.5 : 0.35) * life;
                        double height = cap ? random.range(1.7,2.4) : random.range(0.1,1.8);
                        if (water) { radius*=0.65; height*=1.4; }
                        if (snow) height*=0.7;
                        Vec3 at = origin.add(Math.cos(angle)*radius+seconds*m.windX(), height*visualRadius*life+Math.min(seconds,12)*0.16, Math.sin(angle)*radius+seconds*m.windZ());
                        float size = (float)((0.4+random.nextDouble())*visualRadius*(cap?0.8:0.5)*Math.min(1,seconds*2));
                        double puffFade=fade;
                        if(residual) {
                            double age=(seconds+j*2.0)%12;
                            at=origin.add(Math.cos(angle)*visualRadius*0.2+age*m.windX(),age*0.7*Math.sqrt(MeteorEffects.scale(m.radius())),Math.sin(angle)*visualRadius*0.2+age*m.windZ());
                            size=(float)(visualRadius*(0.15+age*0.025));puffFade*=Math.sin(age/12*Math.PI);
                        }
                        if(m.giant()) {
                            double growth=Math.min(1,seconds/8);
                            double spread=(cap?78:28)*growth*random.range(0.3,1);
                            double y=(cap?132+random.range(-6,6):random.range(0,130))*growth;
                            at=origin.add(Math.cos(angle)*spread+seconds*m.windX(),y,Math.sin(angle)*spread+seconds*m.windZ());
                            size=(float)((cap?42:22)*growth);
                        }
                        Vec3 rotatedRight=right.scale(Math.cos(angle)).add(up.scale(Math.sin(angle)));
                        Vec3 rotatedUp=up.scale(Math.cos(angle)).subtract(right.scale(Math.sin(angle)));
                        puff(out,at,rotatedRight,rotatedUp,size, ((int)((m.giant()?170:95)*puffFade)<<24)|color,true);
                    }
                    if (seconds<2.5) for(int j=0;j<(detail<=1?8:20);j++) {
                        double angle=random.range(0,Math.PI*2), speed=random.range(3,12)*(0.5+m.power());
                        double h=Math.max(0,speed*0.8*seconds-4*seconds*seconds);
                        Vec3 at=origin.add(Math.cos(angle)*speed*seconds,h,Math.sin(angle)*speed*seconds);
                        puff(out,at,right,up,water?0.18:0.12,water?0xB0B8E5FF:snow?0xDDEEF6FF:0xDCFF6D20,false);
                    }
                    if (!water && !snow && seconds<m.coolingSeconds()) {
                        double cool=Math.max(0,1-seconds/Math.max(1,m.coolingSeconds()));
                        ring(out,origin.add(0,0.1,0),visualRadius*0.45,0.25,((int)(65*cool)<<24)|0xFF3B00,24);
                    }
                }
            }
        }
        // Approximate back-to-front ordering for transparent smoke clusters.
        out.sort(Comparator.comparingDouble((Quad q)->quadDistanceSquared(q)).reversed());
        return List.copyOf(out);
    }

    private static double quadDistanceSquared(Quad q) {
        double x=(q.a.x+q.c.x)*0.5,y=(q.a.y+q.c.y)*0.5,z=(q.a.z+q.c.z)*0.5;
        return x*x+y*y+z*z;
    }

    private static void ribbon(List<Quad> out,Vec3 a,Vec3 b,Vec3 right,double width,int color) {
        Vec3 w=right.scale(width), tip=right.scale(width*0.05);
        out.add(new Quad(a.subtract(w),a.add(w),b.add(tip),b.subtract(tip),color,0));
    }
    private static void puff(List<Quad> out,Vec3 center,Vec3 right,Vec3 up,double size,int color,boolean smoke) {
        if(out.size()>=MAX_QUADS) return;
        Vec3 r=right.scale(size),u=up.scale(size);
        out.add(new Quad(center.subtract(r).subtract(u),center.add(r).subtract(u),center.add(r).add(u),center.subtract(r).add(u),color,smoke?1:0));
    }
    private static void ring(List<Quad> out,Vec3 center,double radius,double width,int color,int segments) {
        for(int j=0;j<segments && out.size()<MAX_QUADS;j++) {
            double a=j*Math.PI*2/segments,b=(j+1)*Math.PI*2/segments;
            out.add(new Quad(center.add(Math.cos(a)*radius,0,Math.sin(a)*radius),center.add(Math.cos(a)*(radius+width),0,Math.sin(a)*(radius+width)),
                    center.add(Math.cos(b)*(radius+width),0,Math.sin(b)*(radius+width)),center.add(Math.cos(b)*radius,0,Math.sin(b)*radius),color,0));
        }
    }
    private static void body(List<Quad> out,Vec3 center,double scale,double spin,int stride) {
        float[][] points=mesh.vertices();
        for(int i=0;i<mesh.triangles().length && out.size()<MAX_QUADS-160;i+=stride) {
            int[] triangle=mesh.triangles()[i];
            Vec3 a=rotate(points[triangle[0]],center,scale,spin),b=rotate(points[triangle[1]],center,scale,spin),c=rotate(points[triangle[2]],center,scale,spin);
            out.add(new Quad(a,b,c,c,0xFFFFFFFF,2));
        }
    }
    private static Vec3 rotate(float[] point,Vec3 center,double scale,double spin) {
        return center.add((point[0]*Math.cos(spin)-point[2]*Math.sin(spin))*scale,point[1]*scale,(point[0]*Math.sin(spin)+point[2]*Math.cos(spin))*scale);
    }
    private static void draw(List<Quad> quads,int layer,PoseStack.Pose pose,VertexConsumer vertices) {
        for(Quad q:quads) if(q.layer==layer) {
            vertex(vertices,pose,q.a,q.u0,q.v1,q.color);vertex(vertices,pose,q.b,q.u1,q.v1,q.color);
            vertex(vertices,pose,q.c,q.u1,q.v0,q.color);vertex(vertices,pose,q.d,q.u0,q.v0,q.color);
        }
    }
    private static void vertex(VertexConsumer v,PoseStack.Pose pose,Vec3 p,float u,float w,int color) {
        v.addVertex(pose,(float)p.x,(float)p.y,(float)p.z).setColor(color).setUv(u,w)
                .setOverlay(net.minecraft.client.renderer.texture.OverlayTexture.NO_OVERLAY).setLight(0xF000F0).setNormal(pose,0,1,0);
    }
}
