package com.takumistudios.liveevents.client.api;

import com.takumistudios.liveevents.client.visual.ClientVisual;
import com.takumistudios.liveevents.client.visual.VisualDirector;

/**
 * API de cliente para addons: registrar la parte visual de un módulo nuevo. Llamar desde el entrypoint de cliente del
 * addon. El visual hereda automáticamente LOD, presupuesto, perfiles y accesibilidad.
 */
public final class LiveEventsClientApi {
    private LiveEventsClientApi() {
    }

    public static void registerVisual(String moduleType, ClientVisual visual) {
        VisualDirector.register(moduleType, visual);
    }
}
