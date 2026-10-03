package com.takumistudios.liveevents.core;

import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.takumistudios.liveevents.core.module.ModuleType;
import com.takumistudios.liveevents.core.module.ModuleTypeRegistry;
import com.takumistudios.liveevents.core.module.ParamSpec;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * El JSON Schema ({@code schema/}) y la referencia de módulos ({@code docs/modulos.md}; la wiki se genera desde docs/) se generan
 * del catálogo de módulos. Con {@code -PgenerateDocs} este test los escribe; sin él, falla si no están al día.
 */
class GeneratedDocsTest {
    private static final String SCHEMA_ID = "https://raw.githubusercontent.com/AloneX15/LiveEvents-mod/main/schema/liveevents-event.schema.json";

    @Test
    void generatedFilesAreUpToDate() throws IOException {
        String rootProp = System.getProperty("liveevents.root");
        if (rootProp == null) {
            return; // ejecutado fuera de Gradle
        }
        Path root = Path.of(rootProp);
        boolean generate = Boolean.parseBoolean(System.getProperty("liveevents.generateDocs", "false"));
        Map<Path, String> files = new LinkedHashMap<>();
        files.put(root.resolve("schema/liveevents-event.schema.json"), schema());
        String reference = moduleReference();
        files.put(root.resolve("docs/modulos.md"), reference);
        for (Map.Entry<Path, String> e : files.entrySet()) {
            if (generate) {
                Files.createDirectories(e.getKey().getParent());
                Files.writeString(e.getKey(), e.getValue(), StandardCharsets.UTF_8);
            } else {
                String current = Files.exists(e.getKey()) ? Files.readString(e.getKey(), StandardCharsets.UTF_8) : "";
                assertEquals(e.getValue(), current.replace("\r\n", "\n"),
                        e.getKey().getFileName() + " no está al día: ejecuta ./gradlew :26.3:test -PgenerateDocs");
            }
        }
    }

    // ------------------------------------------------------------------------------------------------ schema

    static String schema() {
        JsonObject root = new JsonObject();
        root.addProperty("$schema", "http://json-schema.org/draft-07/schema#");
        root.addProperty("$id", SCHEMA_ID);
        root.addProperty("title", "LiveEvents event definition");
        root.addProperty("description", "Definición de evento de LiveEvents (generado; no editar a mano).");
        root.addProperty("type", "object");
        JsonArray required = new JsonArray();
        required.add("format_version");
        root.add("required", required);
        JsonObject props = new JsonObject();
        props.add("$schema", str("URL de este schema"));
        props.add("format_version", withConst(integer("Versión del formato (obligatorio)"), 1));
        props.add("id", pattern(str("espacio:nombre"), "^[a-z0-9_.-]+:[a-z0-9_./-]+$"));
        props.add("extends", pattern(str("Evento del que hereda (variante)"), "^[a-z0-9_.-]+:[a-z0-9_./-]+$"));
        props.add("display_name", str("Nombre o clave de traducción"));
        props.add("description", str("Descripción o clave de traducción"));
        props.add("scope", enumOf("Ámbito", "celestial", "localized", "regional"));
        JsonObject anchor = obj("Ancla en la bóveda");
        JsonObject anchorProps = new JsonObject();
        anchorProps.add("azimuth", number("Acimut en grados (0 = norte, 90 = este)"));
        anchorProps.add("altitude", number("Altura en grados (0–90)"));
        anchor.add("properties", anchorProps);
        props.add("anchor", anchor);
        props.add("allowed_dimensions", array("Dimensiones donde se puede lanzar", str("Dimensión")));
        props.add("night_override", enumOf("Noche forzada", "none", "visual", "require"));
        props.add("clear_weather", bool("Despeja el cielo y restaura el clima al terminar"));
        props.add("lethal", bool("false = nunca baja a nadie de medio corazón"));
        props.add("affects", array("Entidades afectadas", enumOf("Tipo", "players", "hostile", "passive", "pets", "named")));
        props.add("world_mode", enumOf("Persistencia de los cambios", "permanent", "revert_on_end", "revert_manual"));
        props.add("cooldown", duration("Tiempo mínimo entre dos lanzamientos"));
        props.add("default_profile", enumOf("Perfil por defecto", "cinematic", "gameplay", "safe"));
        props.add("queue_policy", enumOf("Si hay un evento incompatible", "reject", "queue"));
        JsonObject palette = obj("Colores con nombre");
        JsonObject colorSchema = pattern(str("#RRGGBB"), "^#[0-9A-Fa-f]{6}$");
        palette.add("additionalProperties", colorSchema);
        props.add("palette", palette);
        JsonObject params = obj("Parámetros numéricos ($radius, $count…)");
        params.add("additionalProperties", number("Valor"));
        props.add("params", params);
        JsonObject overrides = obj("Cambios por ruta fase.módulo.parámetro (variantes)");
        props.add("overrides", overrides);
        JsonObject phase = obj("Fase (segmento del guion)");
        JsonObject phaseProps = new JsonObject();
        phaseProps.add("id", str("Id de la fase"));
        phaseProps.add("from", duration("Inicio"));
        phaseProps.add("to", duration("Fin"));
        phaseProps.add("title", str("Título del segmento"));
        JsonObject desc = obj("Textos del guion");
        JsonObject descProps = new JsonObject();
        descProps.add("sky", str("El Cielo"));
        descProps.add("chaos", str("El Caos"));
        desc.add("properties", descProps);
        phaseProps.add("description", desc);
        JsonObject moduleRef = new JsonObject();
        moduleRef.addProperty("$ref", "#/definitions/module");
        phaseProps.add("modules", array("Módulos", moduleRef));
        phase.add("properties", phaseProps);
        JsonArray phaseReq = new JsonArray();
        phaseReq.add("id");
        phaseReq.add("from");
        phaseReq.add("to");
        phase.add("required", phaseReq);
        props.add("phases", array("Fases", phase));
        JsonObject aftermath = obj("Secuelas persistentes del cielo");
        JsonObject aftermathProps = new JsonObject();
        aftermathProps.add("persistent", array("Secuelas", enumOf("Tipo", "nebula", "pulsar")));
        aftermathProps.add("duration", duration("Duración (p. ej. 3d o permanent)"));
        aftermath.add("properties", aftermathProps);
        props.add("aftermath", aftermath);
        root.add("properties", props);

        JsonObject definitions = new JsonObject();
        JsonObject module = obj("Módulo");
        JsonArray moduleReq = new JsonArray();
        moduleReq.add("type");
        module.add("required", moduleReq);
        JsonObject moduleProps = new JsonObject();
        JsonArray types = new JsonArray();
        for (ModuleType t : ModuleTypeRegistry.all()) {
            types.add(t.id());
            types.add(t.id().substring(t.id().indexOf(':') + 1));
        }
        JsonObject typeProp = str("Tipo de módulo");
        JsonArray anyOf = new JsonArray();
        JsonObject known = new JsonObject();
        known.add("enum", types);
        anyOf.add(known);
        anyOf.add(pattern(str("Módulo de un addon (espacio:nombre)"), "^[a-z0-9_.-]+:[a-z0-9_./-]+$"));
        typeProp.add("anyOf", anyOf);
        moduleProps.add("type", typeProp);
        for (ParamSpec p : ModuleType.COMMON) {
            moduleProps.add(p.name(), param(p));
        }
        module.add("properties", moduleProps);
        JsonArray allOf = new JsonArray();
        for (ModuleType t : ModuleTypeRegistry.all()) {
            JsonObject cond = new JsonObject();
            JsonObject ifObj = new JsonObject();
            JsonObject ifProps = new JsonObject();
            JsonObject typeEnum = new JsonObject();
            JsonArray names = new JsonArray();
            names.add(t.id());
            names.add(t.id().substring(t.id().indexOf(':') + 1));
            typeEnum.add("enum", names);
            ifProps.add("type", typeEnum);
            ifObj.add("properties", ifProps);
            cond.add("if", ifObj);
            JsonObject then = new JsonObject();
            then.addProperty("description", t.description());
            JsonObject thenProps = new JsonObject();
            JsonArray req = new JsonArray();
            for (ParamSpec p : t.params().values()) {
                thenProps.add(p.name(), param(p));
                if (p.required()) {
                    req.add(p.name());
                }
            }
            then.add("properties", thenProps);
            if (!req.isEmpty()) {
                then.add("required", req);
            }
            cond.add("then", then);
            allOf.add(cond);
        }
        module.add("allOf", allOf);
        definitions.add("module", module);
        root.add("definitions", definitions);
        return new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create().toJson(root) + "\n";
    }

    private static JsonObject param(ParamSpec p) {
        String desc = p.description() + (p.required() ? " (obligatorio)" : " (por defecto: " + p.defaultText() + ")");
        JsonObject s = new JsonObject();
        s.addProperty("description", desc);
        switch (p.kind()) {
            case NUMBER -> s.add("anyOf", anyOf(bounded(p), pattern(str("Variable o expresión"), "^.*\\$.*$")));
            case RANGE -> {
                JsonObject range = obj("Rango que se interpola");
                JsonObject rp = new JsonObject();
                rp.add("from", bounded(p));
                rp.add("to", bounded(p));
                range.add("properties", rp);
                JsonObject pair = array("[desde, hasta]", bounded(p));
                pair.addProperty("minItems", 2);
                pair.addProperty("maxItems", 2);
                s.add("anyOf", anyOf(bounded(p), pattern(str("Variable o expresión"), "^.*\\$.*$"), range, pair));
            }
            case DURATION -> s.add("anyOf", anyOf(number("Segundos"), pattern(str("Duración"), "^([0-9]+(\\.[0-9]+)?[tsmhd]?|permanent)$")));
            case COLOR -> {
                s.addProperty("type", "string");
                s.addProperty("pattern", "^(#[0-9A-Fa-f]{6}|\\$[a-z0-9_]+)$");
            }
            case BOOLEAN -> s.addProperty("type", "boolean");
            case STRING -> s.addProperty("type", "string");
            case IDENTIFIER -> {
                s.addProperty("type", "string");
                s.addProperty("pattern", "^([a-z0-9_.-]+:[a-z0-9_./-]+|\\$[a-z0-9_]+)$");
            }
            case CUTS -> {
                s.addProperty("type", "array");
                JsonObject item = new JsonObject();
                item.addProperty("type", "array");
                item.addProperty("minItems", 2);
                item.addProperty("maxItems", 2);
                s.add("items", item);
            }
            case MODULES -> {
                s.addProperty("type", "array");
                JsonObject ref = new JsonObject();
                ref.addProperty("$ref", "#/definitions/module");
                s.add("items", ref);
            }
            case OBJECT -> s.addProperty("type", "object");
            case LIST -> s.addProperty("type", "array");
        }
        return s;
    }

    private static JsonObject bounded(ParamSpec p) {
        JsonObject n = number("Número");
        if (!Double.isNaN(p.min())) {
            n.addProperty("minimum", p.min());
        }
        if (!Double.isNaN(p.max())) {
            n.addProperty("maximum", p.max());
        }
        return n;
    }

    private static JsonArray anyOf(JsonObject... options) {
        JsonArray a = new JsonArray();
        for (JsonObject o : options) {
            a.add(o);
        }
        return a;
    }

    private static JsonObject str(String d) {
        JsonObject o = new JsonObject();
        o.addProperty("type", "string");
        o.addProperty("description", d);
        return o;
    }

    private static JsonObject number(String d) {
        JsonObject o = new JsonObject();
        o.addProperty("type", "number");
        o.addProperty("description", d);
        return o;
    }

    private static JsonObject integer(String d) {
        JsonObject o = new JsonObject();
        o.addProperty("type", "integer");
        o.addProperty("description", d);
        return o;
    }

    private static JsonObject bool(String d) {
        JsonObject o = new JsonObject();
        o.addProperty("type", "boolean");
        o.addProperty("description", d);
        return o;
    }

    private static JsonObject obj(String d) {
        JsonObject o = new JsonObject();
        o.addProperty("type", "object");
        o.addProperty("description", d);
        return o;
    }

    private static JsonObject array(String d, JsonObject items) {
        JsonObject o = new JsonObject();
        o.addProperty("type", "array");
        o.addProperty("description", d);
        o.add("items", items);
        return o;
    }

    private static JsonObject duration(String d) {
        JsonObject o = new JsonObject();
        o.addProperty("description", d);
        o.add("anyOf", anyOf(number("Segundos"), pattern(str("Duración"), "^([0-9]+(\\.[0-9]+)?[tsmhd]?|permanent)$")));
        return o;
    }

    private static JsonObject enumOf(String d, String... values) {
        JsonObject o = str(d);
        JsonArray e = new JsonArray();
        for (String v : values) {
            e.add(v);
        }
        o.add("enum", e);
        return o;
    }

    private static JsonObject pattern(JsonObject o, String regex) {
        o.addProperty("pattern", regex);
        return o;
    }

    private static JsonObject withConst(JsonObject o, int value) {
        o.addProperty("const", value);
        return o;
    }

    // ------------------------------------------------------------------------------------------------ referencia

    static String moduleReference() {
        StringBuilder sb = new StringBuilder();
        sb.append("# Referencia de módulos\n\n");
        sb.append("> Generado a partir del catálogo de módulos (`BuiltinModules`). No editar a mano: ")
                .append("`./gradlew :26.3:test -PgenerateDocs`.\n\n");
        sb.append("Un evento combina módulos sobre una línea de tiempo. Cada módulo tiene un **tipo** y unos **parámetros**. ")
                .append("Todos admiten además los [parámetros comunes](#parámetros-comunes).\n\n");
        sb.append("**Lado:** S = servidor (efectos de juego, bloques), C = cliente (visuales y sonido), S+C = ambos.\n\n");
        sb.append("**Tipos de valor:** número (admite `$variable` y expresiones como `$radius * 0.5`), ")
                .append("rango (número, `[a, b]` o `{\"from\": a, \"to\": b}`, interpolado con la curva), ")
                .append("duración (`\"8.1s\"`, `\"20t\"`, `\"2m\"`, `\"3d\"`), color (`\"#RRGGBB\"` o `\"$nombre\"` de la paleta).\n\n");
        Map<String, List<ModuleType>> byCategory = new LinkedHashMap<>();
        for (ModuleType t : ModuleTypeRegistry.all()) {
            byCategory.computeIfAbsent(t.category(), c -> new java.util.ArrayList<>()).add(t);
        }
        sb.append("## Índice\n\n");
        for (Map.Entry<String, List<ModuleType>> e : byCategory.entrySet()) {
            sb.append("- **").append(capitalize(e.getKey())).append(":** ");
            List<String> links = e.getValue().stream().map(t -> "[`" + shortId(t) + "`](#" + shortId(t).replace("_", "_") + ")").toList();
            sb.append(String.join(" · ", links)).append('\n');
        }
        sb.append('\n');
        for (Map.Entry<String, List<ModuleType>> e : byCategory.entrySet()) {
            sb.append("## ").append(capitalize(e.getKey())).append("\n\n");
            for (ModuleType t : e.getValue()) {
                sb.append("### ").append(shortId(t)).append("\n\n");
                sb.append("`").append(t.id()).append("` · ").append(side(t)).append("\n\n");
                sb.append(t.description()).append("\n\n");
                if (!t.params().isEmpty()) {
                    sb.append("| Parámetro | Tipo | Por defecto | Descripción |\n|---|---|---|---|\n");
                    for (ParamSpec p : t.params().values()) {
                        sb.append("| `").append(p.name()).append("` | ").append(kind(p)).append(" | ")
                                .append(p.required() ? "**obligatorio**" : "`" + p.defaultText().replace("|", "\\|") + "`")
                                .append(" | ").append(p.description().replace("|", "\\|")).append(bounds(p)).append(" |\n");
                    }
                    sb.append('\n');
                }
            }
        }
        sb.append("## Parámetros comunes\n\n| Parámetro | Tipo | Por defecto | Descripción |\n|---|---|---|---|\n");
        for (ParamSpec p : ModuleType.COMMON) {
            sb.append("| `").append(p.name()).append("` | ").append(kind(p)).append(" | `").append(p.defaultText())
                    .append("` | ").append(p.description()).append(" |\n");
        }
        sb.append("\n---\n\nCreado por **TakumiStudios**.\n");
        return sb.toString();
    }

    private static String shortId(ModuleType t) {
        return t.id().startsWith("liveevents:") ? t.id().substring(11) : t.id();
    }

    private static String side(ModuleType t) {
        return switch (t.side()) {
            case SERVER -> "S";
            case CLIENT -> "C";
            case BOTH -> "S+C";
        };
    }

    private static String kind(ParamSpec p) {
        return switch (p.kind()) {
            case NUMBER -> "número";
            case RANGE -> "rango";
            case DURATION -> "duración";
            case COLOR -> "color";
            case BOOLEAN -> "booleano";
            case STRING -> "texto";
            case IDENTIFIER -> "id";
            case CUTS -> "cortes";
            case MODULES -> "módulos";
            case OBJECT -> "objeto";
            case LIST -> "lista";
        };
    }

    private static String bounds(ParamSpec p) {
        if (!p.hasBounds()) {
            return "";
        }
        return " (" + trim(p.min()) + "–" + trim(p.max()) + ")";
    }

    private static String trim(double v) {
        return Double.isNaN(v) ? "…" : v == Math.floor(v) ? String.valueOf((long) v) : String.valueOf(v);
    }

    private static String capitalize(String s) {
        return s.isEmpty() ? s : Character.toUpperCase(s.charAt(0)) + s.substring(1);
    }
}
