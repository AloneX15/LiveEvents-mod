package com.takumistudios.liveevents.core.module;

/**
 * Descripción de un parámetro de módulo.
 *
 * @param name        nombre en el JSON
 * @param kind        tipo
 * @param defaultText valor por defecto legible (para la documentación), o "" si es obligatorio
 * @param description descripción breve (la wiki la publica tal cual)
 * @param min         mínimo permitido (solo NUMBER/RANGE), o NaN
 * @param max         máximo permitido (solo NUMBER/RANGE), o NaN
 */
public record ParamSpec(String name, ParamKind kind, String defaultText, String description, double min, double max) {
    public boolean required() {
        return defaultText.isEmpty();
    }

    public boolean hasBounds() {
        return !Double.isNaN(min) || !Double.isNaN(max);
    }
}
