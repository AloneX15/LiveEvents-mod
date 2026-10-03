package com.takumistudios.liveevents.core.definition;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalInt;

/** Colores con nombre de un evento ({@code "$cyan"} → 0x00FFFF). */
public final class Palette {
    public static final Palette EMPTY = new Palette(Map.of());

    private final Map<String, Integer> colors;

    public Palette(Map<String, Integer> colors) {
        this.colors = Collections.unmodifiableMap(new LinkedHashMap<>(colors));
    }

    public Map<String, Integer> colors() {
        return colors;
    }

    public OptionalInt get(String name) {
        Integer c = colors.get(name);
        return c == null ? OptionalInt.empty() : OptionalInt.of(c);
    }

    /**
     * Resuelve un color: {@code "#RRGGBB"}, {@code "RRGGBB"} o {@code "$nombre"}.
     */
    public OptionalInt resolve(String text) {
        if (text == null) {
            return OptionalInt.empty();
        }
        String s = text.trim();
        if (s.startsWith("$")) {
            return get(s.substring(1));
        }
        return parseHex(s);
    }

    public static OptionalInt parseHex(String text) {
        String s = text.startsWith("#") ? text.substring(1) : text;
        if (!s.matches("[0-9a-fA-F]{6}")) {
            return OptionalInt.empty();
        }
        return OptionalInt.of(Integer.parseInt(s, 16));
    }

    public static String toHex(int rgb) {
        return String.format(Locale.ROOT, "#%06X", rgb & 0xFFFFFF);
    }

    /** Sugerencia para nombres mal escritos: "$cyna" → "cyan". */
    public Optional<String> suggest(String wrong) {
        return Suggestions.closest(wrong, colors.keySet());
    }
}
