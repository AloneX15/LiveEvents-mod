package com.takumistudios.liveevents.client.compat;

import com.takumistudios.liveevents.client.screen.ConfigScreen;
import com.terraformersmc.modmenu.api.ConfigScreenFactory;
import com.terraformersmc.modmenu.api.ModMenuApi;

/** Integración opcional con Mod Menu: solo se carga si Mod Menu está instalado (entrypoint "modmenu"). */
public final class ModMenuCompat implements ModMenuApi {
    @Override
    public ConfigScreenFactory<?> getModConfigScreenFactory() {
        return ConfigScreen::new;
    }
}
