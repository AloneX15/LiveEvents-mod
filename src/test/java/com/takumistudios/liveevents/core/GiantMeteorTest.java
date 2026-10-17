package com.takumistudios.liveevents.core;
import com.takumistudios.liveevents.core.plan.*;
import com.takumistudios.liveevents.network.Payloads;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class GiantMeteorTest {
    @Test void giantIsOnePercentAndFirstLargeIsPreserved() {
        int giants=0;
        for(int i=0;i<100000;i++) if(MeteorEffects.giantRoll(2107,i)) giants++;
        assertEquals(0.01,giants/100000.0,0.001);
        var a=MeteorPlan.generate(2107,400,128,0,1200,5,5,true);
        assertEquals(a,MeteorPlan.generate(2107,400,128,0,1200,5,5,true));
        assertEquals(7,a.getFirst().craterRadius());
        assertTrue(a.stream().anyMatch(m->m.craterRadius()==100));
    }
    @Test void giantHasIndependentBodyAndBoundedCrater() {
        var m=new AnnouncedMeteor(1,2,60,0,64,0,100,1,35,45,true,"earth",2107);
        assertTrue(m.valid());assertTrue(m.giant());assertEquals(30,m.bodyRadius());
        assertEquals(3,Payloads.PROTOCOL);
        var crater=CraterShape.generate(100,0.35,2107,0,0);
        assertTrue(crater.size()>25000 && crater.size()<32000);
        assertEquals(35,crater.stream().filter(c->c.dx()==0 && c.dz()==0).findFirst().orElseThrow().depth());
        assertTrue(crater.stream().allMatch(c->Math.hypot(c.dx(),c.dz())<=100 && c.depth()<=35));
        assertEquals(180,MeteorEffects.smokeSeconds(600,100));
        assertFalse(new AnnouncedMeteor(1,2,60,0,64,0,100,1,35,45,true,"earth",2107,1,0,0,0,0,31).valid());
        assertEquals(80,new AnnouncedMeteor(1,2,60,0,64,0,100,1,35,45,true,"earth",2107,1,0,0,0,0).lifetimeTicks());
    }
    @Test void waveAndFlashUseDistanceAndRecoverCompletely() {
        assertEquals(80,MeteorEffects.waveRadius(20));assertEquals(300,MeteorEffects.waveRadius(100));
        assertEquals(1,MeteorEffects.waveFalloff(0));assertEquals(0,MeteorEffects.waveFalloff(300));
        assertEquals(40,MeteorEffects.giantFlashDuration(30));assertEquals(5,MeteorEffects.giantFlashDuration(300));
        assertTrue(MeteorEffects.giantFlash(2,30)>MeteorEffects.giantFlash(2,100));
        assertTrue(MeteorEffects.giantFlash(2,100)>MeteorEffects.giantFlash(2,200));
        assertEquals(0,MeteorEffects.giantFlash(2,300));assertEquals(0,MeteorEffects.giantFlash(40,30));
    }
}
