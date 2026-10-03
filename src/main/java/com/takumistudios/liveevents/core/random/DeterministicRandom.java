package com.takumistudios.liveevents.core.random;

/**
 * Generador determinista (SplitMix64) y ruido de valor. El servidor y el cliente obtienen exactamente los mismos
 * números a partir de la misma semilla, sin intercambiar datos: trayectorias, cráteres, grietas y visuales.
 * <p>
 * No es seguro entre hilos; cada uso crea su propia instancia (es muy barata).
 */
public final class DeterministicRandom {
    private long state;

    public DeterministicRandom(long seed) {
        this.state = seed;
    }

    /** Deriva una semilla independiente para un sub-sistema (p. ej. "meteoro nº 12"). */
    public static long derive(long seed, long salt) {
        return mix(seed ^ mix(salt + 0x9E3779B97F4A7C15L));
    }

    public static long derive(long seed, String salt) {
        long h = 1125899906842597L;
        for (int i = 0; i < salt.length(); i++) {
            h = 31 * h + salt.charAt(i);
        }
        return derive(seed, h);
    }

    public long nextLong() {
        state += 0x9E3779B97F4A7C15L;
        return mix(state);
    }

    /** Entero uniforme en [0, bound). */
    public int nextInt(int bound) {
        if (bound <= 0) {
            throw new IllegalArgumentException("bound debe ser positivo");
        }
        return (int) Math.floorMod(nextLong(), (long) bound);
    }

    /** Decimal uniforme en [0, 1). */
    public double nextDouble() {
        return (nextLong() >>> 11) * 0x1.0p-53;
    }

    public double range(double min, double max) {
        return min + (max - min) * nextDouble();
    }

    public boolean chance(double probability) {
        return nextDouble() < probability;
    }

    private static long mix(long z) {
        z = (z ^ (z >>> 30)) * 0xBF58476D1CE4E5B9L;
        z = (z ^ (z >>> 27)) * 0x94D049BB133111EBL;
        return z ^ (z >>> 31);
    }

    /** Ruido de valor 1D suave en [-1, 1] (interpolación coseno entre enteros). */
    public static double noise1(long seed, double x) {
        long i = (long) Math.floor(x);
        double f = x - i;
        double a = hashToUnit(seed, i, 0), b = hashToUnit(seed, i + 1, 0);
        double t = (1 - Math.cos(f * Math.PI)) * 0.5;
        return a + (b - a) * t;
    }

    /** Ruido de valor 2D suave en [-1, 1]. */
    public static double noise2(long seed, double x, double y) {
        long ix = (long) Math.floor(x), iy = (long) Math.floor(y);
        double fx = x - ix, fy = y - iy;
        double tx = fx * fx * (3 - 2 * fx), ty = fy * fy * (3 - 2 * fy);
        double a = hashToUnit(seed, ix, iy), b = hashToUnit(seed, ix + 1, iy);
        double c = hashToUnit(seed, ix, iy + 1), d = hashToUnit(seed, ix + 1, iy + 1);
        double top = a + (b - a) * tx, bottom = c + (d - c) * tx;
        return top + (bottom - top) * ty;
    }

    /** Ruido fractal (varias octavas) en aproximadamente [-1, 1]. */
    public static double fbm2(long seed, double x, double y, int octaves) {
        double sum = 0, amp = 1, norm = 0, freq = 1;
        for (int o = 0; o < Math.max(1, Math.min(octaves, 8)); o++) {
            sum += amp * noise2(seed + o * 1013L, x * freq, y * freq);
            norm += amp;
            amp *= 0.5;
            freq *= 2;
        }
        return sum / norm;
    }

    private static double hashToUnit(long seed, long x, long y) {
        long h = mix(seed ^ mix(x * 0x632BE59BD9B4E019L + y * 0x85157AF5L));
        return ((h >>> 11) * 0x1.0p-53) * 2 - 1;
    }
}
