package com.takumistudios.liveevents.core;

import com.google.gson.JsonObject;
import com.takumistudios.liveevents.core.definition.DefinitionLoader;
import com.takumistudios.liveevents.core.definition.DefinitionLoader.RawDefinition;
import com.takumistudios.liveevents.core.definition.EventDefinition;
import com.takumistudios.liveevents.core.definition.ModuleParams;
import com.takumistudios.liveevents.core.definition.ModuleSpec;
import com.takumistudios.liveevents.core.definition.Profile;
import com.takumistudios.liveevents.core.definition.Scope;
import com.takumistudios.liveevents.core.definition.ValidationIssue;
import com.takumistudios.liveevents.core.time.Durations;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DefinitionLoaderTest {
    static String resource(String name) throws IOException {
        try (InputStream in = DefinitionLoaderTest.class.getResourceAsStream("/data/liveevents/liveevents/event/" + name + ".json")) {
            assertNotNull(in, "falta el recurso " + name);
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    static DefinitionLoader.Result loadAll(Map<String, String> files) {
        List<ValidationIssue> issues = new ArrayList<>();
        Map<String, RawDefinition> raws = new LinkedHashMap<>();
        for (Map.Entry<String, String> e : files.entrySet()) {
            DefinitionLoader.read(e.getValue(), e.getKey(), e.getKey() + ".json", issues).ifPresent(r -> raws.put(r.id(), r));
        }
        return DefinitionLoader.load(raws, issues);
    }

    @Test
    void officialEventsLoadWithoutErrors() throws IOException {
        DefinitionLoader.Result result = loadAll(Map.of(
                "liveevents:supernova", resource("supernova"),
                "liveevents:meteor_shower", resource("meteor_shower"),
                "liveevents:earthquake", resource("earthquake")));
        List<ValidationIssue> errors = result.issues().stream().filter(ValidationIssue::isError).toList();
        assertTrue(errors.isEmpty(), "errores: " + errors);
        assertEquals(3, result.definitions().size());

        EventDefinition supernova = result.definitions().get("liveevents:supernova");
        assertEquals(Scope.CELESTIAL, supernova.scope());
        assertEquals(1200, supernova.durationTicks(), 1e-9, "60 s = 1200 ticks");
        assertEquals(5, supernova.phases().size());
        assertEquals("breakout", supernova.phaseAt(8.0 * 20).orElseThrow().id());
        assertEquals(List.of("nebula", "pulsar"), supernova.aftermath());
        assertEquals(3 * Durations.TICKS_PER_GAME_DAY, supernova.aftermathTicks());
        assertTrue(supernova.modifiesWorld());
        assertTrue(supernova.dealsDamage());

        EventDefinition quake = result.definitions().get("liveevents:earthquake");
        assertEquals(Scope.REGIONAL, quake.scope());
        assertEquals(160, quake.params().get("radius"));
    }

    static final List<String> OFFICIAL = List.of("supernova", "meteor_shower", "earthquake", "aurora_borealis",
            "total_eclipse", "solar_storm", "great_meteor", "blizzard", "acid_rain", "black_hole");

    static Map<String, String> allOfficial() throws IOException {
        Map<String, String> files = new LinkedHashMap<>();
        for (String name : OFFICIAL) {
            files.put("liveevents:" + name, resource(name));
        }
        return files;
    }

    @Test
    void allOfficialEventsAreValid() throws IOException {
        DefinitionLoader.Result result = loadAll(allOfficial());
        List<ValidationIssue> errors = result.issues().stream().filter(ValidationIssue::isError).toList();
        assertTrue(errors.isEmpty(), "errores: " + errors);
        assertEquals(OFFICIAL.size(), result.definitions().size());
    }

    /** El Example Pack (examples/) tiene que cargar sin errores ni avisos. */
    @Test
    void examplePackIsValid() throws IOException {
        String root = System.getProperty("liveevents.root");
        if (root == null) {
            return;
        }
        java.nio.file.Path events = java.nio.file.Path.of(root, "examples/LiveEvents-Example-Pack/datapack/data/example/liveevents/event");
        Map<String, String> files = new LinkedHashMap<>(allOfficial());
        try (var stream = java.nio.file.Files.list(events)) {
            for (java.nio.file.Path p : stream.toList()) {
                String name = p.getFileName().toString().replace(".json", "");
                files.put("example:" + name, java.nio.file.Files.readString(p, StandardCharsets.UTF_8));
            }
        }
        DefinitionLoader.Result result = loadAll(files);
        List<ValidationIssue> exampleIssues = result.issues().stream().filter(i -> i.eventId().startsWith("example:")).toList();
        assertTrue(exampleIssues.isEmpty(), "problemas en el Example Pack: " + exampleIssues);
        assertTrue(result.definitions().containsKey("example:blood_moon"));
        assertTrue(result.definitions().containsKey("example:red_supernova"));
    }

    @Test
    void officialEventsHaveNoUnexpectedWarnings() throws IOException {
        DefinitionLoader.Result result = loadAll(allOfficial());
        // El único aviso permitido es el de la regla de destellos en los cortes de la supernova (8,0 y 8,1 s)
        List<ValidationIssue> warnings = result.issues().stream().filter(i -> !i.isError())
                .filter(i -> !i.message().contains("cortes en un segundo")).toList();
        assertTrue(warnings.isEmpty(), "avisos: " + warnings);
    }

    @Test
    void moduleTimingUsesAtAndDurationCorrectly() throws IOException {
        EventDefinition supernova = loadAll(Map.of("liveevents:supernova", resource("supernova")))
                .definitions().get("liveevents:supernova");
        ModuleSpec glitch = supernova.phase("breakout").orElseThrow().modules().stream()
                .filter(m -> m.shortType().equals("chromatic_glitch")).findFirst().orElseThrow();
        assertEquals(200, glitch.fromTicks(), 1e-9);
        assertEquals(212, glitch.toTicks(), 1e-9, "at 10,0 s + 0,6 s");
        ModuleSpec radiation = supernova.phase("breakout").orElseThrow().modules().stream()
                .filter(m -> m.shortType().equals("status_effect")).findFirst().orElseThrow();
        assertEquals(1, radiation.durationTicks(), 1e-9, "en status_effect 'duration' es la del efecto: el módulo es puntual");
    }

    @Test
    void profilesOverrideParameters() throws IOException {
        EventDefinition supernova = loadAll(Map.of("liveevents:supernova", resource("supernova")))
                .definitions().get("liveevents:supernova");
        ModuleSpec whiteout = supernova.phase("demon_sun").orElseThrow().modules().stream()
                .filter(m -> m.shortType().equals("whiteout")).findFirst().orElseThrow();
        ModuleParams safe = new ModuleParams(ModuleParams.forProfile(whiteout.raw(), Profile.SAFE), supernova.palette(), supernova.params());
        ModuleParams cine = new ModuleParams(ModuleParams.forProfile(whiteout.raw(), Profile.CINEMATIC), supernova.palette(), supernova.params());
        ModuleParams game = new ModuleParams(ModuleParams.forProfile(whiteout.raw(), Profile.GAMEPLAY), supernova.palette(), supernova.params());
        assertEquals(0.4, safe.number("peak", 0), 1e-9);
        assertEquals(1.0, cine.number("peak", 0), 1e-9);
        assertEquals(0.85, game.number("peak", 0), 1e-9);
        assertEquals(0xFFFFFF, game.color("color", 0xFFFFFF));
    }

    @Test
    void rangesAndExpressionsResolve() {
        JsonObject m = new JsonObject();
        JsonObject range = new JsonObject();
        range.addProperty("from", 0);
        range.addProperty("to", 10);
        m.add("amplitude", range);
        m.addProperty("radius", "$radius * 0.5");
        ModuleParams p = new ModuleParams(m, com.takumistudios.liveevents.core.definition.Palette.EMPTY, Map.of("radius", 128.0));
        assertEquals(5, p.range("amplitude", 0.5, 0), 1e-9);
        assertEquals(64, p.number("radius", 0), 1e-9);
        assertEquals(7, p.number("missing", 7), 1e-9);
    }

    @Test
    void variantInheritsAndOverrides() throws IOException {
        String variant = """
                {
                  "format_version": 1,
                  "id": "miserie:supernova_roja",
                  "extends": "liveevents:supernova",
                  "display_name": "Supernova Roja (episodio 12)",
                  "palette": { "cyan": "#FF2244", "pulsar": "#FF6688" },
                  "overrides": {
                    "breakout.light_cut.cuts": [ ["8.0s", "$cyan"], ["9.0s", "$violet"] ],
                    "aftermath.duration": "permanent"
                  }
                }""";
        DefinitionLoader.Result result = loadAll(Map.of(
                "liveevents:supernova", resource("supernova"),
                "miserie:supernova_roja", variant));
        assertTrue(result.issues().stream().noneMatch(ValidationIssue::isError), result.issues().toString());
        EventDefinition red = result.definitions().get("miserie:supernova_roja");
        assertEquals("Supernova Roja (episodio 12)", red.displayName());
        assertEquals(0xFF2244, red.palette().get("cyan").orElseThrow());
        assertEquals(0x8A2BE2, red.palette().get("violet").orElseThrow(), "los colores no cambiados se heredan");
        assertEquals(Durations.PERMANENT, red.aftermathTicks());
        ModuleSpec cut = red.phase("breakout").orElseThrow().modules().stream()
                .filter(m -> m.shortType().equals("light_cut")).findFirst().orElseThrow();
        assertEquals(2, cut.raw().getAsJsonArray("cuts").size());
    }

    @Test
    void preciseErrorsWithSuggestions() {
        String broken = """
                {
                  "format_version": 1,
                  "id": "miserie:apocalipsis",
                  "palette": { "cyan": "#00FFFF" },
                  "phases": [
                    { "id": "a", "from": "0s", "to": "5s", "modules": [ { "type": "sky_tint", "color": "$cyan" } ] },
                    { "id": "b", "from": "5s", "to": "10s", "modules": [
                      { "type": "flash" },
                      { "type": "sky_tnit", "color": "$cyan" },
                      { "type": "sky_tint", "color": "$cyna" }
                    ] }
                  ]
                }""";
        DefinitionLoader.Result result = loadAll(Map.of("miserie:apocalipsis", broken));
        assertFalse(result.definitions().containsKey("miserie:apocalipsis"), "un evento con errores se desactiva");
        List<String> messages = result.issues().stream().map(ValidationIssue::toString).toList();
        assertTrue(messages.stream().anyMatch(s -> s.contains("phases[1].modules[2].color") && s.contains("¿Querías decir $cyan?")), messages.toString());
        assertTrue(messages.stream().anyMatch(s -> s.contains("phases[1].modules[1].type") && s.contains("sky_tint")), messages.toString());
    }

    @Test
    void circularInheritanceAndMissingFormatAreRejected() {
        String a = "{\"format_version\":1,\"id\":\"x:a\",\"extends\":\"x:b\"}";
        String b = "{\"format_version\":1,\"id\":\"x:b\",\"extends\":\"x:a\"}";
        String noFormat = "{\"id\":\"x:c\",\"phases\":[{\"id\":\"p\",\"from\":\"0s\",\"to\":\"1s\"}]}";
        String future = "{\"format_version\":99,\"id\":\"x:d\",\"phases\":[{\"id\":\"p\",\"from\":\"0s\",\"to\":\"1s\"}]}";
        DefinitionLoader.Result result = loadAll(Map.of("x:a", a, "x:b", b, "x:c", noFormat, "x:d", future));
        assertTrue(result.definitions().isEmpty());
        List<String> messages = result.issues().stream().map(ValidationIssue::message).toList();
        assertTrue(messages.stream().anyMatch(s -> s.contains("herencia circular")), messages.toString());
        assertTrue(messages.stream().anyMatch(s -> s.contains("format_version")), messages.toString());
        assertTrue(messages.stream().anyMatch(s -> s.contains("versión más nueva")), messages.toString());
    }

    @Test
    void oversizedAndMalformedFilesAreRejected() {
        List<ValidationIssue> issues = new ArrayList<>();
        assertTrue(DefinitionLoader.read("{".repeat(10), "x:y", "y.json", issues).isEmpty());
        assertTrue(DefinitionLoader.read(" ".repeat(DefinitionLoader.MAX_FILE_BYTES + 1), "x:y", "y.json", issues).isEmpty());
        assertTrue(DefinitionLoader.read("[]", "x:y", "y.json", issues).isEmpty());
        assertTrue(DefinitionLoader.read("{\"id\":\"BAD ID\"}", null, "y.json", issues).isEmpty());
        assertEquals(4, issues.size());
    }
}
