package com.takumistudios.liveevents.client.visual;

import com.takumistudios.liveevents.core.random.DeterministicRandom;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.world.phys.Vec3;

/**
 * Temblor de cámara con ruido suave (§7.6). El mixin de {@link Camera} lo suma como desplazamiento a la rotación ya
 * calculada, así convive con otros mods de cámara. También guarda la cámara activa para proyectar el cielo.
 */
public final class CameraShake {
    private static volatile float amplitude;
    private static volatile float frequency;
    private static volatile int axis;
    private static Camera camera;

    private CameraShake() {
    }

    static void set(float amp, float freq, int ax) {
        amplitude = amp;
        frequency = freq;
        axis = ax;
    }

    /** Lo llama el mixin con la cámara principal en cada actualización. */
    public static void capture(Camera cam) {
        camera = cam;
    }

    public static Camera camera() {
        return camera;
    }

    public static Vec3 cameraPosition() {
        if (camera != null) {
            return camera.position();
        }
        Minecraft mc = Minecraft.getInstance();
        return mc.player != null ? mc.player.getEyePosition() : Vec3.ZERO;
    }

    /** Desplazamiento {yaw, pitch} en grados para este instante, o null si no hay temblor. */
    public static float[] offset(float[] out) {
        float amp = amplitude;
        if (amp <= 0.001f) {
            return null;
        }
        double t = System.nanoTime() / 1e9 * Math.max(0.1, frequency);
        float yaw = (float) (DeterministicRandom.noise1(11, t) * amp);
        float pitch = (float) (DeterministicRandom.noise1(23, t * 1.1) * amp);
        out[0] = axis == 1 ? 0 : yaw;
        out[1] = axis == 2 ? pitch * 0.3f : pitch;
        return out;
    }
}
