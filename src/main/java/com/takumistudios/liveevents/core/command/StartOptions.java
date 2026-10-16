package com.takumistudios.liveevents.core.command;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

/**
 * Opciones de {@code /le start <evento> [opciones]}:
 * <pre>
 * at &lt;x&gt; &lt;y&gt; &lt;z&gt;     posición (admite ~ relativas)
 * on &lt;jugador|selector&gt;
 * in &lt;dimensión&gt;
 * delay &lt;segundos&gt;    cuenta atrás
 * profile=&lt;cinematic|gameplay|safe&gt;
 * timescale=&lt;0.1–2.0&gt;
 * queue                si hay un evento incompatible, ponerlo en cola
 * confirm              confirmar (mundos hardcore)
 * &lt;param&gt;=&lt;número&gt;    parámetros del evento ($radius, $count…)
 * </pre>
 */
public record StartOptions(Optional<String[]> at, Optional<String> on, Optional<String> in, int delaySeconds,
                           Optional<String> profile, double timescale, boolean queue, boolean confirm,
                           Map<String, Double> params) {
    public static final List<String> KEYWORDS = List.of("at", "on", "in", "delay", "profile=cinematic", "profile=gameplay",
            "profile=safe", "timescale=", "queue", "confirm");
    public static final int MAX_DELAY_SECONDS = 3600;
    public static final int MAX_PARAMS = 32;

    public static StartOptions parse(String text) {
        List<String> tokens = new ArrayList<>();
        if (text != null && !text.isBlank()) {
            for (String t : text.trim().split("\\s+")) {
                tokens.add(t);
            }
        }
        Optional<String[]> at = Optional.empty();
        Optional<String> on = Optional.empty(), in = Optional.empty(), profile = Optional.empty();
        int delay = 0;
        double timescale = 1.0;
        boolean queue = false, confirm = false;
        Map<String, Double> params = new LinkedHashMap<>();
        for (int i = 0; i < tokens.size(); i++) {
            String t = tokens.get(i);
            String lower = t.toLowerCase(Locale.ROOT);
            switch (lower) {
                case "at" -> {
                    if (i + 3 >= tokens.size()) {
                        throw new IllegalArgumentException("at necesita tres coordenadas");
                    }
                    String[] xyz = {tokens.get(i + 1), tokens.get(i + 2), tokens.get(i + 3)};
                    for (String c : xyz) {
                        if (c.isEmpty() || !c.matches("~?-?\\d*(\\.\\d+)?") || c.equals("-")) {
                            throw new IllegalArgumentException("coordenada no válida: " + c);
                        }
                    }
                    at = Optional.of(xyz);
                    i += 3;
                }
                case "on" -> {
                    on = Optional.of(next(tokens, ++i, "on"));
                }
                case "in" -> {
                    String dim = next(tokens, ++i, "in");
                    if (!dim.matches("[a-z0-9_.-]+(:[a-z0-9_./-]+)?")) {
                        throw new IllegalArgumentException("dimensión no válida: " + dim);
                    }
                    in = Optional.of(dim.contains(":") ? dim : "minecraft:" + dim);
                }
                case "delay" -> {
                    String n = next(tokens, ++i, "delay");
                    try {
                        delay = Integer.parseInt(n.endsWith("s") ? n.substring(0, n.length() - 1) : n);
                    } catch (NumberFormatException e) {
                        throw new IllegalArgumentException("delay necesita un número de segundos");
                    }
                    if (delay < 0 || delay > MAX_DELAY_SECONDS) {
                        throw new IllegalArgumentException("delay debe estar entre 0 y " + MAX_DELAY_SECONDS + " s");
                    }
                }
                case "queue" -> queue = true;
                case "confirm" -> confirm = true;
                default -> {
                    int eq = t.indexOf('=');
                    if (eq <= 0 || eq == t.length() - 1) {
                        throw new IllegalArgumentException("opción desconocida: " + t);
                    }
                    String key = lower.substring(0, eq), value = t.substring(eq + 1);
                    switch (key) {
                        case "profile" -> {
                            if (!value.matches("(?i)cinematic|gameplay|safe")) {
                                throw new IllegalArgumentException("perfil desconocido: " + value);
                            }
                            profile = Optional.of(value.toLowerCase(Locale.ROOT));
                        }
                        case "timescale" -> {
                            timescale = number(key, value);
                            if (timescale < 0.1 || timescale > 2.0) {
                                throw new IllegalArgumentException("timescale debe estar entre 0.1 y 2.0");
                            }
                        }
                        default -> {
                            if (!key.matches("[a-z0-9_]+")) {
                                throw new IllegalArgumentException("parámetro no válido: " + key);
                            }
                            if (params.size() >= MAX_PARAMS) {
                                throw new IllegalArgumentException("demasiados parámetros");
                            }
                            double parsed = number(key, (key.equals("duration") || key.equals("remaining")) && value.endsWith("s") ? value.substring(0, value.length() - 1) : value);
                            if ((key.equals("power") || key.equals("visual_power") || key.equals("destructive_power") || key.equals("brutality")) && (parsed < 0 || parsed > 1)) throw new IllegalArgumentException(key + " must be 0..1");
                            if (key.equals("duration") && (parsed < 3 || parsed > 86400)) throw new IllegalArgumentException("duration must be 3..86400s");
                            params.put(key, parsed);
                        }
                    }
                }
            }
        }
        if (at.isPresent() && on.isPresent()) {
            throw new IllegalArgumentException("usa at o on, no los dos");
        }
        return new StartOptions(at, on, in, delay, profile, timescale, queue, confirm, params);
    }

    private static String next(List<String> tokens, int i, String option) {
        if (i >= tokens.size()) {
            throw new IllegalArgumentException(option + " necesita un valor");
        }
        return tokens.get(i);
    }

    private static double number(String key, String value) {
        try {
            double v = Double.parseDouble(value);
            if (!Double.isFinite(v)) {
                throw new NumberFormatException();
            }
            return v;
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException(key + " necesita un número: " + value);
        }
    }

    /** Resuelve una coordenada (absoluta o ~relativa) respecto a la posición de quien ejecuta. */
    public static double coordinate(String text, double origin) {
        if (text.startsWith("~")) {
            String rest = text.substring(1);
            return origin + (rest.isEmpty() ? 0 : Double.parseDouble(rest));
        }
        return Double.parseDouble(text);
    }
}
