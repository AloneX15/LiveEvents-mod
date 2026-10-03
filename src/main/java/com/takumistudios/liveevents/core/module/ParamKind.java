package com.takumistudios.liveevents.core.module;

/** Tipo de un parámetro de módulo; decide cómo se valida y cómo aparece en el JSON Schema. */
public enum ParamKind {
    /** Número o expresión ("$radius * 0.5"). */
    NUMBER,
    /** Número o rango {"from": a, "to": b} / [a, b] que se interpola a lo largo del módulo. */
    RANGE,
    /** Duración "1.5s", "20t", "3d". */
    DURATION,
    /** Color "#RRGGBB" o "$nombre" de la paleta. */
    COLOR,
    BOOLEAN,
    STRING,
    /** Id con espacio de nombres ("minecraft:blindness"). */
    IDENTIFIER,
    /** Lista de [tiempo, color] (light_cut). */
    CUTS,
    /** Lista de módulos anidados (gaze_check.then). */
    MODULES,
    /** Objeto libre (p. ej. "collapse" de celestial_body). */
    OBJECT,
    /** Lista libre. */
    LIST
}
