package com.takumistudios.liveevents.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonParseException;
import com.takumistudios.liveevents.LiveEvents;
import com.takumistudios.liveevents.core.definition.Profile;
import com.takumistudios.liveevents.core.definition.WorldMode;
import com.takumistudios.liveevents.core.safety.RateLimiter;
import com.takumistudios.liveevents.core.time.Durations;
import com.takumistudios.liveevents.util.AtomicFiles;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;

/**
 * {@code config/liveevents/server.json} (§12.1). Los campos son públicos para que Gson los lea; tras cargar se
 * validan y los valores fuera de rango se corrigen con un aviso en el log. Nunca produce un crash.
 */
public final class ServerConfig {
    public static final int CURRENT_VERSION = 1;
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();

    public int config_version = CURRENT_VERSION;
    public int max_concurrent_events = 2;
    public double tick_budget_ms = 2.0;
    public int max_block_changes_per_tick = 256;
    public boolean respect_protections = true;
    public List<Region> excluded_regions = new ArrayList<>();
    public int journal_expiry_days = 7;
    public String default_world_mode = "revert_manual";
    public boolean photosensitivity_notice = true;
    public boolean audit_log = true;
    public String default_profile = "gameplay";
    public String max_profile = "cinematic";
    public String sky_scars_default_duration = "3d";
    public int max_lightning_per_event = 12;
    public String on_restart = "cancel";
    public boolean pause_when_empty = true;
    public int watchdog_mspt = 100;
    public int watchdog_seconds = 10;
    public String start_rate_limit = "5/min";
    public List<String> allowed_dimensions = new ArrayList<>(List.of("minecraft:overworld", "minecraft:the_end"));
    public String missing_mod_message = "";
    public String discord_webhook = "";
    public String timezone = "";
    public int audit_retention_days = 30;
    public int respawn_grace_seconds = 5;
    public int schedule_warning_minutes = 5;
    public boolean damage_in_protected_regions = true;
    public double interest_radius = 0;

    /** Zona excluida: ningún evento cambia bloques dentro. */
    public static final class Region {
        public String dimension = "minecraft:overworld";
        public int min_x, min_y = -64, min_z, max_x, max_y = 320, max_z;
        public String name = "";

        public boolean contains(String dim, int x, int y, int z) {
            return dimension.equals(dim) && x >= Math.min(min_x, max_x) && x <= Math.max(min_x, max_x)
                    && y >= Math.min(min_y, max_y) && y <= Math.max(min_y, max_y)
                    && z >= Math.min(min_z, max_z) && z <= Math.max(min_z, max_z);
        }
    }

    public static ServerConfig load(Path file) {
        ServerConfig config;
        if (Files.isRegularFile(file)) {
            try {
                config = GSON.fromJson(Files.readString(file, StandardCharsets.UTF_8), ServerConfig.class);
                if (config == null) {
                    throw new JsonParseException("archivo vacío");
                }
            } catch (IOException | JsonParseException e) {
                Path backup = file.resolveSibling(file.getFileName() + ".bak");
                LiveEvents.LOGGER.warn("server.json is corrupt ({}); a copy was saved as {} and defaults were regenerated",
                        e.getMessage(), backup.getFileName());
                try {
                    Files.copy(file, backup, StandardCopyOption.REPLACE_EXISTING);
                } catch (IOException copyError) {
                    LiveEvents.LOGGER.warn("Could not back up server.json", copyError);
                }
                config = new ServerConfig();
            }
        } else {
            config = new ServerConfig();
        }
        config.validate();
        config.save(file);
        return config;
    }

    public void save(Path file) {
        try {
            AtomicFiles.writeString(file, GSON.toJson(this));
        } catch (IOException e) {
            LiveEvents.LOGGER.warn("Could not save {}", file, e);
        }
    }

    /** Corrige valores fuera de rango con un aviso. */
    public void validate() {
        if (config_version > CURRENT_VERSION) {
            LiveEvents.LOGGER.warn("server.json was written by a newer LiveEvents (version {}); unknown fields are ignored", config_version);
        }
        config_version = CURRENT_VERSION;
        max_concurrent_events = clamp("max_concurrent_events", max_concurrent_events, 1, 16);
        tick_budget_ms = clamp("tick_budget_ms", tick_budget_ms, 0.2, 20);
        max_block_changes_per_tick = clamp("max_block_changes_per_tick", max_block_changes_per_tick, 1, 8192);
        journal_expiry_days = clamp("journal_expiry_days", journal_expiry_days, 1, 365);
        audit_retention_days = clamp("audit_retention_days", audit_retention_days, 1, 3650);
        max_lightning_per_event = clamp("max_lightning_per_event", max_lightning_per_event, 0, 500);
        watchdog_mspt = clamp("watchdog_mspt", watchdog_mspt, 50, 10_000);
        watchdog_seconds = clamp("watchdog_seconds", watchdog_seconds, 2, 600);
        respawn_grace_seconds = clamp("respawn_grace_seconds", respawn_grace_seconds, 0, 60);
        schedule_warning_minutes = clamp("schedule_warning_minutes", schedule_warning_minutes, 0, 120);
        interest_radius = clamp("interest_radius", interest_radius, 0, 100_000);
        if (WorldMode.parse(default_world_mode).isEmpty()) {
            warn("default_world_mode", default_world_mode, "revert_manual");
            default_world_mode = "revert_manual";
        }
        if (Profile.parse(default_profile).isEmpty()) {
            warn("default_profile", default_profile, "gameplay");
            default_profile = "gameplay";
        }
        if (Profile.parse(max_profile).isEmpty()) {
            warn("max_profile", max_profile, "cinematic");
            max_profile = "cinematic";
        }
        if (!on_restart.equals("cancel") && !on_restart.equals("resume")) {
            warn("on_restart", on_restart, "cancel");
            on_restart = "cancel";
        }
        try {
            Durations.parseTicks(sky_scars_default_duration);
        } catch (IllegalArgumentException e) {
            warn("sky_scars_default_duration", sky_scars_default_duration, "3d");
            sky_scars_default_duration = "3d";
        }
        if (RateLimiter.parse(start_rate_limit, null) == null) {
            warn("start_rate_limit", start_rate_limit, "5/min");
            start_rate_limit = "5/min";
        }
        if (!timezone.isEmpty()) {
            try {
                ZoneId.of(timezone);
            } catch (RuntimeException e) {
                warn("timezone", timezone, "(system)");
                timezone = "";
            }
        }
        if (!discord_webhook.isEmpty() && !discord_webhook.startsWith("https://discord.com/api/webhooks/")
                && !discord_webhook.startsWith("https://discordapp.com/api/webhooks/")) {
            LiveEvents.LOGGER.warn("discord_webhook does not look like a Discord webhook URL; it is disabled");
            discord_webhook = "";
        }
        if (excluded_regions == null) {
            excluded_regions = new ArrayList<>();
        }
        if (allowed_dimensions == null || allowed_dimensions.isEmpty()) {
            allowed_dimensions = new ArrayList<>(List.of("minecraft:overworld", "minecraft:the_end"));
        }
        if (missing_mod_message == null) {
            missing_mod_message = "";
        }
    }

    public Profile defaultProfile() {
        return Profile.parse(default_profile).orElse(Profile.GAMEPLAY);
    }

    public Profile maxProfile() {
        return Profile.parse(max_profile).orElse(Profile.CINEMATIC);
    }

    public WorldMode defaultWorldMode() {
        return WorldMode.parse(default_world_mode).orElse(WorldMode.REVERT_MANUAL);
    }

    public ZoneId zone() {
        return timezone.isEmpty() ? ZoneId.systemDefault() : ZoneId.of(timezone);
    }

    public long scarTicks() {
        return Durations.parseTicks(sky_scars_default_duration);
    }

    private static int clamp(String name, int value, int min, int max) {
        if (value < min || value > max) {
            int fixed = Math.max(min, Math.min(max, value));
            LiveEvents.LOGGER.warn("server.json: {}={} is out of range [{}, {}]; using {}", name, value, min, max, fixed);
            return fixed;
        }
        return value;
    }

    private static double clamp(String name, double value, double min, double max) {
        if (!Double.isFinite(value) || value < min || value > max) {
            double fixed = Double.isFinite(value) ? Math.max(min, Math.min(max, value)) : min;
            LiveEvents.LOGGER.warn("server.json: {}={} is out of range [{}, {}]; using {}", name, value, min, max, fixed);
            return fixed;
        }
        return value;
    }

    private static void warn(String name, String value, String fixed) {
        LiveEvents.LOGGER.warn("server.json: invalid {}='{}'; using {}", name, value, fixed);
    }
}
