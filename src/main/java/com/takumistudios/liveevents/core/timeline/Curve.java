package com.takumistudios.liveevents.core.timeline;

import java.util.Locale;

/** Curvas de interpolación de los keyframes. Todas reciben y devuelven valores en [0, 1]. */
public enum Curve {
    LINEAR,
    EASE_IN,
    EASE_OUT,
    EASE_IN_OUT,
    PULSE,
    STEP;

    public double apply(double t) {
        double x = Math.max(0, Math.min(1, t));
        return switch (this) {
            case LINEAR -> x;
            case EASE_IN -> x * x * x;
            case EASE_OUT -> 1 - Math.pow(1 - x, 3);
            case EASE_IN_OUT -> x < 0.5 ? 4 * x * x * x : 1 - Math.pow(-2 * x + 2, 3) / 2;
            case PULSE -> Math.sin(x * Math.PI);
            case STEP -> x < 1 ? 0 : 1;
        };
    }

    public static Curve parse(String name, Curve fallback) {
        if (name == null) {
            return fallback;
        }
        try {
            return valueOf(name.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            return fallback;
        }
    }
}
