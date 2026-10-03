package com.takumistudios.liveevents.util;

import net.minecraft.network.chat.Component;

/**
 * Textos de las definiciones: una clave de traducción ("event.liveevents.supernova") o un texto literal. Así los packs
 * de la comunidad pueden traer su propio lang o escribir el texto directamente.
 */
public final class Texts {
    private Texts() {
    }

    public static boolean isKey(String text) {
        return text != null && text.matches("[a-z0-9_]+(\\.[a-z0-9_\\-]+)+");
    }

    public static Component of(String text) {
        if (text == null) {
            return Component.empty();
        }
        String clipped = text.length() > 256 ? text.substring(0, 256) : text;
        return isKey(clipped) ? Component.translatable(clipped) : Component.literal(clipped);
    }
}
