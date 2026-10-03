package com.takumistudios.liveevents.core.module;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/** Registro de tipos de módulo (oficiales y de addons). Se congela al terminar la carga de mods. */
public final class ModuleTypeRegistry {
    private static final Map<String, ModuleType> TYPES = new LinkedHashMap<>();
    private static boolean frozen;

    static {
        for (ModuleType type : BuiltinModules.all()) {
            TYPES.put(type.id(), type);
        }
    }

    private ModuleTypeRegistry() {
    }

    public static synchronized void register(ModuleType type) {
        if (frozen) {
            throw new IllegalStateException("El registro de módulos ya está cerrado; registra " + type.id()
                    + " desde el entrypoint 'liveevents'");
        }
        String id = normalize(type.id());
        if (TYPES.containsKey(id)) {
            throw new IllegalArgumentException("Tipo de módulo duplicado: " + id);
        }
        TYPES.put(id, type);
    }

    public static synchronized void freeze() {
        frozen = true;
    }

    public static synchronized Optional<ModuleType> get(String id) {
        return Optional.ofNullable(TYPES.get(normalize(id)));
    }

    public static synchronized Collection<ModuleType> all() {
        return Collections.unmodifiableCollection(new ArrayList<>(TYPES.values()));
    }

    public static synchronized List<String> ids() {
        return new ArrayList<>(TYPES.keySet());
    }

    /** "sky_tint" → "liveevents:sky_tint"; los ids con espacio de nombres se dejan igual. */
    public static String normalize(String id) {
        return id.indexOf(':') < 0 ? "liveevents:" + id : id;
    }
}
