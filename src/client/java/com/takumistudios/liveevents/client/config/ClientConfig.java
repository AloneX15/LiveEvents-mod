package com.takumistudios.liveevents.client.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonParseException;
import com.takumistudios.liveevents.LiveEvents;
import com.takumistudios.liveevents.core.definition.Profile;
import com.takumistudios.liveevents.util.AtomicFiles;
import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Locale;

/**
 * Opciones del cliente (§12.2) en {@code config/liveevents/client.json}. Valores fuera de rango se corrigen con un
 * aviso; un archivo corrupto se copia a .bak y se regenera.
 */
public final class ClientConfig {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static ClientConfig instance;

    public enum Quality {
        LOW, MEDIUM, HIGH, ULTRA;

        public double particleFactor() {
            return switch (this) {
                case LOW -> 0.25;
                case MEDIUM -> 0.5;
                case HIGH -> 1.0;
                case ULTRA -> 1.5;
            };
        }

        public Quality lower() {
            return this == LOW ? LOW : values()[ordinal() - 1];
        }

        public Quality higher() {
            return this == ULTRA ? ULTRA : values()[ordinal() + 1];
        }
    }

    public int config_version = 1;
    public String quality = "HIGH";
    public int particle_limit = 4000;
    public double max_flash_intensity = 1.0;
    public boolean camera_shake = true;
    public boolean reduce_motion = false;
    public double event_volume = 1.0;
    public String max_profile = "cinematic";
    public boolean show_sky_scars = true;
    public boolean auto_quality = true;
    public boolean photosensitivity_notice_seen = false;

    public static ClientConfig get() {
        if (instance == null) {
            instance = load();
        }
        return instance;
    }

    private static Path file() {
        return FabricLoader.getInstance().getConfigDir().resolve("liveevents").resolve("client.json");
    }

    private static ClientConfig load() {
        Path file = file();
        ClientConfig c = new ClientConfig();
        if (Files.isRegularFile(file)) {
            try {
                ClientConfig read = GSON.fromJson(Files.readString(file, StandardCharsets.UTF_8), ClientConfig.class);
                if (read != null) {
                    c = read;
                }
            } catch (IOException | JsonParseException e) {
                LiveEvents.LOGGER.warn("client.json is corrupt; a copy was saved as client.json.bak and defaults were restored");
                try {
                    Files.copy(file, file.resolveSibling("client.json.bak"), StandardCopyOption.REPLACE_EXISTING);
                } catch (IOException copy) {
                    LiveEvents.LOGGER.warn("Could not back up client.json", copy);
                }
            }
        }
        c.validate();
        c.save();
        return c;
    }

    public void validate() {
        quality = quality().name();
        particle_limit = Math.max(0, Math.min(16000, particle_limit));
        max_flash_intensity = Double.isFinite(max_flash_intensity) ? Math.max(0, Math.min(1, max_flash_intensity)) : 1;
        event_volume = Double.isFinite(event_volume) ? Math.max(0, Math.min(1, event_volume)) : 1;
        max_profile = maxProfile().id();
    }

    public void save() {
        try {
            AtomicFiles.writeString(file(), GSON.toJson(this));
        } catch (IOException e) {
            LiveEvents.LOGGER.warn("Could not save client.json", e);
        }
    }

    public Quality quality() {
        try {
            return Quality.valueOf(quality.toUpperCase(Locale.ROOT));
        } catch (RuntimeException e) {
            return Quality.HIGH;
        }
    }

    public Profile maxProfile() {
        return Profile.parse(max_profile).orElse(Profile.CINEMATIC);
    }
}
