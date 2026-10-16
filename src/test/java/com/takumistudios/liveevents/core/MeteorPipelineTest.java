package com.takumistudios.liveevents.core;
import com.takumistudios.liveevents.core.plan.*;
import com.takumistudios.liveevents.core.command.StartOptions;
import org.junit.jupiter.api.Test;
import java.io.StringReader;
import static org.junit.jupiter.api.Assertions.*;

class MeteorPipelineTest {
    @Test void announcementsRejectMalformedNetworkValues() {
        var valid=new AnnouncedMeteor(1,2,60,0,64,0,5,0.7,35,45,true,"earth",2107);
        assertTrue(valid.valid());
        assertFalse(new AnnouncedMeteor(1,2,60,Double.NaN,64,0,5,0.7,35,45,true,"earth",2107).valid());
        assertFalse(new AnnouncedMeteor(1,2,60,0,64,0,49,0.7,35,45,true,"earth",2107).valid());
        assertFalse(new AnnouncedMeteor(1,2,60,0,64,0,5,0.7,35,45,true,null,2107).valid());
    }
    @Test void craterPreparationIsDeterministicAndBounded() {
        var a=CraterShape.generate(7,0.55,2107,10,-30);
        assertEquals(a,CraterShape.generate(7,0.55,2107,10,-30));
        assertTrue(a.stream().anyMatch(c->c.dx()==0&&c.dz()==0&&c.depth()>=3&&c.core()));
        assertTrue(a.stream().anyMatch(CraterShape.Column::rim));
        assertTrue(CraterShape.generate(100000,100000,2107,0,0).size()<10000);
        assertThrows(IllegalArgumentException.class,()->CraterShape.generate(Double.NaN,1,0,0,0));
    }
    @Test void meshImporterRejectsWrongCoordinatesAndIndices() {
        String base="{\"format\":\"liveevents.mesh.v1\",\"coordinates\":\"Minecraft Y-up\",\"vertices\":[[0,1,0],[1,0,0],[0,0,1]],\"triangles\":[[0,1,2]]}";
        assertEquals(1,MeteorMesh.read(new StringReader(base)).triangles().length);
        assertThrows(IllegalArgumentException.class,()->MeteorMesh.read(new StringReader(base.replace("[0,1,2]","[0,1,3]"))));
        assertThrows(IllegalArgumentException.class,()->MeteorMesh.read(new StringReader(base.replace("Minecraft Y-up","Blender Z-up"))));
    }
    @Test void extendingDurationKeepsActivityUntilTheClosingTail() {
        assertEquals(1200,MeteorTimeline.phaseTime(1200,3600));
        assertTrue(MeteorTimeline.phaseTime(3000,3600)<1300);
        assertEquals(1300,MeteorTimeline.phaseTime(3100,3600));
    }
    @Test void brutalityHasDistinctCalmNominalAndMaximumPressure() {
        assertEquals(0,MeteorIntensity.retain(0));
        assertEquals(1,MeteorIntensity.retain(0.5));assertEquals(0,MeteorIntensity.extra(0.5));
        assertEquals(1,MeteorIntensity.extra(1));
    }
    @Test void launchControlsAreValidated() {
        assertEquals(120,StartOptions.parse("power=0.7 brutality=0.5 duration=120s").params().get("duration"));
        assertThrows(IllegalArgumentException.class,()->StartOptions.parse("power=2"));
        assertThrows(IllegalArgumentException.class,()->StartOptions.parse("brutality=NaN"));
    }
}
