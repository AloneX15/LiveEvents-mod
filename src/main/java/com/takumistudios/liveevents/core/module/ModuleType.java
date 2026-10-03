package com.takumistudios.liveevents.core.module;

import com.takumistudios.liveevents.core.definition.Side;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Tipo de módulo registrable: id, lado, descripción y parámetros. Los oficiales están en {@link BuiltinModules}; los
 * addons registran los suyos con la API pública.
 */
public final class ModuleType {
    /** Parámetros comunes a todos los módulos. */
    public static final List<ParamSpec> COMMON = List.of(
            new ParamSpec("from", ParamKind.DURATION, "inicio de la fase", "Momento del evento en que empieza", Double.NaN, Double.NaN),
            new ParamSpec("to", ParamKind.DURATION, "fin de la fase", "Momento del evento en que termina", Double.NaN, Double.NaN),
            new ParamSpec("at", ParamKind.DURATION, "-", "Momento puntual (sustituye a from)", Double.NaN, Double.NaN),
            new ParamSpec("duration", ParamKind.DURATION, "-", "Duración a partir de at", Double.NaN, Double.NaN),
            new ParamSpec("curve", ParamKind.STRING, "linear", "Curva de los rangos: linear, ease_in, ease_out, ease_in_out, pulse, step", Double.NaN, Double.NaN),
            new ParamSpec("priority", ParamKind.NUMBER, "0", "Prioridad al mezclar con otros eventos", -100, 100),
            new ParamSpec("profiles", ParamKind.OBJECT, "-", "Parámetros distintos por perfil, p. ej. {\"safe\": {\"strength\": 0.2}}", Double.NaN, Double.NaN),
            new ParamSpec("requires_sky", ParamKind.BOOLEAN, "false", "Solo afecta a quien está bajo el cielo", Double.NaN, Double.NaN));

    private final String id;
    private final Side side;
    private final String category;
    private final String description;
    private final Map<String, ParamSpec> params;

    private ModuleType(String id, Side side, String category, String description, Map<String, ParamSpec> params) {
        this.id = id;
        this.side = side;
        this.category = category;
        this.description = description;
        this.params = Collections.unmodifiableMap(params);
    }

    public static Builder builder(String id, Side side) {
        return new Builder(id, side);
    }

    public String id() {
        return id;
    }

    public Side side() {
        return side;
    }

    public String category() {
        return category;
    }

    public String description() {
        return description;
    }

    /** Parámetros propios (sin los comunes). */
    public Map<String, ParamSpec> params() {
        return params;
    }

    public boolean knowsParam(String name) {
        if (name.equals("type")) {
            return true;
        }
        for (ParamSpec spec : COMMON) {
            if (spec.name().equals(name)) {
                return true;
            }
        }
        return params.containsKey(name);
    }

    /** Lista de parámetros completa para la documentación: propios + comunes. */
    public List<ParamSpec> allParams() {
        List<ParamSpec> all = new ArrayList<>(params.values());
        all.addAll(COMMON);
        return all;
    }

    public static final class Builder {
        private final String id;
        private final Side side;
        private String category = "otros";
        private String description = "";
        private final Map<String, ParamSpec> params = new LinkedHashMap<>();

        private Builder(String id, Side side) {
            this.id = id;
            this.side = side;
        }

        public Builder category(String category) {
            this.category = category;
            return this;
        }

        public Builder description(String description) {
            this.description = description;
            return this;
        }

        public Builder param(String name, ParamKind kind, String defaultText, String description) {
            return param(name, kind, defaultText, description, Double.NaN, Double.NaN);
        }

        public Builder param(String name, ParamKind kind, String defaultText, String description, double min, double max) {
            params.put(name, new ParamSpec(name, kind, defaultText, description, min, max));
            return this;
        }

        public ModuleType build() {
            return new ModuleType(id, side, category, description, new LinkedHashMap<>(params));
        }
    }
}
