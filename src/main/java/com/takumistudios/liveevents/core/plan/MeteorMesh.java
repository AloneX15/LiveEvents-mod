package com.takumistudios.liveevents.core.plan;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.Reader;

/** Explicit, bounded Blender-to-runtime contract. Positions are already Minecraft Y-up. */
public record MeteorMesh(float[][] vertices, int[][] triangles) {
    public static MeteorMesh read(Reader reader) {
        JsonObject object=JsonParser.parseReader(reader).getAsJsonObject();
        if (!object.get("format").getAsString().equals("liveevents.mesh.v1")
                || !object.get("coordinates").getAsString().equals("Minecraft Y-up")) throw new IllegalArgumentException("Unsupported meteor mesh");
        var points=object.getAsJsonArray("vertices"); var faces=object.getAsJsonArray("triangles");
        if(points.isEmpty()||points.size()>4096||faces.isEmpty()||faces.size()>8192) throw new IllegalArgumentException("Mesh budget exceeded");
        float[][] vertices=new float[points.size()][3]; int[][] triangles=new int[faces.size()][3];
        for(int i=0;i<points.size();i++) {
            var point=points.get(i).getAsJsonArray(); if(point.size()!=3) throw new IllegalArgumentException("Expected xyz");
            for(int j=0;j<3;j++) {
                float value=point.get(j).getAsFloat();
                if(!Float.isFinite(value)||Math.abs(value)>16) throw new IllegalArgumentException("Invalid mesh coordinate"); vertices[i][j]=value;
            }
        }
        for(int i=0;i<faces.size();i++) {
            var face=faces.get(i).getAsJsonArray(); if(face.size()!=3) throw new IllegalArgumentException("Expected triangles");
            for(int j=0;j<3;j++) {
                int value=face.get(j).getAsInt(); if(value<0||value>=vertices.length) throw new IllegalArgumentException("Invalid mesh index"); triangles[i][j]=value;
            }
        }
        return new MeteorMesh(vertices,triangles);
    }
}
