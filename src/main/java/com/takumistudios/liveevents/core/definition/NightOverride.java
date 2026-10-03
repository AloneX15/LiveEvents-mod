package com.takumistudios.liveevents.core.definition;

import java.util.Locale;

/** none: nada; visual: el cliente pinta la noche; require: solo se puede lanzar de noche. */
public enum NightOverride {
    NONE,
    VISUAL,
    REQUIRE;

    public String id() {
        return name().toLowerCase(Locale.ROOT);
    }

    public static NightOverride parse(String text, NightOverride fallback) {
        for (NightOverride n : values()) {
            if (n.id().equalsIgnoreCase(text)) {
                return n;
            }
        }
        return fallback;
    }
}
