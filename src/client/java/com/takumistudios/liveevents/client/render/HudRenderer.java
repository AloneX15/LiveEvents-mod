package com.takumistudios.liveevents.client.render;

import com.takumistudios.liveevents.client.visual.CameraShake;
import com.takumistudios.liveevents.client.visual.FrameState;
import com.takumistudios.liveevents.client.visual.FrameState.SkyObject;
import com.takumistudios.liveevents.client.visual.FrameState.WorldLine;
import com.takumistudios.liveevents.client.visual.VisualDirector;
import net.minecraft.client.Camera;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.resources.Identifier;
import net.minecraft.world.phys.Vec3;

/**
 * Backend de render por HUD (§7.8): proyecta los objetos del cielo y las líneas del mundo sobre la pantalla y dibuja
 * las capas de color. Solo usa {@code fill}, {@code fillGradient} y {@code blit}, estables en todas las 26.x, y funciona
 * igual con Sodium e Iris (es el modo alternativo de §7.5/§7.9). Se dibuja antes que el HUD vanilla, así que la vida,
 * la barra rápida y el chat quedan siempre por encima.
 */
public final class HudRenderer {
    private static final Identifier[] TEXTURES = new Identifier[FrameState.Sprite.values().length];
    private static final int[] TEX_W = {128, 64, 128, 256, 256, 256, 128, 64, 0};
    private static final int[] TEX_H = {128, 64, 128, 32, 256, 256, 256, 256, 0};
    private static final Identifier VIGNETTE = tex("vignette");
    private static final Identifier LENS_DIRT = tex("lens_dirt");

    static {
        String[] names = {"glow", "core", "ring", "flare", "cloud", "filaments", "aurora", "beam", null};
        for (int i = 0; i < names.length; i++) {
            TEXTURES[i] = names[i] == null ? null : tex(names[i]);
        }
    }

    // Base de la cámara para este fotograma (sin objetos nuevos)
    private double fx, fy, fz, rx, rz, ux, uy, uz;
    private double tanY, tanX;
    private int width, height;
    private double px, py, depth;

    private static Identifier tex(String name) {
        return Identifier.fromNamespaceAndPath("liveevents", "textures/effect/" + name + ".png");
    }

    public void render(GuiGraphicsExtractor g, DeltaTracker delta) {
        FrameState f = VisualDirector.get().frame();
        Camera camera = CameraShake.camera();
        Minecraft mc = Minecraft.getInstance();
        if (camera == null || mc.level == null) {
            return;
        }
        width = g.guiWidth();
        height = g.guiHeight();
        setupCamera(camera, mc.options.fov().get());
        double horizonY = horizonScreenY(camera.xRot());
        int hy = (int) Math.max(0, Math.min(height, horizonY));

        if (f.night > 0) {
            fill(g, 0, 0, width, hy, 0x050814, f.night * 0.72f);
            fill(g, 0, hy, width, height, 0x050814, f.night * 0.45f);
        }
        if (f.dayA > 0) {
            fill(g, 0, 0, width, height, f.day, f.dayA * 0.35f);
        }
        if (f.skyTintA > 0 && hy > 0) {
            g.fillGradient(0, 0, width, hy, argb(f.skyTint, f.skyTintA * 0.7f), argb(f.skyTint, f.skyTintA * 0.45f));
            fill(g, 0, hy, width, height, f.skyTint, f.skyTintA * 0.12f);
        }
        if (f.horizonA > 0) {
            int band = Math.max(8, height / 6);
            g.fillGradient(0, hy - band, width, hy, argb(f.horizon, 0), argb(f.horizon, f.horizonA * 0.6f));
            g.fillGradient(0, hy, width, hy + band / 2, argb(f.horizon, f.horizonA * 0.35f), argb(f.horizon, 0));
        }
        if (f.blackout > 0 && hy > 0) {
            fill(g, 0, 0, width, hy, 0x000000, f.blackout * 0.95f);
            fill(g, 0, hy, width, height, 0x000000, f.blackout * 0.4f);
        }
        for (int i = 0; i < f.skyCount; i++) {
            drawSky(g, f.sky[i], f.exposure);
        }
        Vec3 cam = camera.position();
        for (int i = 0; i < f.lineCount; i++) {
            drawLine(g, f.lines[i], cam);
        }
        if (f.lightA > 0) {
            fill(g, 0, 0, width, height, f.light, f.lightA * 0.55f);
        }
        if (f.tintA > 0) {
            fill(g, 0, 0, width, height, f.tintColor, f.tintA);
        }
        if (f.bloom > 0.05f) {
            fill(g, 0, 0, width, height, 0xFFFFFF, Math.min(0.12f, f.bloom * 0.04f) * f.exposure);
        }
        if (f.whiteoutA > 0) {
            fill(g, 0, 0, width, height, f.whiteout, f.whiteoutA);
        }
        if (f.flashA > 0) {
            fill(g, 0, 0, width, height, f.flash, f.flashA);
        }
        if (f.glitch > 0.01f) {
            drawGlitch(g, f.glitch, f.glitchSlices);
        }
        if (f.vignette > 0) {
            g.blit(RenderPipelines.GUI_TEXTURED, VIGNETTE, 0, 0, 0f, 0f, width, height, 256, 256, 256, 256, argb(f.vignetteColor, f.vignette));
        }
        if (f.lensDirt > 0.02f) {
            g.blit(RenderPipelines.GUI_TEXTURED, LENS_DIRT, 0, 0, 0f, 0f, width, height, 256, 256, 256, 256, argb(0xFFF6E0, f.lensDirt * 0.6f));
        }
    }

    // ------------------------------------------------------------------------------------------------ proyección

    private void setupCamera(Camera camera, int fovDegrees) {
        double yaw = Math.toRadians(camera.yRot()), pitch = Math.toRadians(camera.xRot());
        fx = -Math.sin(yaw) * Math.cos(pitch);
        fy = -Math.sin(pitch);
        fz = Math.cos(yaw) * Math.cos(pitch);
        rx = -Math.cos(yaw);
        rz = -Math.sin(yaw);
        // u = r × f (r tiene y = 0)
        ux = 0 * fz - rz * fy;
        uy = rz * fx - rx * fz;
        uz = rx * fy - 0 * fx;
        tanY = Math.tan(Math.toRadians(Math.max(30, Math.min(110, fovDegrees))) / 2);
        tanX = tanY * width / Math.max(1.0, height);
    }

    /** Proyecta una dirección o desplazamiento relativo a la cámara. false si está detrás. */
    private boolean project(double dx, double dy, double dz) {
        double z = dx * fx + dy * fy + dz * fz;
        if (z <= 0.05) {
            return false;
        }
        double x = (dx * rx + dz * rz) / z;
        double y = (dx * ux + dy * uy + dz * uz) / z;
        px = width / 2.0 + x / tanX * width / 2.0;
        py = height / 2.0 - y / tanY * height / 2.0;
        depth = z;
        return true;
    }

    private double horizonScreenY(float xRotDegrees) {
        double pitch = Math.toRadians(xRotDegrees);
        return height / 2.0 - Math.tan(pitch) / tanY * height / 2.0;
    }

    private double pixelsPerDegree() {
        return height / 2.0 / tanY * Math.PI / 180;
    }

    private void drawSky(GuiGraphicsExtractor g, SkyObject o, float exposure) {
        double az = Math.toRadians(o.azimuth), alt = Math.toRadians(o.altitude);
        double dx = Math.sin(az) * Math.cos(alt), dy = Math.sin(alt), dz = -Math.cos(az) * Math.cos(alt);
        if (!project(dx, dy, dz)) {
            return;
        }
        double ppd = pixelsPerDegree();
        int w = (int) Math.max(1, Math.min(width * 4.0, o.widthDeg * ppd));
        int h = (int) Math.max(1, Math.min(height * 4.0, o.heightDeg * ppd));
        if (px + w < -width || px - w > width * 2 || py + h < -height || py - h > height * 2) {
            return; // fuera de pantalla
        }
        float alpha = o.alpha * (0.35f + 0.65f * exposure);
        int color = argb(o.color, alpha);
        var pose = g.pose();
        pose.pushMatrix();
        pose.translate((float) px, (float) py);
        if (o.rotation != 0) {
            pose.rotate((float) Math.toRadians(o.rotation));
        }
        if (o.sprite == FrameState.Sprite.LINE) {
            g.fill(-w / 2, -Math.max(1, h / 2), w / 2, Math.max(1, h / 2), color);
        } else {
            int s = o.sprite.ordinal();
            int ox = o.sprite == FrameState.Sprite.BEAM ? -w / 2 : -w / 2;
            int oy = o.sprite == FrameState.Sprite.BEAM ? 0 : -h / 2; // los haces salen del centro
            g.blit(RenderPipelines.GUI_TEXTURED, TEXTURES[s], ox, oy, 0f, 0f, w, h, TEX_W[s], TEX_H[s], TEX_W[s], TEX_H[s], color);
        }
        pose.popMatrix();
    }

    private boolean projectSky(double azDeg, double altDeg) {
        double az = Math.toRadians(azDeg), alt = Math.toRadians(altDeg);
        return project(Math.sin(az) * Math.cos(alt), Math.sin(alt), -Math.cos(az) * Math.cos(alt));
    }

    private void drawLine(GuiGraphicsExtractor g, WorldLine l, Vec3 cam) {
        boolean ok = l.sky ? projectSky(l.x0, l.y0) : project(l.x0 - cam.x, l.y0 - cam.y, l.z0 - cam.z);
        if (!ok) {
            return;
        }
        double ax = px, ay = py, ad = l.sky ? 24 : depth;
        ok = l.sky ? projectSky(l.x1, l.y1) : project(l.x1 - cam.x, l.y1 - cam.y, l.z1 - cam.z);
        if (!ok) {
            return;
        }
        double bx = px, by = py;
        double len = Math.hypot(bx - ax, by - ay);
        if (len < 0.5 || len > width * 3) {
            return;
        }
        float thickness = (float) Math.max(1, Math.min(12, l.widthPx * 24 / Math.max(4, ad)));
        var pose = g.pose();
        pose.pushMatrix();
        pose.translate((float) ax, (float) ay);
        pose.rotate((float) Math.atan2(by - ay, bx - ax));
        g.fill(0, (int) -Math.ceil(thickness / 2), (int) Math.ceil(len), (int) Math.ceil(thickness / 2), argb(l.color, l.alpha));
        pose.popMatrix();
    }

    private void drawGlitch(GuiGraphicsExtractor g, float strength, boolean slices) {
        long time = System.nanoTime() / 40_000_000L; // cambia ~25 veces/s, pero cubre poca pantalla
        int offset = (int) (strength * 6) + 1;
        // Separación RGB: bordes rojo y cian desplazados
        fill(g, 0, 0, offset, height, 0xFF0040, 0.25f * strength);
        fill(g, width - offset, 0, width, height, 0x00FFFF, 0.25f * strength);
        if (slices) {
            for (int i = 0; i < 6; i++) {
                long h = (time * 31 + i * 977) * 2654435761L;
                int y = (int) Math.floorMod(h, (long) Math.max(1, height));
                int sh = 2 + (int) Math.floorMod(h >> 8, 10L);
                int dx = (int) Math.floorMod(h >> 16, (long) (offset * 4 + 1)) - offset * 2;
                fill(g, Math.max(0, dx), y, width + Math.min(0, dx), y + sh, (i % 2 == 0) ? 0xFF2050 : 0x20FFFF, 0.18f * strength);
            }
        }
        for (int y = (int) (time % 3); y < height; y += 3) { // líneas de barrido
            fill(g, 0, y, width, y + 1, 0x000000, 0.08f * strength);
        }
    }

    private static void fill(GuiGraphicsExtractor g, int x0, int y0, int x1, int y1, int rgb, float alpha) {
        if (alpha > 0.003f && x1 > x0 && y1 > y0) {
            g.fill(x0, y0, x1, y1, argb(rgb, alpha));
        }
    }

    private static int argb(int rgb, float alpha) {
        int a = (int) (Math.max(0, Math.min(1, alpha)) * 255);
        return (a << 24) | (rgb & 0xFFFFFF);
    }
}
