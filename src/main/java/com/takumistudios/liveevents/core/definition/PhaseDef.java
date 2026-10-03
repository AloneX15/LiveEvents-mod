package com.takumistudios.liveevents.core.definition;

import java.util.List;

/**
 * Una fase = un segmento del guion ("00:08 – 00:20 | El Latigazo Violento").
 *
 * @param title       título del segmento (texto o clave de traducción)
 * @param descSky     "El Cielo" del guion
 * @param descChaos   "El Caos" del guion
 */
public record PhaseDef(String id, double fromTicks, double toTicks, String title, String descSky, String descChaos,
                       List<ModuleSpec> modules) {
    public boolean activeAt(double eventTicks) {
        return eventTicks >= fromTicks && eventTicks < toTicks;
    }
}
