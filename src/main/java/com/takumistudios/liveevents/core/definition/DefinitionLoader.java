package com.takumistudios.liveevents.core.definition;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonPrimitive;
import com.google.gson.JsonSyntaxException;
import com.takumistudios.liveevents.core.expr.Expression;
import com.takumistudios.liveevents.core.expr.ExpressionException;
import com.takumistudios.liveevents.core.module.ModuleType;
import com.takumistudios.liveevents.core.module.ModuleTypeRegistry;
import com.takumistudios.liveevents.core.module.ParamSpec;
import com.takumistudios.liveevents.core.time.Durations;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.Set;
import java.util.TreeMap;

/**
 * Carga definiciones de evento: herencia ({@code extends}), {@code overrides}, migración de {@code format_version} y
 * validación con errores precisos. Un evento inválido se descarta con sus errores; nunca impide cargar los demás.
 */
public final class DefinitionLoader {
    public static final int CURRENT_FORMAT = 1;
    public static final int MAX_FILE_BYTES = 256 * 1024;
    public static final int MAX_EXTENDS_DEPTH = 8;
    public static final int MAX_PHASES = 64;
    public static final int MAX_MODULES_PER_PHASE = 128;
    public static final int MAX_CUTS_PER_SECOND = 3;
    private static final String ID_PATTERN = "[a-z0-9_.-]+:[a-z0-9_./-]+";

    /** Un archivo leído: JSON sin procesar y de dónde viene (para los mensajes). */
    public record RawDefinition(String id, JsonObject json, String source) {
    }

    public record Result(Map<String, EventDefinition> definitions, List<ValidationIssue> issues) {
        public long errorCount() {
            return issues.stream().filter(ValidationIssue::isError).count();
        }

        public List<ValidationIssue> issuesFor(String id) {
            return issues.stream().filter(i -> i.eventId().equals(id)).toList();
        }
    }

    private DefinitionLoader() {
    }

    /** Lee un archivo: comprueba tamaño y sintaxis. El id puede venir de la ruta (datapacks) o del JSON. */
    public static Optional<RawDefinition> read(String text, String fallbackId, String source, List<ValidationIssue> issues) {
        String label = fallbackId == null ? source : fallbackId;
        if (text.length() > MAX_FILE_BYTES) {
            issues.add(ValidationIssue.error(label, source, "", "el archivo supera el tamaño máximo (" + MAX_FILE_BYTES / 1024 + " KB)"));
            return Optional.empty();
        }
        JsonElement root;
        try {
            root = JsonParser.parseString(text);
        } catch (JsonSyntaxException | IllegalStateException e) {
            issues.add(ValidationIssue.error(label, source, "", "JSON no válido: " + rootMessage(e)));
            return Optional.empty();
        }
        if (!root.isJsonObject()) {
            issues.add(ValidationIssue.error(label, source, "", "la raíz debe ser un objeto JSON"));
            return Optional.empty();
        }
        JsonObject json = root.getAsJsonObject();
        String id = json.has("id") && json.get("id").isJsonPrimitive() ? json.get("id").getAsString() : fallbackId;
        if (id == null || !id.matches(ID_PATTERN)) {
            issues.add(ValidationIssue.error(label, source, "id", "falta \"id\" o no tiene el formato espacio:nombre (minúsculas)"));
            return Optional.empty();
        }
        return Optional.of(new RawDefinition(id, json, source));
    }

    /** Resuelve y valida todas las definiciones. */
    public static Result load(Map<String, RawDefinition> raws, List<ValidationIssue> previousIssues) {
        List<ValidationIssue> issues = new ArrayList<>(previousIssues);
        Map<String, JsonObject> resolved = new LinkedHashMap<>();
        for (String id : raws.keySet()) {
            resolve(id, raws, resolved, new LinkedHashSet<>(), issues);
        }
        Map<String, EventDefinition> defs = new TreeMap<>();
        for (Map.Entry<String, JsonObject> e : resolved.entrySet()) {
            RawDefinition raw = raws.get(e.getKey());
            List<ValidationIssue> local = new ArrayList<>();
            EventDefinition def = parse(e.getKey(), e.getValue(), raw.source(), local);
            issues.addAll(local);
            if (def != null && local.stream().noneMatch(ValidationIssue::isError)) {
                defs.put(def.id(), def);
            }
        }
        return new Result(Collections.unmodifiableMap(defs), Collections.unmodifiableList(issues));
    }

    // ------------------------------------------------------------------------------------------------ herencia

    private static JsonObject resolve(String id, Map<String, RawDefinition> raws, Map<String, JsonObject> resolved,
                                      LinkedHashSet<String> chain, List<ValidationIssue> issues) {
        if (resolved.containsKey(id)) {
            return resolved.get(id);
        }
        RawDefinition raw = raws.get(id);
        if (raw == null) {
            return null;
        }
        if (!chain.add(id)) {
            issues.add(ValidationIssue.error(id, raw.source(), "extends", "herencia circular: " + String.join(" → ", chain) + " → " + id));
            return null;
        }
        if (chain.size() > MAX_EXTENDS_DEPTH) {
            issues.add(ValidationIssue.error(id, raw.source(), "extends", "la herencia supera " + MAX_EXTENDS_DEPTH + " niveles"));
            return null;
        }
        JsonObject json = raw.json().deepCopy();
        if (json.has("extends")) {
            String parentId = json.get("extends").getAsString();
            if (!raws.containsKey(parentId)) {
                Optional<String> hint = Suggestions.closest(parentId, raws.keySet());
                issues.add(ValidationIssue.error(id, raw.source(), "extends", "el evento padre " + parentId + " no existe"
                        + hint.map(h -> ". ¿Querías decir " + h + "?").orElse("")));
                chain.remove(id);
                return null;
            }
            JsonObject parent = resolve(parentId, raws, resolved, chain, issues);
            if (parent == null) {
                chain.remove(id);
                return null; // el error ya está en el padre (o es circular)
            }
            json = merge(parent, json);
            if (json.has("overrides")) {
                applyOverrides(id, raw.source(), json, json.getAsJsonObject("overrides"), issues);
            }
        }
        json.remove("overrides");
        json.remove("extends");
        json.addProperty("id", id);
        chain.remove(id);
        resolved.put(id, json);
        return json;
    }

    /** El hijo sustituye los campos del padre; palette y params se mezclan clave a clave. */
    static JsonObject merge(JsonObject parent, JsonObject child) {
        JsonObject out = parent.deepCopy();
        for (Map.Entry<String, JsonElement> e : child.entrySet()) {
            String key = e.getKey();
            if ((key.equals("palette") || key.equals("params")) && e.getValue().isJsonObject() && out.has(key) && out.get(key).isJsonObject()) {
                JsonObject merged = out.getAsJsonObject(key);
                for (Map.Entry<String, JsonElement> c : e.getValue().getAsJsonObject().entrySet()) {
                    merged.add(c.getKey(), c.getValue().deepCopy());
                }
            } else {
                out.add(key, e.getValue().deepCopy());
            }
        }
        return out;
    }

    /**
     * Rutas {@code fase.módulo.parámetro} (módulo = tipo, opcionalmente con #n para la n-ésima aparición) o rutas de
     * primer nivel como {@code aftermath.duration}.
     */
    static void applyOverrides(String id, String source, JsonObject json, JsonObject overrides, List<ValidationIssue> issues) {
        for (Map.Entry<String, JsonElement> e : overrides.entrySet()) {
            String path = e.getKey();
            String[] parts = path.split("\\.");
            boolean ok = false;
            if (parts.length >= 3 && json.has("phases") && json.get("phases").isJsonArray()) {
                for (JsonElement p : json.getAsJsonArray("phases")) {
                    JsonObject phase = p.getAsJsonObject();
                    if (!phase.has("id") || !phase.get("id").getAsString().equals(parts[0])) {
                        continue;
                    }
                    JsonObject module = findModule(phase, parts[1]);
                    if (module != null) {
                        setPath(module, parts, 2, e.getValue());
                        ok = true;
                    }
                }
            }
            if (!ok && parts.length >= 1) {
                JsonObject target = json;
                for (int i = 0; i < parts.length - 1 && target != null; i++) {
                    target = target.has(parts[i]) && target.get(parts[i]).isJsonObject() ? target.getAsJsonObject(parts[i]) : null;
                }
                if (target != null && (parts.length == 1 || target.has(parts[parts.length - 1]) || parts.length == 2)) {
                    target.add(parts[parts.length - 1], e.getValue().deepCopy());
                    ok = true;
                }
            }
            if (!ok) {
                issues.add(ValidationIssue.error(id, source, "overrides." + path, "la ruta no existe en el evento padre"));
            }
        }
    }

    private static JsonObject findModule(JsonObject phase, String selector) {
        if (!phase.has("modules") || !phase.get("modules").isJsonArray()) {
            return null;
        }
        String type = selector;
        int wanted = 0;
        int hash = selector.indexOf('#');
        if (hash > 0) {
            type = selector.substring(0, hash);
            try {
                wanted = Integer.parseInt(selector.substring(hash + 1));
            } catch (NumberFormatException e) {
                return null;
            }
        }
        String normalized = ModuleTypeRegistry.normalize(type);
        int seen = 0;
        for (JsonElement m : phase.getAsJsonArray("modules")) {
            if (m.isJsonObject() && m.getAsJsonObject().has("type")
                    && ModuleTypeRegistry.normalize(m.getAsJsonObject().get("type").getAsString()).equals(normalized)) {
                if (seen++ == wanted) {
                    return m.getAsJsonObject();
                }
            }
        }
        return null;
    }

    private static void setPath(JsonObject target, String[] parts, int from, JsonElement value) {
        JsonObject cur = target;
        for (int i = from; i < parts.length - 1; i++) {
            if (!cur.has(parts[i]) || !cur.get(parts[i]).isJsonObject()) {
                cur.add(parts[i], new JsonObject());
            }
            cur = cur.getAsJsonObject(parts[i]);
        }
        cur.add(parts[parts.length - 1], value.deepCopy());
    }

    // ------------------------------------------------------------------------------------------------ parseo

    /**
     * Lee una definición ya resuelta (la que el servidor sincroniza al cliente). Devuelve vacío si tiene errores; el
     * cliente nunca falla por un JSON inválido.
     */
    public static Optional<EventDefinition> parseResolved(JsonObject json, String source) {
        try {
            String id = json.has("id") ? json.get("id").getAsString() : "";
            if (!id.matches(ID_PATTERN)) {
                return Optional.empty();
            }
            List<ValidationIssue> issues = new ArrayList<>();
            EventDefinition def = parse(id, json, source, issues);
            return def == null || issues.stream().anyMatch(ValidationIssue::isError) ? Optional.empty() : Optional.of(def);
        } catch (RuntimeException e) {
            return Optional.empty();
        }
    }

    static EventDefinition parse(String id, JsonObject json, String source, List<ValidationIssue> issues) {
        Ctx ctx = new Ctx(id, source, issues);

        int format = json.has("format_version") && json.get("format_version").isJsonPrimitive()
                ? json.get("format_version").getAsInt() : -1;
        if (format < 0) {
            ctx.error("format_version", "falta \"format_version\" (usa " + CURRENT_FORMAT + ")");
            return null;
        }
        if (format > CURRENT_FORMAT) {
            ctx.error("format_version", "este evento necesita una versión más nueva de LiveEvents (formato " + format + ")");
            return null;
        }
        // Migraciones futuras: if (format < 2) { migrar(json); }

        String displayName = str(json, "display_name", id);
        String description = "";
        if (json.has("description") && json.get("description").isJsonPrimitive()) {
            description = json.get("description").getAsString();
        }
        Scope scope = Scope.parse(str(json, "scope", "localized"), null);
        if (scope == null) {
            ctx.error("scope", "debe ser celestial, localized o regional");
            scope = Scope.LOCALIZED;
        }
        double azimuth = 160, altitude = 55;
        if (json.has("anchor") && json.get("anchor").isJsonObject()) {
            JsonObject a = json.getAsJsonObject("anchor");
            azimuth = num(a, "azimuth", azimuth);
            altitude = num(a, "altitude", altitude);
            if (altitude < 0 || altitude > 90) {
                ctx.warn("anchor.altitude", "la altura debe estar entre 0 y 90; se ajusta");
                altitude = Math.max(0, Math.min(90, altitude));
            }
        }

        List<String> dims = new ArrayList<>();
        if (json.has("allowed_dimensions") && json.get("allowed_dimensions").isJsonArray()) {
            for (JsonElement d : json.getAsJsonArray("allowed_dimensions")) {
                String dim = d.getAsString();
                if (!dim.matches(ID_PATTERN)) {
                    ctx.error("allowed_dimensions", "dimensión no válida: " + dim);
                }
                dims.add(dim);
            }
        } else {
            dims.add("minecraft:overworld");
        }
        if (scope == Scope.CELESTIAL && dims.contains("minecraft:the_nether")) {
            ctx.warn("allowed_dimensions", "el Nether no tiene cielo: los eventos celestes no se pueden lanzar ahí");
            dims.remove("minecraft:the_nether");
        }

        NightOverride night = NightOverride.parse(str(json, "night_override", "none"), null);
        if (night == null) {
            ctx.error("night_override", "debe ser none, visual o require");
            night = NightOverride.NONE;
        }
        boolean clearWeather = bool(json, "clear_weather", false);
        boolean lethal = bool(json, "lethal", true);

        Set<String> affects = new HashSet<>();
        if (json.has("affects") && json.get("affects").isJsonArray()) {
            for (JsonElement a : json.getAsJsonArray("affects")) {
                String v = a.getAsString();
                if (!Set.of("players", "hostile", "passive", "pets", "named").contains(v)) {
                    ctx.error("affects", "valor desconocido: " + v + " (players, hostile, passive, pets, named)");
                }
                affects.add(v);
            }
        } else {
            affects.addAll(EventDefinition.DEFAULT_AFFECTS);
        }

        Optional<WorldMode> worldMode = Optional.empty();
        if (json.has("world_mode")) {
            worldMode = WorldMode.parse(json.get("world_mode").getAsString());
            if (worldMode.isEmpty()) {
                ctx.error("world_mode", "debe ser permanent, revert_on_end o revert_manual");
            }
        }
        long cooldown = 0;
        if (json.has("cooldown")) {
            cooldown = ticks(ctx, "cooldown", json.get("cooldown"), 0);
        }
        Optional<Profile> defaultProfile = Optional.empty();
        if (json.has("default_profile")) {
            defaultProfile = Profile.parse(json.get("default_profile").getAsString());
            if (defaultProfile.isEmpty()) {
                ctx.error("default_profile", "debe ser cinematic, gameplay o safe");
            }
        }
        QueuePolicy queue = QueuePolicy.parse(str(json, "queue_policy", "reject"), QueuePolicy.REJECT);

        Map<String, Integer> colors = new LinkedHashMap<>();
        if (json.has("palette") && json.get("palette").isJsonObject()) {
            for (Map.Entry<String, JsonElement> c : json.getAsJsonObject("palette").entrySet()) {
                OptionalInt rgb = Palette.parseHex(c.getValue().getAsString());
                if (rgb.isEmpty()) {
                    ctx.error("palette." + c.getKey(), "color no válido (usa #RRGGBB): " + c.getValue().getAsString());
                } else {
                    colors.put(c.getKey(), rgb.getAsInt());
                }
            }
        }
        Palette palette = new Palette(colors);

        Map<String, Double> params = new LinkedHashMap<>();
        params.put("radius", scope == Scope.REGIONAL ? 160.0 : 128.0);
        if (json.has("params") && json.get("params").isJsonObject()) {
            for (Map.Entry<String, JsonElement> p : json.getAsJsonObject("params").entrySet()) {
                if (!p.getKey().matches("[a-z0-9_]+")) {
                    ctx.error("params." + p.getKey(), "nombre de parámetro no válido (minúsculas, números y _)");
                    continue;
                }
                if (!p.getValue().isJsonPrimitive() || !p.getValue().getAsJsonPrimitive().isNumber()) {
                    ctx.error("params." + p.getKey(), "los parámetros deben ser números");
                    continue;
                }
                params.put(p.getKey(), p.getValue().getAsDouble());
            }
        }

        List<PhaseDef> phases = new ArrayList<>();
        if (!json.has("phases") || !json.get("phases").isJsonArray() || json.getAsJsonArray("phases").isEmpty()) {
            ctx.error("phases", "el evento necesita al menos una fase");
            return null;
        }
        JsonArray phaseArray = json.getAsJsonArray("phases");
        if (phaseArray.size() > MAX_PHASES) {
            ctx.error("phases", "demasiadas fases (máximo " + MAX_PHASES + ")");
            return null;
        }
        Set<String> phaseIds = new HashSet<>();
        int moduleIndex = 0;
        double lastEnd = 0;
        for (int i = 0; i < phaseArray.size(); i++) {
            String ppath = "phases[" + i + "]";
            if (!phaseArray.get(i).isJsonObject()) {
                ctx.error(ppath, "cada fase debe ser un objeto");
                continue;
            }
            JsonObject p = phaseArray.get(i).getAsJsonObject();
            String pid = str(p, "id", "phase" + i);
            if (!phaseIds.add(pid)) {
                ctx.error(ppath + ".id", "id de fase repetido: " + pid);
            }
            double from = ticksExact(ctx, ppath + ".from", p.get("from"), lastEnd);
            double to = ticksExact(ctx, ppath + ".to", p.get("to"), -1);
            if (to <= from) {
                ctx.error(ppath, "\"to\" debe ser mayor que \"from\"");
                continue;
            }
            if (to > Durations.MAX_TICKS) {
                ctx.error(ppath + ".to", "un evento no puede durar más de 24 horas");
                continue;
            }
            if (from < lastEnd - 0.001) {
                ctx.warn(ppath + ".from", "la fase empieza antes de que termine la anterior");
            }
            lastEnd = Math.max(lastEnd, to);
            String title = str(p, "title", pid);
            String sky = "", chaos = "";
            if (p.has("description") && p.get("description").isJsonObject()) {
                sky = str(p.getAsJsonObject("description"), "sky", "");
                chaos = str(p.getAsJsonObject("description"), "chaos", "");
            }
            List<ModuleSpec> modules = new ArrayList<>();
            JsonArray mods = p.has("modules") && p.get("modules").isJsonArray() ? p.getAsJsonArray("modules") : new JsonArray();
            if (mods.size() > MAX_MODULES_PER_PHASE) {
                ctx.error(ppath + ".modules", "demasiados módulos en una fase (máximo " + MAX_MODULES_PER_PHASE + ")");
                continue;
            }
            for (int m = 0; m < mods.size(); m++) {
                String mpath = ppath + ".modules[" + m + "]";
                ModuleSpec spec = parseModule(ctx, mpath, mods.get(m), pid, moduleIndex, from, to, palette, params);
                if (spec != null) {
                    modules.add(spec);
                    moduleIndex++;
                }
            }
            phases.add(new PhaseDef(pid, from, to, title, sky, chaos, List.copyOf(modules)));
        }

        List<String> aftermath = new ArrayList<>();
        long aftermathTicks = 3 * Durations.TICKS_PER_GAME_DAY;
        if (json.has("aftermath") && json.get("aftermath").isJsonObject()) {
            JsonObject a = json.getAsJsonObject("aftermath");
            if (a.has("persistent") && a.get("persistent").isJsonArray()) {
                for (JsonElement e : a.getAsJsonArray("persistent")) {
                    String kind = e.getAsString();
                    if (!Set.of("nebula", "pulsar").contains(kind)) {
                        ctx.error("aftermath.persistent", "secuela desconocida: " + kind + " (nebula, pulsar)");
                    } else {
                        aftermath.add(kind);
                    }
                }
            }
            if (a.has("duration")) {
                aftermathTicks = ticks(ctx, "aftermath.duration", a.get("duration"), aftermathTicks);
            }
        }

        return new EventDefinition(id, format, displayName, description, scope, azimuth, altitude, List.copyOf(dims),
                night, clearWeather, lethal, Set.copyOf(affects), worldMode, cooldown, defaultProfile, queue, palette,
                Collections.unmodifiableMap(params), List.copyOf(phases), List.copyOf(aftermath), aftermathTicks,
                source, json);
    }

    private static ModuleSpec parseModule(Ctx ctx, String path, JsonElement element, String phaseId, int index,
                                          double phaseFrom, double phaseTo, Palette palette, Map<String, Double> params) {
        if (!element.isJsonObject()) {
            ctx.error(path, "cada módulo debe ser un objeto");
            return null;
        }
        JsonObject m = element.getAsJsonObject();
        if (!m.has("type")) {
            ctx.error(path + ".type", "falta \"type\"");
            return null;
        }
        String rawType = m.get("type").getAsString();
        String type = ModuleTypeRegistry.normalize(rawType);
        Optional<ModuleType> moduleType = ModuleTypeRegistry.get(type);
        if (moduleType.isEmpty()) {
            List<String> shortIds = ModuleTypeRegistry.ids().stream()
                    .map(s -> s.startsWith("liveevents:") ? s.substring(11) : s).toList();
            Optional<String> hint = Suggestions.closest(rawType, shortIds);
            ctx.error(path + ".type", "tipo de módulo desconocido: " + rawType + hint.map(h -> ". ¿Querías decir " + h + "?").orElse(""));
            return null;
        }
        ModuleType mt = moduleType.get();

        double from = phaseFrom, to = phaseTo;
        if (m.has("at")) {
            from = ticksExact(ctx, path + ".at", m.get("at"), phaseFrom);
            // En status_effect "duration" es la del efecto, no la del módulo: entonces el módulo es puntual
            boolean ownDuration = mt.params().containsKey("duration");
            to = from + (m.has("duration") && !ownDuration ? ticksExact(ctx, path + ".duration", m.get("duration"), 1) : 1);
        } else {
            if (m.has("from")) {
                from = ticksExact(ctx, path + ".from", m.get("from"), phaseFrom);
            }
            if (m.has("to")) {
                to = ticksExact(ctx, path + ".to", m.get("to"), phaseTo);
            }
        }
        if (to <= from) {
            ctx.error(path, "el módulo termina antes de empezar");
            return null;
        }

        validateParams(ctx, path, m, mt, palette, params);
        for (String profile : List.of("safe", "gameplay", "cinematic")) {
            if (m.has("profiles") && m.get("profiles").isJsonObject() && m.getAsJsonObject("profiles").has(profile)) {
                JsonElement pe = m.getAsJsonObject("profiles").get(profile);
                if (pe.isJsonObject()) {
                    validateParams(ctx, path + ".profiles." + profile, pe.getAsJsonObject(), mt, palette, params);
                }
            }
        }
        if (m.has("profiles") && m.get("profiles").isJsonObject()) {
            for (String key : m.getAsJsonObject("profiles").keySet()) {
                if (Profile.parse(key).isEmpty()) {
                    ctx.error(path + ".profiles." + key, "perfil desconocido (cinematic, gameplay, safe)");
                }
            }
        }
        return new ModuleSpec(type, mt.side(), phaseId, index, from, to, m.deepCopy());
    }

    private static void validateParams(Ctx ctx, String path, JsonObject m, ModuleType type, Palette palette, Map<String, Double> params) {
        for (Map.Entry<String, JsonElement> e : m.entrySet()) {
            String name = e.getKey();
            if (!type.knowsParam(name)) {
                Optional<String> hint = Suggestions.closest(name, type.params().keySet());
                ctx.warn(path + "." + name, "parámetro desconocido para " + type.id() + hint.map(h -> ". ¿Querías decir " + h + "?").orElse(""));
                continue;
            }
            ParamSpec spec = type.params().get(name);
            if (spec == null) {
                continue;
            }
            JsonElement v = e.getValue();
            String ppath = path + "." + name;
            switch (spec.kind()) {
                case COLOR -> checkColor(ctx, ppath, v, palette);
                case NUMBER -> checkNumber(ctx, ppath, v, spec, params);
                case RANGE -> {
                    if (v.isJsonArray()) {
                        for (JsonElement x : v.getAsJsonArray()) {
                            checkNumber(ctx, ppath, x, spec, params);
                        }
                    } else if (v.isJsonObject()) {
                        checkNumber(ctx, ppath + ".from", v.getAsJsonObject().get("from"), spec, params);
                        checkNumber(ctx, ppath + ".to", v.getAsJsonObject().get("to"), spec, params);
                    } else {
                        checkNumber(ctx, ppath, v, spec, params);
                    }
                }
                case DURATION -> ticksExact(ctx, ppath, v, 0);
                case IDENTIFIER -> {
                    if (!v.isJsonPrimitive() || !v.getAsString().matches(ID_PATTERN) && !v.getAsString().startsWith("$")) {
                        ctx.error(ppath, "se esperaba un id espacio:nombre");
                    }
                }
                case CUTS -> checkCuts(ctx, ppath, v, palette);
                case MODULES -> {
                    if (!v.isJsonArray()) {
                        ctx.error(ppath, "se esperaba una lista de módulos");
                    } else {
                        int i = 0;
                        for (JsonElement sub : v.getAsJsonArray()) {
                            String sp = ppath + "[" + i++ + "]";
                            if (!sub.isJsonObject() || !sub.getAsJsonObject().has("type")) {
                                ctx.error(sp, "cada módulo necesita \"type\"");
                                continue;
                            }
                            Optional<ModuleType> st = ModuleTypeRegistry.get(sub.getAsJsonObject().get("type").getAsString());
                            if (st.isEmpty()) {
                                ctx.error(sp + ".type", "tipo de módulo desconocido");
                            } else if (!st.get().side().onServer()) {
                                ctx.error(sp + ".type", "dentro de " + type.id() + " solo se admiten módulos de servidor");
                            } else {
                                validateParams(ctx, sp, sub.getAsJsonObject(), st.get(), palette, params);
                            }
                        }
                    }
                }
                case LIST -> {
                    if (!v.isJsonArray()) {
                        ctx.error(ppath, "se esperaba una lista");
                    } else if (name.equals("colors")) {
                        for (JsonElement c : v.getAsJsonArray()) {
                            checkColor(ctx, ppath, c, palette);
                        }
                    }
                }
                case BOOLEAN -> {
                    if (!v.isJsonPrimitive() || !v.getAsJsonPrimitive().isBoolean()) {
                        ctx.error(ppath, "se esperaba true o false");
                    }
                }
                case OBJECT -> {
                    if (!v.isJsonObject()) {
                        ctx.error(ppath, "se esperaba un objeto");
                    }
                }
                case STRING -> {
                    if (!v.isJsonPrimitive()) {
                        ctx.error(ppath, "se esperaba un texto");
                    }
                }
            }
        }
        for (ParamSpec spec : type.params().values()) {
            if (spec.required() && !m.has(spec.name()) && !path.contains(".profiles.")) {
                ctx.error(path + "." + spec.name(), "falta el parámetro obligatorio \"" + spec.name() + "\"");
            }
        }
    }

    private static void checkColor(Ctx ctx, String path, JsonElement v, Palette palette) {
        if (v == null || !v.isJsonPrimitive()) {
            ctx.error(path, "se esperaba un color");
            return;
        }
        String s = v.getAsString();
        if (s.startsWith("$")) {
            if (palette.get(s.substring(1)).isEmpty()) {
                Optional<String> hint = palette.suggest(s.substring(1));
                ctx.error(path, s + " no existe en la paleta" + hint.map(h -> ". ¿Querías decir $" + h + "?").orElse(""));
            }
        } else if (Palette.parseHex(s).isEmpty()) {
            ctx.error(path, "color no válido (usa #RRGGBB o $nombre): " + s);
        }
    }

    private static void checkNumber(Ctx ctx, String path, JsonElement v, ParamSpec spec, Map<String, Double> params) {
        if (v == null || !v.isJsonPrimitive()) {
            ctx.error(path, "se esperaba un número");
            return;
        }
        JsonPrimitive p = v.getAsJsonPrimitive();
        double value;
        if (p.isNumber()) {
            value = p.getAsDouble();
        } else {
            String s = p.getAsString();
            try {
                if (s.startsWith("$") && s.substring(1).matches("[a-z0-9_]+")) {
                    if (!params.containsKey(s.substring(1))) {
                        Optional<String> hint = Suggestions.closest(s.substring(1), params.keySet());
                        ctx.error(path, "variable desconocida " + s + hint.map(h -> ". ¿Querías decir $" + h + "?").orElse(""));
                        return;
                    }
                    value = params.get(s.substring(1));
                } else if (Expression.looksLikeExpression(s)) {
                    value = Expression.compile(s).evaluate(params);
                } else {
                    value = Double.parseDouble(s);
                }
            } catch (ExpressionException e) {
                ctx.error(path, "expresión no válida: " + e.getMessage());
                return;
            } catch (NumberFormatException e) {
                ctx.error(path, "se esperaba un número: " + s);
                return;
            }
        }
        if (!Double.isNaN(spec.min()) && value < spec.min() || !Double.isNaN(spec.max()) && value > spec.max()) {
            ctx.warn(path, String.format(Locale.ROOT, "%s fuera del rango permitido [%s, %s]; se recortará",
                    trim(value), trim(spec.min()), trim(spec.max())));
        }
    }

    private static void checkCuts(Ctx ctx, String path, JsonElement v, Palette palette) {
        if (!v.isJsonArray()) {
            ctx.error(path, "se esperaba una lista de [tiempo, color]");
            return;
        }
        List<Double> times = new ArrayList<>();
        int i = 0;
        for (JsonElement cut : v.getAsJsonArray()) {
            String cp = path + "[" + i++ + "]";
            if (!cut.isJsonArray() || cut.getAsJsonArray().size() != 2) {
                ctx.error(cp, "cada corte es [tiempo, color]");
                continue;
            }
            times.add(ticksExact(ctx, cp + "[0]", cut.getAsJsonArray().get(0), 0));
            checkColor(ctx, cp + "[1]", cut.getAsJsonArray().get(1), palette);
        }
        Collections.sort(times);
        for (int a = 0; a < times.size(); a++) {
            int inWindow = 1;
            for (int b = a + 1; b < times.size() && times.get(b) - times.get(a) < Durations.TICKS_PER_SECOND; b++) {
                inWindow++;
            }
            if (inWindow > MAX_CUTS_PER_SECOND) {
                ctx.warn(path, "más de " + MAX_CUTS_PER_SECOND + " cortes en un segundo: el cliente convertirá los sobrantes en fundidos (regla de destellos)");
                break;
            }
        }
    }

    // ------------------------------------------------------------------------------------------------ utilidades

    private static double ticksExact(Ctx ctx, String path, JsonElement v, double fallback) {
        if (v == null || v.isJsonNull()) {
            if (fallback < 0) {
                ctx.error(path, "falta la duración");
                return 0;
            }
            return fallback;
        }
        try {
            if (v.isJsonPrimitive() && v.getAsJsonPrimitive().isNumber()) {
                return v.getAsDouble() * Durations.TICKS_PER_SECOND;
            }
            double t = Durations.parseTicksExact(v.getAsString());
            return t == Durations.PERMANENT ? Durations.MAX_TICKS : t;
        } catch (IllegalArgumentException | UnsupportedOperationException | IllegalStateException e) {
            ctx.error(path, "duración no válida (ej. \"8.1s\", \"20t\", \"2m\"): " + v);
            return Math.max(0, fallback);
        }
    }

    private static long ticks(Ctx ctx, String path, JsonElement v, long fallback) {
        try {
            if (v.isJsonPrimitive() && v.getAsJsonPrimitive().isNumber()) {
                return Math.round(v.getAsDouble() * Durations.TICKS_PER_SECOND);
            }
            return Durations.parseTicks(v.getAsString());
        } catch (IllegalArgumentException | UnsupportedOperationException | IllegalStateException e) {
            ctx.error(path, "duración no válida: " + v);
            return fallback;
        }
    }

    private static String str(JsonObject o, String key, String fallback) {
        return o.has(key) && o.get(key).isJsonPrimitive() ? o.get(key).getAsString() : fallback;
    }

    private static double num(JsonObject o, String key, double fallback) {
        return o.has(key) && o.get(key).isJsonPrimitive() && o.get(key).getAsJsonPrimitive().isNumber() ? o.get(key).getAsDouble() : fallback;
    }

    private static boolean bool(JsonObject o, String key, boolean fallback) {
        return o.has(key) && o.get(key).isJsonPrimitive() && o.get(key).getAsJsonPrimitive().isBoolean() ? o.get(key).getAsBoolean() : fallback;
    }

    private static String trim(double v) {
        return v == Math.floor(v) && Math.abs(v) < 1e9 ? String.valueOf((long) v) : String.valueOf(v);
    }

    private static String rootMessage(Throwable t) {
        Throwable c = t;
        while (c.getCause() != null) {
            c = c.getCause();
        }
        return c.getMessage() == null ? c.getClass().getSimpleName() : c.getMessage();
    }

    private record Ctx(String id, String source, List<ValidationIssue> issues) {
        void error(String path, String message) {
            issues.add(ValidationIssue.error(id, source, path, message));
        }

        void warn(String path, String message) {
            issues.add(ValidationIssue.warning(id, source, path, message));
        }
    }
}
