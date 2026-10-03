package com.takumistudios.liveevents.core.time;

import java.util.Locale;

/**
 * Conversión de duraciones escritas en las definiciones ("8.1s", "1200t", "2m", "3d", "permanent") a ticks.
 * Un tick son 50 ms; un día de juego son 24000 ticks.
 */
public final class Durations {
    public static final int TICKS_PER_SECOND = 20;
    public static final long TICKS_PER_GAME_DAY = 24000L;
    /** Valor que representa "sin caducidad". */
    public static final long PERMANENT = Long.MAX_VALUE;
    /** Tope de seguridad: ninguna duración de evento puede superar 24 h reales. */
    public static final long MAX_TICKS = 24L * 60 * 60 * TICKS_PER_SECOND;

    private Durations() {
    }

    /**
     * Convierte una duración a ticks (con decimales; el cliente interpola entre ticks).
     *
     * @throws IllegalArgumentException si el formato no es válido o es negativa
     */
    public static double parseTicksExact(String text) {
        if (text == null) {
            throw new IllegalArgumentException("duración vacía");
        }
        String s = text.trim().toLowerCase(Locale.ROOT);
        if (s.isEmpty()) {
            throw new IllegalArgumentException("duración vacía");
        }
        if (s.equals("permanent")) {
            return PERMANENT;
        }
        if (s.length() > 24) {
            throw new IllegalArgumentException("duración demasiado larga: " + text);
        }
        char unit = s.charAt(s.length() - 1);
        double factor;
        String number;
        if (Character.isDigit(unit) || unit == '.') {
            factor = TICKS_PER_SECOND; // sin unidad = segundos
            number = s;
        } else {
            number = s.substring(0, s.length() - 1);
            factor = switch (unit) {
                case 't' -> 1;
                case 's' -> TICKS_PER_SECOND;
                case 'm' -> TICKS_PER_SECOND * 60.0;
                case 'h' -> TICKS_PER_SECOND * 3600.0;
                case 'd' -> TICKS_PER_GAME_DAY; // días de juego
                default -> throw new IllegalArgumentException("unidad desconocida '" + unit + "' en " + text);
            };
        }
        double value;
        try {
            value = Double.parseDouble(number);
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("número no válido en " + text, e);
        }
        if (!Double.isFinite(value) || value < 0) {
            throw new IllegalArgumentException("la duración debe ser un número positivo: " + text);
        }
        return value * factor;
    }

    /** Igual que {@link #parseTicksExact} pero redondeado al tick más cercano. */
    public static long parseTicks(String text) {
        double exact = parseTicksExact(text);
        if (exact == PERMANENT) {
            return PERMANENT;
        }
        return Math.round(exact);
    }

    public static double ticksToSeconds(double ticks) {
        return ticks / TICKS_PER_SECOND;
    }

    /** "75.5" segundos → "01:15". */
    public static String formatClock(double seconds) {
        long total = Math.max(0, (long) Math.floor(seconds));
        return String.format(Locale.ROOT, "%02d:%02d", total / 60, total % 60);
    }
}
