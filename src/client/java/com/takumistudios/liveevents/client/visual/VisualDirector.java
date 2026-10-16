package com.takumistudios.liveevents.client.visual;

import com.takumistudios.liveevents.LiveEvents;
import com.takumistudios.liveevents.client.config.ClientConfig;
import com.takumistudios.liveevents.client.state.ClientEventState;
import com.takumistudios.liveevents.client.state.ClientInstance;
import com.takumistudios.liveevents.core.definition.ModuleSpec;
import com.takumistudios.liveevents.core.definition.Profile;
import com.takumistudios.liveevents.core.module.ModuleTypeRegistry;
import com.takumistudios.liveevents.core.safety.FlashLimiter;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayDeque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;

/**
 * Coordina los visuales de todos los eventos activos (§7): mezcla capas, aplica perfiles, accesibilidad, exposición
 * según el cielo, LOD, presupuesto de partículas y calidad automática. Un error en un visual lo desactiva solo a él.
 */
public final class VisualDirector {
    /** Velocidad del sonido (bloques/s) para el retraso por distancia. */
    public static final double SPEED_OF_SOUND = 340;
    private static final VisualDirector INSTANCE = new VisualDirector();
    private static final Map<String, ClientVisual> VISUALS = new HashMap<>();

    static {
        reg("sky_tint", new SkyVisuals.SkyTint());
        reg("sky_blackout", new SkyVisuals.Blackout());
        reg("time_override", new SkyVisuals.TimeOverride());
        reg("artificial_day", new SkyVisuals.ArtificialDay());
        reg("shadow_whip", new SkyVisuals.ShadowWhip());
        reg("celestial_body", new SkyVisuals.CelestialBody());
        reg("star_point", new SkyVisuals.StarPoint());
        reg("sky_shatter", new SkyVisuals.SkyShatter());
        reg("atmo_rings", new SkyVisuals.AtmoRings());
        reg("plasma_shell", new SkyVisuals.PlasmaShell());
        reg("aurora", new SkyVisuals.Aurora());
        reg("nebula", new SkyVisuals.Nebula());
        reg("pulsar", new SkyVisuals.Pulsar());
        reg("lens_flare", new SkyVisuals.LensFlare());
        reg("flash", new ScreenVisuals.Flash());
        reg("whiteout", new ScreenVisuals.Whiteout());
        reg("light_cut", new ScreenVisuals.LightCut());
        reg("chromatic_glitch", new ScreenVisuals.ChromaticGlitch());
        reg("bloom", new ScreenVisuals.Bloom());
        reg("screen_overlay", new ScreenVisuals.ScreenOverlay());
        reg("light_pulse", new ScreenVisuals.LightPulse());
        reg("camera_shake", new ScreenVisuals.CameraShake());
        reg("sound", new AudioVisuals.Sound());
        reg("sound_loop", new AudioVisuals.SoundLoop());
        reg("audio_duck", new AudioVisuals.AudioDuck());
        reg("shockwave", new WorldVisuals.Shockwave());
        reg("particle_emitter", new WorldVisuals.ParticleEmitter());
        reg("meteor_trail", new WorldVisuals.MeteorTrail());
        reg("ash_fall", new WorldVisuals.AshFall());
        reg("debris", new WorldVisuals.Debris());
        reg("meteor_impacts", new WorldVisuals.MeteorImpacts());
        reg("seismic_wave", new WorldVisuals.SeismicWave());
    }

    private static void reg(String id, ClientVisual visual) {
        VISUALS.put("liveevents:" + id, visual);
    }

    /** Registro desde addons (LiveEventsClientApi). */
    public static synchronized void register(String typeId, ClientVisual visual) {
        String id = ModuleTypeRegistry.normalize(typeId);
        if (VISUALS.containsKey(id)) {
            throw new IllegalArgumentException("Ya hay un visual para " + id);
        }
        VISUALS.put(id, visual);
    }

    public static VisualDirector get() {
        return INSTANCE;
    }

    private record PendingSound(long atNanos, Identifier id, float volume, float pitch, double x, double y, double z) {
    }

    private static final int[] NEBULA_DEFAULT = {0xFF0000, 0x00CED1, 0xC8B400};
    private final FrameState frame = new FrameState();
    private final VisualContext ctx = new VisualContext();
    private final FlashLimiter flashLimiter = new FlashLimiter();
    private final Set<String> failed = new HashSet<>();
    private final ArrayDeque<PendingSound> sounds = new ArrayDeque<>();
    private double exposure = 1;
    private double exposureTarget = 1;
    private int tickParticleBudget;
    private int spawnedThisTick;
    private ClientConfig.Quality quality;
    private int lowFpsSeconds;
    private int goodFpsSeconds;
    private long lastFpsCheck;
    private boolean ducked;
    private boolean noticeShown;

    private VisualDirector() {
        ctx.director = this;
    }

    public FrameState frame() {
        return frame;
    }

    public ClientConfig.Quality quality() {
        if (quality == null) {
            quality = ClientConfig.get().quality();
        }
        return quality;
    }

    public void resetQuality() {
        quality = ClientConfig.get().quality();
    }

    // ================================================================================================ fotograma

    /** Calcula el estado visual del fotograma. Se llama desde el render del HUD. */
    public FrameState computeFrame() {
        frame.reset();
        Minecraft mc = Minecraft.getInstance();
        ClientLevel level = mc.level;
        ClientEventState state = ClientEventState.get();
        if (level == null || state.panicked() || !state.active()) {
            CameraShake.set(0, 0, 0);
            return frame;
        }
        long now = System.nanoTime();
        exposure += (exposureTarget - exposure) * 0.08;
        frame.exposure = (float) exposure;
        Vec3 cam = CameraShake.cameraPosition();
        String dim = level.dimension().identifier().toString();
        for (ClientInstance i : state.instances()) {
            if (i.dimension.equals(dim)) {
                i.t = i.clock.advance(now);
                runFrame(i, level, cam, now, 1);
            }
        }
        Iterator<ClientInstance> it = state.ending().iterator();
        while (it.hasNext()) {
            ClientInstance i = it.next();
            double fade = i.fadeOut(now);
            if (fade <= 0) {
                it.remove();
                continue;
            }
            if (i.dimension.equals(dim)) {
                i.t = i.clock.advance(now);
                runFrame(i, level, cam, now, fade);
            }
        }
        if (ClientConfig.get().show_sky_scars) {
            drawScars(level);
        }
        applyAccessibility(mc);
        CameraShake.set(frame.shake, frame.shakeFreq, frame.shakeAxis);
        return frame;
    }

    private void runFrame(ClientInstance i, ClientLevel level, Vec3 cam, long now, double fade) {
        for (ModuleSpec original : i.def.allModules()) {
            ModuleSpec spec = i.visualSpec(original);
            if (!spec.side().onClient() || !(spec.activeAt(i.t) || spec.shortType().equals("meteor_impacts") && i.t >= spec.fromTicks() && i.t < i.vars.getOrDefault("duration",i.def.durationTicks()/20)*20) && fade >= 1) {
                continue;
            }
            if (fade < 1 && !spec.activeAt(Math.min(i.t, i.def.durationTicks() - 0.01))) {
                continue;
            }
            ClientVisual visual = VISUALS.get(spec.type());
            String key = i.id + "#" + spec.index();
            if (visual == null || failed.contains(key)) {
                continue;
            }
            fill(i, spec, level, cam, now, fade);
            try {
                visual.frame(ctx, frame);
            } catch (RuntimeException e) {
                failed.add(key);
                LiveEvents.LOGGER.error("Visual {} of event #{} failed and was disabled", spec.type(), i.id, e);
            }
        }
    }

    private void fill(ClientInstance i, ModuleSpec spec, ClientLevel level, Vec3 cam, long now, double fade) {
        ctx.instance = i;
        ctx.spec = spec;
        ctx.params = i.params(spec);
        ctx.t = i.t;
        ctx.progress = spec.progress(i.t);
        ctx.nowNanos = now;
        ctx.level = level;
        ctx.camera = cam;
        ctx.distance = Math.hypot(cam.x - i.center.x, cam.z - i.center.z);
        ctx.exposure = exposure;
        ctx.quality = quality();
        ctx.fade = fade;
    }

    private void drawScars(ClientLevel level) {
        if (ClientEventState.get().scars().isEmpty()) {
            return;
        }
        // Solo de noche: de día apenas se ven
        float night = level.isDarkOutside() ? 1f : 0.12f;
        long now = System.nanoTime();
        for (ClientEventState.Scar s : ClientEventState.get().scars()) {
            if (s.kind().equals("nebula")) {
                SkyVisuals.Nebula.draw(frame, s.azimuth(), s.altitude(), s.colors().length > 0 ? s.colors() : NEBULA_DEFAULT,
                        s.spanDeg(), 0.45f * night, s.seed(), quality().ordinal());
            } else if (s.kind().equals("pulsar")) {
                SkyVisuals.Pulsar.draw(frame, s.azimuth(), s.altitude(), s.colors().length > 0 ? s.colors()[0] : 0x7DF9FF,
                        30, 20, 0.7f * night, now);
            }
        }
    }

    /**
     * Opciones vanilla (escala de efectos de pantalla, destellos de rayos, oscuridad), opciones propias (intensidad
     * máxima, temblor, reducir movimiento) y perfil seguro. Nunca se quita el HUD ni se bloquea la vista del todo.
     */
    private void applyAccessibility(Minecraft mc) {
        ClientConfig cfg = ClientConfig.get();
        double screenScale = mc.options.screenEffectScale().get();
        double darkness = mc.options.darknessEffectScale().get();
        boolean hideFlashes = mc.options.hideLightningFlash().get();
        double maxFlash = cfg.max_flash_intensity * (hideFlashes ? 0.3 : 1);
        boolean anySafe = false;
        for (ClientInstance i : ClientEventState.get().instances()) {
            anySafe |= i.profile == Profile.SAFE;
        }
        if (anySafe) {
            maxFlash = Math.min(maxFlash, 0.4);
            frame.glitch = Math.min(frame.glitch, 0.2f);
            frame.glitchSlices = false;
        }
        float exposureF = frame.exposure;
        frame.flashA = (float) Math.min(frame.flashA, maxFlash) * (0.3f + 0.7f * exposureF);
        frame.whiteoutA = (float) Math.min(frame.whiteoutA, maxFlash) * exposureF;
        frame.skyTintA *= exposureF;
        frame.horizonA *= exposureF;
        frame.lightA *= 0.4f + 0.6f * exposureF;
        frame.dayA *= exposureF;
        frame.blackout *= (float) (exposureF * Math.max(0.25, darkness));
        frame.night *= (float) Math.max(0.25, darkness);
        frame.glitch *= (float) screenScale;
        frame.lensDirt *= exposureF;
        float shake = frame.shake * (float) Math.max(0.2, screenScale);
        if (!cfg.camera_shake) {
            shake = 0;
        } else if (cfg.reduce_motion) {
            shake *= 0.3f;
        }
        if (anySafe) {
            shake *= 0.3f;
        }
        if (mc.player != null) {
            if (mc.player.isFallFlying()) {
                shake *= 0.15f; // con élitros, casi sin temblor para no provocar caídas
            } else if (mc.player.isPassenger()) {
                shake *= 0.5f;
            }
            if (mc.player.isDeadOrDying()) {
                frame.reset(); // nada sobre la pantalla de muerte
                return;
            }
        }
        frame.shake = shake;
    }

    /** Regla de destellos global (todos los eventos juntos). */
    boolean requestFlash(long nowNanos, double areaRatio, int color, boolean safe) {
        if (safe && areaRatio >= FlashLimiter.LARGE_AREA) {
            return false; // en safe nunca hay cortes duros
        }
        if (FlashLimiter.isSaturatedRed(color) && areaRatio >= FlashLimiter.LARGE_AREA) {
            return false;
        }
        return flashLimiter.tryFlash(nowNanos, areaRatio);
    }

    public int recentFlashes() {
        return flashLimiter.recentCount(System.nanoTime());
    }

    // ================================================================================================ tick

    /** Tick de cliente: partículas, sonidos, silencio de música, exposición y calidad automática. */
    public void tick() {
        Minecraft mc = Minecraft.getInstance();
        ClientLevel level = mc.level;
        ClientEventState state = ClientEventState.get();
        if (level == null || mc.player == null) {
            return;
        }
        playPendingSounds(level);
        if (state.panicked() || state.instances().isEmpty()) {
            ducked = false;
            return;
        }
        maybeShowNotice(mc);
        Vec3 cam = CameraShake.cameraPosition();
        BlockPos pos = BlockPos.containing(cam);
        int sky = level.getBrightness(LightLayer.SKY, pos);
        if (level.dimensionType().hasCeiling()) {
            sky = 0;
        }
        exposureTarget = Math.max(0.15, sky / 15.0);
        if (mc.player.isUnderWater()) {
            exposureTarget *= 0.5;
        }
        ClientConfig cfg = ClientConfig.get();
        tickParticleBudget = (int) Math.max(0, cfg.particle_limit / 60.0 * quality().particleFactor());
        spawnedThisTick = 0;
        long now = System.nanoTime();
        String dim = level.dimension().identifier().toString();
        for (ClientInstance i : state.instances()) {
            if (!i.dimension.equals(dim)) {
                continue;
            }
            double t = i.clock.advance(now);
            i.t = t;
            for (ModuleSpec original : i.def.allModules()) {
            ModuleSpec spec = i.visualSpec(original);
                if (!spec.side().onClient() || !(spec.activeAt(t) || spec.shortType().equals("meteor_impacts") && t >= spec.fromTicks() && t < i.vars.getOrDefault("duration",i.def.durationTicks()/20)*20)) {
                    continue;
                }
                ClientVisual visual = VISUALS.get(spec.type());
                String key = i.id + "#" + spec.index();
                if (visual == null || failed.contains(key)) {
                    continue;
                }
                fill(i, spec, level, cam, now, 1);
                try {
                    visual.tick(ctx);
                } catch (RuntimeException e) {
                    failed.add(key);
                    LiveEvents.LOGGER.error("Visual {} of event #{} failed and was disabled", spec.type(), i.id, e);
                }
            }
        }
        boolean duck = frame.duck < 0.5f;
        if (duck && !ducked) {
            mc.getMusicManager().stopPlaying(); // silencio: la música vuelve sola más tarde
        }
        ducked = duck;
        autoQuality(mc);
    }

    private void maybeShowNotice(Minecraft mc) {
        ClientConfig cfg = ClientConfig.get();
        if (noticeShown || cfg.photosensitivity_notice_seen || !ClientEventState.get().photosensitivityNotice() || mc.player == null) {
            return;
        }
        noticeShown = true;
        mc.player.sendSystemMessage(net.minecraft.network.chat.Component.translatable("liveevents.client.photosensitivity")
                .withStyle(net.minecraft.ChatFormatting.YELLOW));
        cfg.photosensitivity_notice_seen = true;
        cfg.save();
    }

    /** Si los FPS bajan de 40 durante 2 s, baja un nivel; si se recuperan durante 10 s, sube (§7.14). */
    private void autoQuality(Minecraft mc) {
        if (!ClientConfig.get().auto_quality) {
            return;
        }
        long now = System.nanoTime();
        if (now - lastFpsCheck < 1_000_000_000L) {
            return;
        }
        lastFpsCheck = now;
        int fps = mc.getFps();
        if (fps < 40) {
            goodFpsSeconds = 0;
            if (++lowFpsSeconds >= 2 && quality().ordinal() > 0) {
                quality = quality().lower();
                lowFpsSeconds = 0;
            }
        } else if (fps > 55) {
            lowFpsSeconds = 0;
            if (++goodFpsSeconds >= 10 && quality().ordinal() < ClientConfig.get().quality().ordinal()) {
                quality = quality().higher();
                goodFpsSeconds = 0;
            }
        }
    }

    /** LOD por distancia (§7.2) y presupuesto global por tick (§7.3). */
    int particleBudget(double requested, double distance) {
        double lod = distance < 64 ? 1 : distance < 192 ? 0.4 : 0;
        int wanted = (int) Math.floor(requested * lod * quality().particleFactor() + Math.random());
        int allowed = Math.max(0, Math.min(wanted, tickParticleBudget - spawnedThisTick));
        spawnedThisTick += allowed;
        return allowed;
    }

    void spawn(ParticleOptions options, double x, double y, double z, double vx, double vy, double vz) {
        ClientLevel level = Minecraft.getInstance().level;
        if (level != null) {
            level.addParticle(options, x, y, z, vx, vy, vz);
        }
    }

    // ================================================================================================ sonido

    public static double soundDelaySeconds(double distance) {
        return Math.max(0, distance) / SPEED_OF_SOUND;
    }

    void playSound(String id, float volume, float pitch, double delaySeconds, String position, VisualContext c) {
        Vec3 cam = c.camera;
        double x = cam.x, y = cam.y, z = cam.z;
        if (position.equals("center")) {
            // Atenuación por capas: lejos se oye más bajo, pero siempre en la posición del oyente (sin cortes por rango)
            volume *= (float) Math.max(0.15, 1 - c.distance / 512);
        } else if (position.equals("anchor")) {
            double az = Math.toRadians(c.anchorAzimuth()), alt = Math.toRadians(c.anchorAltitude());
            x += Math.sin(az) * Math.cos(alt) * 8;
            y += Math.sin(alt) * 8;
            z += -Math.cos(az) * Math.cos(alt) * 8;
        }
        playSoundAt(id, volume, pitch, delaySeconds, x, y, z);
    }

    void playSoundAt(String id, float volume, float pitch, double delaySeconds, double x, double y, double z) {
        Identifier key;
        try {
            key = Identifier.parse(id);
        } catch (RuntimeException e) {
            return;
        }
        float v = (float) (volume * ClientConfig.get().event_volume);
        if (v <= 0.01f || sounds.size() >= 64) {
            return; // tope de voces
        }
        sounds.add(new PendingSound(System.nanoTime() + (long) (delaySeconds * 1e9), key, v, pitch, x, y, z));
    }

    private void playPendingSounds(ClientLevel level) {
        if (sounds.isEmpty()) {
            return;
        }
        long now = System.nanoTime();
        int played = 0;
        Iterator<PendingSound> it = sounds.iterator();
        while (it.hasNext() && played < 8) {
            PendingSound s = it.next();
            if (s.atNanos() > now) {
                continue;
            }
            it.remove();
            if (ClientEventState.get().panicked()) {
                continue;
            }
            SoundEvent event = BuiltInRegistries.SOUND_EVENT.getValue(s.id());
            if (event != null) {
                // Si el jugador se ha alejado mucho, el sonido se oye igual (los eventos celestes no se atenúan)
                Vec3 cam = CameraShake.cameraPosition();
                double dx = s.x() - cam.x, dy = s.y() - cam.y, dz = s.z() - cam.z;
                double d = Math.sqrt(dx * dx + dy * dy + dz * dz);
                double k = d > 12 ? 12 / d : 1;
                level.playLocalSound(cam.x + dx * k, cam.y + dy * k, cam.z + dz * k, event, SoundSource.WEATHER, s.volume(), s.pitch(), false);
                played++;
            }
        }
    }

    /** /le panic o tecla de pánico: corte inmediato de todo lo del cliente. */
    public void panic() {
        sounds.clear();
        frame.reset();
        CameraShake.set(0, 0, 0);
        flashLimiter.reset();
    }

    public void onDisconnect() {
        panic();
        failed.clear();
        noticeShown = false;
        quality = null;
    }
}
