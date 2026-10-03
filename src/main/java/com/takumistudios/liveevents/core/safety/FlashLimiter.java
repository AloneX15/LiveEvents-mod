package com.takumistudios.liveevents.core.safety;

/**
 * Regla de destellos (criterio WCAG 2.3.1), aplicada en todos los perfiles y sumando todos los eventos activos:
 * <ul>
 *   <li>Como máximo {@value #MAX_FLASHES_PER_SECOND} destellos o cortes grandes por segundo (ventana deslizante).</li>
 *   <li>Ningún destello de rojo saturado que ocupe una parte grande de la pantalla.</li>
 *   <li>Los parpadeos rápidos solo se permiten en áreas pequeñas.</li>
 * </ul>
 * Trabaja con tiempo real en nanosegundos (nunca con fotogramas) para no cambiar con FPS bajos.
 */
public final class FlashLimiter {
    public static final int MAX_FLASHES_PER_SECOND = 3;
    /** Área de pantalla (fracción) a partir de la cual un destello cuenta como "grande". */
    public static final double LARGE_AREA = 0.25;
    private static final long WINDOW_NANOS = 1_000_000_000L;

    private final long[] recent = new long[MAX_FLASHES_PER_SECOND];
    private int count;
    private int head;

    /**
     * Pide permiso para un destello o corte. Si se concede, queda registrado.
     *
     * @param nowNanos  tiempo real actual
     * @param areaRatio fracción de pantalla que ocupa (0–1)
     * @return true si puede mostrarse tal cual; false si debe convertirse en fundido o descartarse
     */
    public synchronized boolean tryFlash(long nowNanos, double areaRatio) {
        if (areaRatio < LARGE_AREA) {
            return true; // destellos pequeños: no cuentan
        }
        expire(nowNanos);
        if (count >= MAX_FLASHES_PER_SECOND) {
            return false;
        }
        recent[(head + count) % recent.length] = nowNanos;
        count++;
        return true;
    }

    /** Destellos grandes en el último segundo. */
    public synchronized int recentCount(long nowNanos) {
        expire(nowNanos);
        return count;
    }

    public synchronized void reset() {
        count = 0;
        head = 0;
    }

    private void expire(long nowNanos) {
        while (count > 0 && nowNanos - recent[head] >= WINDOW_NANOS) {
            head = (head + 1) % recent.length;
            count--;
        }
    }

    /**
     * Rojo saturado: el canal rojo domina claramente y la saturación es alta. Se usa para prohibir destellos grandes
     * de ese color (el caso más peligroso de fotosensibilidad).
     */
    public static boolean isSaturatedRed(int rgb) {
        int r = (rgb >> 16) & 0xFF, g = (rgb >> 8) & 0xFF, b = rgb & 0xFF;
        int max = Math.max(r, Math.max(g, b)), min = Math.min(r, Math.min(g, b));
        if (max < 0x60 || max != r) {
            return false;
        }
        double saturation = max == 0 ? 0 : (max - min) / (double) max;
        return saturation > 0.8 && r > (g + b);
    }

    /**
     * Frecuencia máxima de parpadeo permitida según el área: en áreas pequeñas no hay límite práctico; en áreas
     * grandes, 3 Hz.
     */
    public static double maxBlinkHz(double areaRatio, double requestedHz) {
        if (areaRatio < 0.02) {
            return requestedHz;
        }
        return Math.min(requestedHz, MAX_FLASHES_PER_SECOND);
    }
}
