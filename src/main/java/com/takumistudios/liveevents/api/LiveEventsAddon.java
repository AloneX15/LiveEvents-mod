package com.takumistudios.liveevents.api;

import com.takumistudios.liveevents.api.module.ServerModule;
import com.takumistudios.liveevents.core.definition.ModuleSpec;
import com.takumistudios.liveevents.core.module.ModuleType;

import java.util.function.Function;

/**
 * Entrypoint {@code "liveevents"} para addons (§20.6). En el {@code fabric.mod.json} del addon:
 * <pre>{@code
 * "entrypoints": { "liveevents": ["com.ejemplo.MiAddon"] },
 * "depends": { "liveevents": ">=1.0.0" }
 * }</pre>
 * Los módulos registrados heredan LOD, presupuesto, perfiles y accesibilidad, y se usan en JSON igual que los
 * oficiales: {@code {"type": "miaddon:tornado_de_fuego"}}.
 */
public interface LiveEventsAddon {
    void registerModules(Registrar registrar);

    interface Registrar {
        /**
         * Registra un tipo de módulo. Para módulos de cliente, registrar además su visual con
         * {@code LiveEventsClientApi.registerVisual} desde el entrypoint de cliente del addon.
         *
         * @param serverFactory crea el comportamiento de servidor (null para módulos solo de cliente)
         */
        void registerModule(ModuleType type, Function<ModuleSpec, ServerModule> serverFactory);
    }
}
