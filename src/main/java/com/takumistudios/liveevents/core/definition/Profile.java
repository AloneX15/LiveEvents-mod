package com.takumistudios.liveevents.core.definition;

import java.util.Locale;
import java.util.Optional;

/** Perfiles de intensidad (§7.10). El orden importa: de menos a más intenso. */
public enum Profile {
    SAFE,
    GAMEPLAY,
    CINEMATIC;

    public String id() {
        return name().toLowerCase(Locale.ROOT);
    }

    /** El menos intenso de los dos. */
    public Profile min(Profile other) {
        return ordinal() <= other.ordinal() ? this : other;
    }

    public static Optional<Profile> parse(String text) {
        if (text == null) {
            return Optional.empty();
        }
        for (Profile p : values()) {
            if (p.id().equalsIgnoreCase(text.trim())) {
                return Optional.of(p);
            }
        }
        return Optional.empty();
    }

    /**
     * Perfil efectivo: el pedido por el comando, limitado por el máximo del servidor y por el que acepta el cliente.
     * El cliente tiene la última palabra.
     */
    public static Profile resolve(Profile requested, Profile serverMax, Profile clientMax) {
        return requested.min(serverMax).min(clientMax);
    }
}
