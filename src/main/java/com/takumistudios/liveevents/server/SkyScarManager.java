package com.takumistudios.liveevents.server;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import com.takumistudios.liveevents.LiveEvents;
import com.takumistudios.liveevents.util.AtomicFiles;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;

/**
 * Secuelas persistentes del cielo (nebulosa, púlsar…) que deja un evento (§6.3). Se guardan en
 * {@code <mundo>/liveevents/scars.json}, caducan según el tiempo de juego y se envían a quien entra o cambia de
 * dimensión.
 */
public final class SkyScarManager {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    /** Una secuela. expires_game_time = -1 → permanente. */
    public static final class Scar {
        public int id;
        public String kind;
        public String dimension;
        public String event_id;
        public double azimuth;
        public double altitude;
        public long seed;
        public List<String> colors = new ArrayList<>();
        public double span_deg = 180;
        public long created_game_time;
        public long expires_game_time = -1;
    }

    private final Path file;
    private final ExecutorService io;
    private final List<Scar> scars = new ArrayList<>();
    private int nextId = 1;

    public SkyScarManager(Path file, ExecutorService io) {
        this.file = file;
        this.io = io;
        load();
    }

    public synchronized Scar add(Scar scar) {
        scar.id = nextId++;
        scars.add(scar);
        save();
        return scar;
    }

    public synchronized List<Scar> list() {
        return new ArrayList<>(scars);
    }

    public synchronized List<Scar> forDimension(String dimension) {
        return scars.stream().filter(s -> s.dimension.equals(dimension)).toList();
    }

    /** Borra una secuela (id) o todas (id &lt; 0). Devuelve las dimensiones afectadas. */
    public synchronized List<String> clear(int id) {
        List<String> dims = new ArrayList<>();
        scars.removeIf(s -> {
            if (id < 0 || s.id == id) {
                if (!dims.contains(s.dimension)) {
                    dims.add(s.dimension);
                }
                return true;
            }
            return false;
        });
        if (!dims.isEmpty()) {
            save();
        }
        return dims;
    }

    /** Quita las caducadas; devuelve las dimensiones que han cambiado. */
    public synchronized List<String> expire(String dimension, long gameTime) {
        List<String> dims = new ArrayList<>();
        scars.removeIf(s -> {
            if (s.dimension.equals(dimension) && s.expires_game_time >= 0 && gameTime >= s.expires_game_time) {
                if (!dims.contains(s.dimension)) {
                    dims.add(s.dimension);
                }
                return true;
            }
            return false;
        });
        if (!dims.isEmpty()) {
            save();
        }
        return dims;
    }

    public synchronized String json(String dimension) {
        return GSON.toJson(forDimension(dimension));
    }

    private void load() {
        if (!Files.exists(file)) {
            return;
        }
        try {
            List<Scar> loaded = GSON.fromJson(Files.readString(file, StandardCharsets.UTF_8), new TypeToken<List<Scar>>() {
            }.getType());
            if (loaded != null) {
                for (Scar s : loaded) {
                    if (s != null && s.kind != null && s.dimension != null) {
                        scars.add(s);
                        nextId = Math.max(nextId, s.id + 1);
                    }
                }
            }
        } catch (IOException | RuntimeException e) {
            LiveEvents.LOGGER.warn("scars.json is corrupt; sky scars were reset", e);
        }
    }

    private void save() {
        String json = GSON.toJson(scars);
        io.execute(() -> {
            try {
                AtomicFiles.writeString(file, json);
            } catch (IOException e) {
                LiveEvents.LOGGER.warn("Could not save sky scars", e);
            }
        });
    }
}
