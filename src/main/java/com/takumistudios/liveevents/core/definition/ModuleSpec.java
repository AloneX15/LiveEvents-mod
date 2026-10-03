package com.takumistudios.liveevents.core.definition;

import com.google.gson.JsonObject;

/**
 * Un módulo dentro de una fase, con su tramo de tiempo absoluto en ticks del evento.
 *
 * @param type      id con espacio de nombres ("liveevents:sky_tint")
 * @param side      dónde se ejecuta
 * @param phaseId   fase a la que pertenece
 * @param index     posición global (estable) dentro del evento; identifica el módulo en red y en errores
 * @param fromTicks inicio (ticks con decimales desde el inicio del evento)
 * @param toTicks   fin (exclusivo); un módulo puntual dura al menos un tick
 * @param raw       parámetros tal cual (incluye "profiles")
 */
public record ModuleSpec(String type, Side side, String phaseId, int index, double fromTicks, double toTicks, JsonObject raw) {
    public boolean activeAt(double eventTicks) {
        return eventTicks >= fromTicks && eventTicks < toTicks;
    }

    /** Progreso 0–1 dentro de su tramo. */
    public double progress(double eventTicks) {
        double span = toTicks - fromTicks;
        if (span <= 0) {
            return 1;
        }
        return Math.max(0, Math.min(1, (eventTicks - fromTicks) / span));
    }

    public double durationTicks() {
        return toTicks - fromTicks;
    }

    /** "sky_tint" sin el espacio de nombres oficial (para mensajes). */
    public String shortType() {
        return type.startsWith("liveevents:") ? type.substring("liveevents:".length()) : type;
    }
}
