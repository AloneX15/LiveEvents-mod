package com.takumistudios.liveevents.core.definition;

import com.google.gson.JsonObject;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Definición de evento ya resuelta (herencia aplicada) y validada. Inmutable; se comparte entre hilos.
 *
 * @param id                 "liveevents:supernova"
 * @param resolvedJson       JSON final tras extends/overrides; es lo que se sincroniza a los clientes
 * @param params             parámetros numéricos por defecto ($radius, $count…), modificables con param=valor
 * @param worldMode          vacío = el del servidor
 * @param defaultProfile     vacío = el del servidor
 * @param aftermathTicks     duración de las secuelas del cielo ({@link com.takumistudios.liveevents.core.time.Durations#PERMANENT})
 */
public record EventDefinition(
        String id,
        int formatVersion,
        String displayName,
        String description,
        Scope scope,
        double anchorAzimuth,
        double anchorAltitude,
        List<String> allowedDimensions,
        NightOverride nightOverride,
        boolean clearWeather,
        boolean lethal,
        Set<String> affects,
        Optional<WorldMode> worldMode,
        long cooldownTicks,
        Optional<Profile> defaultProfile,
        QueuePolicy queuePolicy,
        Palette palette,
        Map<String, Double> params,
        List<PhaseDef> phases,
        List<String> aftermath,
        long aftermathTicks,
        String source,
        JsonObject resolvedJson) {

    public static final Set<String> DEFAULT_AFFECTS = Set.of("players", "hostile");

    public double durationTicks() {
        double max = 0;
        for (PhaseDef p : phases) {
            max = Math.max(max, p.toTicks());
        }
        return max;
    }

    public String namespace() {
        int i = id.indexOf(':');
        return i < 0 ? "liveevents" : id.substring(0, i);
    }

    public String path() {
        int i = id.indexOf(':');
        return i < 0 ? id : id.substring(i + 1);
    }

    public List<ModuleSpec> allModules() {
        List<ModuleSpec> all = new ArrayList<>();
        for (PhaseDef p : phases) {
            all.addAll(p.modules());
        }
        return all;
    }

    public Optional<PhaseDef> phase(String phaseId) {
        return phases.stream().filter(p -> p.id().equals(phaseId)).findFirst();
    }

    public Optional<PhaseDef> phaseAt(double eventTicks) {
        for (PhaseDef p : phases) {
            if (p.activeAt(eventTicks)) {
                return Optional.of(p);
            }
        }
        return Optional.empty();
    }

    public int phaseIndexAt(double eventTicks) {
        for (int i = 0; i < phases.size(); i++) {
            if (phases.get(i).activeAt(eventTicks)) {
                return i;
            }
        }
        return -1;
    }

    /** ¿Tiene módulos que modifican bloques? (se usan para avisar en mundos hardcore y para el diario) */
    public boolean modifiesWorld() {
        for (ModuleSpec m : allModules()) {
            switch (m.shortType()) {
                case "block_transform", "melt", "crater", "fire_spread", "crack", "meteor_impacts", "seismic_wave" -> {
                    return true;
                }
                default -> {
                }
            }
        }
        return false;
    }

    /** ¿Tiene módulos que dañan? (aviso en hardcore). */
    public boolean dealsDamage() {
        for (ModuleSpec m : allModules()) {
            switch (m.shortType()) {
                case "damage_area", "ignite", "lightning_storm", "meteor_impacts", "gaze_check" -> {
                    return true;
                }
                default -> {
                }
            }
        }
        return false;
    }
}
