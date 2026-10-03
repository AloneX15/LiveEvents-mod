package com.takumistudios.liveevents.server.modules;

import net.minecraft.server.level.ServerLevel;

/** Acceso desde el director a utilidades internas de los módulos. */
public final class ModuleHooks {
    private ModuleHooks() {
    }

    /** Ejecuta un comando vanilla interno (clima) en la dimensión, sin salida en el chat. */
    public static void runCommand(ServerLevel level, String command) {
        GameModules.runCommand(level, command);
    }

    public static void clearLightning(int instanceId) {
        GameModules.LightningCounter.clear(instanceId);
    }
}
