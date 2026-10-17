package com.takumistudios.liveevents.core;

import com.takumistudios.liveevents.core.plan.*;
import com.takumistudios.liveevents.core.random.DeterministicRandom;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class MeteorEffectsTest {
    @Test void categoriesFollowDistributionAndIncludeEverySize() {
        int[] counts = new int[5];
        double[] sizes = {0.5,0.8,1,1.5,2};
        var random = new DeterministicRandom(2107);
        for(int i=0;i<100000;i++) {
            double size=MeteorEffects.sizeMultiplier(random.nextDouble());
            for(int j=0;j<sizes.length;j++) if(size==sizes[j]) counts[j]++;
        }
        double[] expected={0.35,0.30,0.20,0.12,0.03};
        for(int j=0;j<counts.length;j++) assertEquals(expected[j],counts[j]/100000.0,0.006);
        assertEquals(0.8,MeteorEffects.sizeMultiplier(0.35));
        assertEquals(1,MeteorEffects.sizeMultiplier(0.65));
        assertEquals(1.5,MeteorEffects.sizeMultiplier(0.85));
        assertEquals(2,MeteorEffects.sizeMultiplier(0.97));
    }
    @Test void plansAreRepeatableBoundedAndPreserveFirstLargeImpact() {
        var plan=MeteorPlan.generate(2107,400,128,0,1200,5,5,true);
        assertEquals(plan,MeteorPlan.generate(2107,400,128,0,1200,5,5,true));
        assertTrue(plan.getFirst().big());
        assertEquals(7,plan.getFirst().craterRadius());
        assertEquals(5,plan.stream().skip(1).filter(m->m.craterRadius()<100).map(MeteorPlan.Impact::craterRadius).distinct().count());
        assertTrue(MeteorPlan.generate(7,400,128,0,1200,40,100,true).stream()
                .allMatch(m->m.craterRadius()>=0.5 && m.craterRadius()<=100));
    }
    @Test void bodySmokeAndAudioTrackPhysicalRadius() {
        assertTrue(MeteorEffects.bodyRadius(2)<MeteorEffects.bodyRadius(10));
        assertTrue(MeteorEffects.scale(2)<MeteorEffects.scale(10));
        assertTrue(MeteorEffects.smokeSeconds(120,2)<MeteorEffects.smokeSeconds(120,10));
        assertTrue(MeteorEffects.pitch(2,7)>MeteorEffects.pitch(10,7));
        assertEquals(0,MeteorEffects.smokeSeconds(0,48));
        assertEquals(600,MeteorEffects.smokeSeconds(600,48));
        for(int radius=1;radius<=48;radius++) {
            assertTrue(MeteorEffects.pitch(radius,7)>=0.62);
            assertTrue(MeteorEffects.pitch(radius,7)<=1.2);
        }
    }
    @Test void flashHasSmoothAttackAndReleaseIndependentOfFrameRate() {
        assertEquals(0,MeteorEffects.flash(-1));
        assertEquals(0,MeteorEffects.flash(0));
        assertTrue(MeteorEffects.flash(1)>0);
        assertTrue(MeteorEffects.flash(3)>MeteorEffects.flash(6));
        assertTrue(MeteorEffects.flash(6)>MeteorEffects.flash(9));
        assertEquals(0,MeteorEffects.flash(12));
        double a=0,b=0;
        for(double t=0;t<12;t+=1.0/3) a+=MeteorEffects.flash(t)/3;
        for(double t=0;t<12;t+=1.0/12) b+=MeteorEffects.flash(t)/12;
        assertEquals(a,b,0.02);
    }
}
