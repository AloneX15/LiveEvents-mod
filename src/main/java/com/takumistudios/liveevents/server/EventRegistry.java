package com.takumistudios.liveevents.server;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.takumistudios.liveevents.LiveEvents;
import com.takumistudios.liveevents.core.definition.DefinitionLoader;
import com.takumistudios.liveevents.core.definition.DefinitionLoader.RawDefinition;
import com.takumistudios.liveevents.core.definition.EventDefinition;
import com.takumistudios.liveevents.core.definition.ValidationIssue;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.packs.resources.Resource;

import java.io.BufferedReader;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;

/**
 * Definiciones de evento, paletas de bloques y secuencias cargadas de: datapacks ({@code data/<ns>/liveevents/event/}),
 * incluidas las oficiales del mod, y {@code config/liveevents/events/} (variantes de los admins, que ganan si repiten
 * id). Se recarga con {@code /le reload} y con {@code /reload}.
 */
public final class EventRegistry {
    private volatile Map<String, EventDefinition> definitions = Map.of();
    private volatile List<ValidationIssue> issues = List.of();
    private volatile Map<String, BlockPalette> palettes = Map.of();
    private volatile Map<String, JsonObject> sequences = Map.of();
    private volatile String syncJson = "[]";
    private final Path configDir;

    public EventRegistry(Path configDir) {
        this.configDir = configDir;
    }

    public void reload(MinecraftServer server) {
        List<ValidationIssue> readIssues = new ArrayList<>();
        Map<String, RawDefinition> raws = new LinkedHashMap<>();
        Map<String, BlockPalette> newPalettes = new TreeMap<>();
        Map<String, JsonObject> newSequences = new TreeMap<>();

        for (Map.Entry<Identifier, Resource> e : list(server, "liveevents/event").entrySet()) {
            String id = toId(e.getKey(), "liveevents/event/");
            read(e.getValue(), e.getKey().toString()).flatMap(text -> DefinitionLoader.read(text, id, "datapack " + e.getKey(), readIssues))
                    .ifPresent(raw -> {
                        if (!raw.id().equals(id)) {
                            readIssues.add(ValidationIssue.warning(id, e.getKey().toString(), "id",
                                    "el id del JSON (" + raw.id() + ") no coincide con la ruta; se usa " + id));
                        }
                        raws.put(id, new RawDefinition(id, raw.json(), raw.source()));
                    });
        }
        for (Map.Entry<Identifier, Resource> e : list(server, "liveevents/palette").entrySet()) {
            String id = toId(e.getKey(), "liveevents/palette/");
            read(e.getValue(), e.getKey().toString()).ifPresent(text -> {
                try {
                    newPalettes.put(id, BlockPalette.parse(id, JsonParser.parseString(text).getAsJsonObject(), server.registryAccess()));
                } catch (RuntimeException ex) {
                    readIssues.add(ValidationIssue.error(id, e.getKey().toString(), "", "paleta no válida: " + ex.getMessage()));
                }
            });
        }
        for (Map.Entry<Identifier, Resource> e : list(server, "liveevents/sequence").entrySet()) {
            String id = toId(e.getKey(), "liveevents/sequence/");
            read(e.getValue(), e.getKey().toString()).ifPresent(text -> parseSequence(id, text, e.getKey().toString(), newSequences, readIssues));
        }

        Path events = configDir.resolve("events");
        Path seqDir = configDir.resolve("sequences");
        try {
            Files.createDirectories(events);
            Files.createDirectories(seqDir);
        } catch (IOException ex) {
            LiveEvents.LOGGER.warn("Could not create {}", events, ex);
        }
        for (Path file : jsonFiles(events)) {
            try {
                String text = Files.readString(file, StandardCharsets.UTF_8);
                DefinitionLoader.read(text, null, "config/liveevents/events/" + file.getFileName(), readIssues)
                        .ifPresent(raw -> raws.put(raw.id(), raw));
            } catch (IOException ex) {
                readIssues.add(ValidationIssue.error(file.getFileName().toString(), file.toString(), "", "no se pudo leer: " + ex.getMessage()));
            }
        }
        for (Path file : jsonFiles(seqDir)) {
            try {
                String text = Files.readString(file, StandardCharsets.UTF_8);
                JsonObject o = JsonParser.parseString(text).getAsJsonObject();
                String id = o.has("id") ? o.get("id").getAsString() : "config:" + file.getFileName().toString().replace(".json", "");
                parseSequence(id, text, file.toString(), newSequences, readIssues);
            } catch (IOException | RuntimeException ex) {
                readIssues.add(ValidationIssue.error(file.getFileName().toString(), file.toString(), "", "secuencia no válida: " + ex.getMessage()));
            }
        }

        DefinitionLoader.Result result = DefinitionLoader.load(raws, readIssues);
        for (EventDefinition def : result.definitions().values()) {
            checkPalettes(def, newPalettes, readIssues);
        }
        definitions = result.definitions();
        issues = result.issues();
        palettes = Collections.unmodifiableMap(newPalettes);
        sequences = Collections.unmodifiableMap(newSequences);
        JsonArray sync = new JsonArray();
        for (EventDefinition def : definitions.values()) {
            sync.add(def.resolvedJson());
        }
        syncJson = sync.toString();

        long errors = issues.stream().filter(ValidationIssue::isError).count();
        LiveEvents.LOGGER.info("Loaded {} event definitions, {} palettes, {} sequences ({} errors, {} warnings)",
                definitions.size(), palettes.size(), sequences.size(), errors, issues.size() - errors);
        for (ValidationIssue issue : issues) {
            if (issue.isError()) {
                LiveEvents.LOGGER.warn("[validate] {}", issue);
            } else {
                LiveEvents.LOGGER.debug("[validate] {}", issue);
            }
        }
    }

    private static void checkPalettes(EventDefinition def, Map<String, BlockPalette> palettes, List<ValidationIssue> issues) {
        def.allModules().forEach(m -> {
            if (m.raw().has("palette")) {
                String p = m.raw().get("palette").getAsString();
                if (!palettes.containsKey(p)) {
                    issues.add(ValidationIssue.warning(def.id(), def.source(), m.phaseId() + "." + m.shortType() + ".palette",
                            "la paleta " + p + " no existe; el módulo no cambiará bloques"));
                }
            }
        });
    }

    private static void parseSequence(String id, String text, String source, Map<String, JsonObject> out, List<ValidationIssue> issues) {
        try {
            JsonObject o = JsonParser.parseString(text).getAsJsonObject();
            if (!o.has("steps") || !o.get("steps").isJsonArray()) {
                issues.add(ValidationIssue.error(id, source, "steps", "una secuencia necesita \"steps\""));
                return;
            }
            if (o.getAsJsonArray("steps").size() > 256) {
                issues.add(ValidationIssue.error(id, source, "steps", "demasiados pasos (máximo 256)"));
                return;
            }
            o.addProperty("id", id);
            out.put(id, o);
        } catch (RuntimeException e) {
            issues.add(ValidationIssue.error(id, source, "", "JSON no válido: " + e.getMessage()));
        }
    }

    private static Map<Identifier, Resource> list(MinecraftServer server, String folder) {
        try {
            return server.getResourceManager().listResources(folder, id -> id.getPath().endsWith(".json"));
        } catch (RuntimeException e) {
            LiveEvents.LOGGER.warn("Could not list {}", folder, e);
            return Map.of();
        }
    }

    private static Optional<String> read(Resource resource, String label) {
        try (BufferedReader reader = resource.openAsReader()) {
            StringBuilder sb = new StringBuilder();
            char[] buf = new char[8192];
            int n;
            while ((n = reader.read(buf)) > 0) {
                sb.append(buf, 0, n);
                if (sb.length() > DefinitionLoader.MAX_FILE_BYTES + 1) {
                    break; // el cargador lo rechazará por tamaño
                }
            }
            return Optional.of(sb.toString());
        } catch (IOException e) {
            LiveEvents.LOGGER.warn("Could not read {}", label, e);
            return Optional.empty();
        }
    }

    private static List<Path> jsonFiles(Path dir) {
        List<Path> files = new ArrayList<>();
        if (!Files.isDirectory(dir)) {
            return files;
        }
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(dir, "*.json")) {
            stream.forEach(files::add);
        } catch (IOException e) {
            LiveEvents.LOGGER.warn("Could not list {}", dir, e);
        }
        Collections.sort(files);
        return files;
    }

    private static String toId(Identifier file, String prefix) {
        String path = file.getPath();
        path = path.substring(prefix.length(), path.length() - ".json".length());
        return file.getNamespace() + ":" + path;
    }

    public Map<String, EventDefinition> definitions() {
        return definitions;
    }

    public Optional<EventDefinition> get(String id) {
        EventDefinition def = definitions.get(id);
        if (def == null && id.indexOf(':') < 0) {
            def = definitions.get("liveevents:" + id);
        }
        return Optional.ofNullable(def);
    }

    public List<ValidationIssue> issues() {
        return issues;
    }

    public Optional<BlockPalette> palette(String id) {
        return Optional.ofNullable(palettes.get(id));
    }

    public Map<String, JsonObject> sequences() {
        return sequences;
    }

    /** JSON con todas las definiciones resueltas, para DefinitionSync. */
    public String syncJson() {
        return syncJson;
    }

    public Path configDir() {
        return configDir;
    }

    /** Escribe una variante en config/liveevents/events (para /le create). */
    public Path writeVariant(String newId, String parentId) throws IOException {
        JsonObject o = new JsonObject();
        o.addProperty("$schema", "https://raw.githubusercontent.com/AloneX15/LiveEvents-mod/main/schema/liveevents-event.schema.json");
        o.addProperty("format_version", DefinitionLoader.CURRENT_FORMAT);
        o.addProperty("id", newId);
        o.addProperty("extends", parentId);
        o.addProperty("display_name", newId);
        o.add("palette", new JsonObject());
        o.add("overrides", new JsonObject());
        Path file = configDir.resolve("events").resolve(com.takumistudios.liveevents.util.AtomicFiles.safeFileName(newId) + ".json");
        if (Files.exists(file)) {
            throw new IOException("ya existe " + file.getFileName());
        }
        com.takumistudios.liveevents.util.AtomicFiles.writeString(file, new com.google.gson.GsonBuilder().setPrettyPrinting().create().toJson(o));
        return file;
    }

    static JsonElement copy(JsonElement e) {
        return e.deepCopy();
    }
}
