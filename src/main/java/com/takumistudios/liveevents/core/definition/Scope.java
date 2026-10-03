package com.takumistudios.liveevents.core.definition;

import java.util.Locale;

/** Ámbito de un evento: toda la dimensión, un centro con radio o una región (epicentro + radio grande). */
public enum Scope {
    CELESTIAL,
    LOCALIZED,
    REGIONAL;

    public String id() {
        return name().toLowerCase(Locale.ROOT);
    }

    public static Scope parse(String text, Scope fallback) {
        for (Scope s : values()) {
            if (s.id().equalsIgnoreCase(text)) {
                return s;
            }
        }
        return fallback;
    }
}
