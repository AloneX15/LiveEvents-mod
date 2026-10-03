package com.takumistudios.liveevents.core.safety;

import java.util.ArrayDeque;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

/**
 * Límite de acciones por clave en una ventana deslizante ("5/min" lanzamientos por persona). Limpia las claves
 * inactivas para no crecer sin límite.
 */
public final class RateLimiter {
    private final int max;
    private final long windowMillis;
    private final Map<String, ArrayDeque<Long>> hits = new HashMap<>();

    public RateLimiter(int max, long windowMillis) {
        this.max = Math.max(1, max);
        this.windowMillis = Math.max(1, windowMillis);
    }

    /** "5/min", "10/h", "3/s". */
    public static RateLimiter parse(String spec, RateLimiter fallback) {
        try {
            String[] parts = spec.trim().split("/");
            int count = Integer.parseInt(parts[0].trim());
            long window = switch (parts[1].trim()) {
                case "s" -> 1000L;
                case "min", "m" -> 60_000L;
                case "h" -> 3_600_000L;
                default -> throw new IllegalArgumentException(spec);
            };
            return new RateLimiter(count, window);
        } catch (RuntimeException e) {
            return fallback;
        }
    }

    public synchronized boolean tryAcquire(String key, long nowMillis) {
        cleanup(nowMillis);
        ArrayDeque<Long> q = hits.computeIfAbsent(key, k -> new ArrayDeque<>());
        while (!q.isEmpty() && nowMillis - q.peekFirst() >= windowMillis) {
            q.pollFirst();
        }
        if (q.size() >= max) {
            return false;
        }
        q.addLast(nowMillis);
        return true;
    }

    private void cleanup(long nowMillis) {
        if (hits.size() < 256) {
            return;
        }
        Iterator<Map.Entry<String, ArrayDeque<Long>>> it = hits.entrySet().iterator();
        while (it.hasNext()) {
            ArrayDeque<Long> q = it.next().getValue();
            if (q.isEmpty() || nowMillis - q.peekLast() >= windowMillis) {
                it.remove();
            }
        }
    }
}
