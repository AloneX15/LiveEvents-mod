package com.takumistudios.liveevents.core.definition;

import java.util.Locale;
import java.util.Optional;

/** Qué pasa con los cambios de bloques al terminar un evento (§8.5). */
public enum WorldMode {
    PERMANENT,
    REVERT_ON_END,
    REVERT_MANUAL;

    public String id() {
        return name().toLowerCase(Locale.ROOT);
    }

    public static Optional<WorldMode> parse(String text) {
        for (WorldMode m : values()) {
            if (m.id().equalsIgnoreCase(text)) {
                return Optional.of(m);
            }
        }
        return Optional.empty();
    }
}
