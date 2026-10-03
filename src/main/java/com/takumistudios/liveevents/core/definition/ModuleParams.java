package com.takumistudios.liveevents.core.definition;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import com.takumistudios.liveevents.core.expr.Expression;
import com.takumistudios.liveevents.core.expr.ExpressionException;
import com.takumistudios.liveevents.core.time.Durations;
import com.takumistudios.liveevents.core.timeline.Curve;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.OptionalInt;

/**
 * Parámetros de un módulo ya resueltos para un perfil y una instancia: paleta, variables y expresiones. Nunca lanza
 * excepciones al leer: un valor inválido devuelve el valor por defecto (la validación ya lo avisó al cargar).
 */
public final class ModuleParams {
    private final JsonObject json;
    private final Palette palette;
    private final Map<String, Double> variables;
    private final Map<String, Double> numberCache = new HashMap<>();

    public ModuleParams(JsonObject json, Palette palette, Map<String, Double> variables) {
        this.json = json;
        this.palette = palette;
        this.variables = variables;
    }

    /**
     * Mezcla los parámetros base con los del perfil ({@code "profiles": {"safe": {...}}}).
     */
    public static JsonObject forProfile(JsonObject base, Profile profile) {
        JsonObject merged = base.deepCopy();
        merged.remove("profiles");
        if (base.has("profiles") && base.get("profiles").isJsonObject()) {
            JsonObject profiles = base.getAsJsonObject("profiles");
            if (profiles.has(profile.id()) && profiles.get(profile.id()).isJsonObject()) {
                for (Map.Entry<String, JsonElement> e : profiles.getAsJsonObject(profile.id()).entrySet()) {
                    merged.add(e.getKey(), e.getValue().deepCopy());
                }
            }
        }
        return merged;
    }

    public JsonObject json() {
        return json;
    }

    public Palette palette() {
        return palette;
    }

    public boolean has(String name) {
        return json.has(name) && !json.get(name).isJsonNull();
    }

    public double number(String name, double fallback) {
        if (!has(name)) {
            return fallback;
        }
        Double cached = numberCache.get(name);
        if (cached != null) {
            return cached;
        }
        double value = toNumber(json.get(name), fallback);
        numberCache.put(name, value);
        return value;
    }

    public int integer(String name, int fallback) {
        return (int) Math.round(number(name, fallback));
    }

    /**
     * Valor de un rango en el instante {@code progress} (0–1) del módulo: admite número, {@code [a, b]} o
     * {@code {"from": a, "to": b}}, con la curva del módulo.
     */
    public double range(String name, double progress, double fallback) {
        if (!has(name)) {
            return fallback;
        }
        JsonElement e = json.get(name);
        double a, b;
        if (e.isJsonArray() && e.getAsJsonArray().size() == 2) {
            a = toNumber(e.getAsJsonArray().get(0), fallback);
            b = toNumber(e.getAsJsonArray().get(1), fallback);
        } else if (e.isJsonObject() && e.getAsJsonObject().has("from") && e.getAsJsonObject().has("to")) {
            a = toNumber(e.getAsJsonObject().get("from"), fallback);
            b = toNumber(e.getAsJsonObject().get("to"), fallback);
        } else {
            return number(name, fallback);
        }
        double t = curve().apply(progress);
        return a + (b - a) * t;
    }

    /** Para rangos usados como intervalo aleatorio ([2, 6] Hz): devuelve {min, max}. */
    public double[] bounds(String name, double fallback) {
        if (has(name) && json.get(name).isJsonArray() && json.getAsJsonArray(name).size() == 2) {
            JsonArray a = json.getAsJsonArray(name);
            double x = toNumber(a.get(0), fallback), y = toNumber(a.get(1), fallback);
            return new double[]{Math.min(x, y), Math.max(x, y)};
        }
        double v = number(name, fallback);
        return new double[]{v, v};
    }

    public Curve curve() {
        return Curve.parse(string("curve", "linear"), Curve.LINEAR);
    }

    public int color(String name, int fallback) {
        if (!has(name) || !json.get(name).isJsonPrimitive()) {
            return fallback;
        }
        OptionalInt c = palette.resolve(json.get(name).getAsString());
        return c.orElse(fallback);
    }

    public List<Integer> colors(String name, List<Integer> fallback) {
        if (!has(name) || !json.get(name).isJsonArray()) {
            return fallback;
        }
        List<Integer> out = new ArrayList<>();
        for (JsonElement e : json.getAsJsonArray(name)) {
            if (e.isJsonPrimitive()) {
                palette.resolve(e.getAsString()).ifPresent(out::add);
            }
        }
        return out.isEmpty() ? fallback : out;
    }

    public String string(String name, String fallback) {
        if (!has(name) || !json.get(name).isJsonPrimitive()) {
            return fallback;
        }
        return json.get(name).getAsString();
    }

    public boolean bool(String name, boolean fallback) {
        if (!has(name) || !json.get(name).isJsonPrimitive()) {
            return fallback;
        }
        JsonPrimitive p = json.getAsJsonPrimitive(name);
        return p.isBoolean() ? p.getAsBoolean() : Boolean.parseBoolean(p.getAsString());
    }

    /** Duración en ticks (con decimales). */
    public double ticks(String name, double fallback) {
        if (!has(name) || !json.get(name).isJsonPrimitive()) {
            return fallback;
        }
        try {
            JsonPrimitive p = json.getAsJsonPrimitive(name);
            if (p.isNumber()) {
                return p.getAsDouble() * Durations.TICKS_PER_SECOND; // número suelto = segundos
            }
            return Durations.parseTicksExact(p.getAsString());
        } catch (IllegalArgumentException e) {
            return fallback;
        }
    }

    public JsonArray array(String name) {
        return has(name) && json.get(name).isJsonArray() ? json.getAsJsonArray(name) : new JsonArray();
    }

    public JsonObject object(String name) {
        return has(name) && json.get(name).isJsonObject() ? json.getAsJsonObject(name) : new JsonObject();
    }

    /** Parámetros de un objeto anidado con la misma paleta y variables (p. ej. "collapse"). */
    public ModuleParams child(String name) {
        return new ModuleParams(object(name), palette, variables);
    }

    private double toNumber(JsonElement e, double fallback) {
        if (e == null || !e.isJsonPrimitive()) {
            return fallback;
        }
        JsonPrimitive p = e.getAsJsonPrimitive();
        if (p.isNumber()) {
            double v = p.getAsDouble();
            return Double.isFinite(v) ? v : fallback;
        }
        String s = p.getAsString().trim();
        try {
            if (s.startsWith("$") && s.substring(1).matches("[a-z0-9_]+")) {
                Double v = variables.get(s.substring(1));
                return v != null ? v : fallback;
            }
            if (Expression.looksLikeExpression(s)) {
                return Expression.compileCached(s).evaluate(variables);
            }
            return Double.parseDouble(s);
        } catch (ExpressionException | NumberFormatException ex) {
            return fallback;
        }
    }
}
