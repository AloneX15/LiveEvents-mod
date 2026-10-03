package com.takumistudios.liveevents.core.plan;

import com.takumistudios.liveevents.core.random.DeterministicRandom;

import java.util.ArrayList;
import java.util.List;

/** Línea de falla determinista del terremoto: una grieta principal que serpentea y algunas ramas. */
public final class CrackPlan {
    /** Punto de la grieta (relativo al epicentro) con su anchura y profundidad locales. */
    public record Point(int dx, int dz, int width, int depth) {
    }

    private CrackPlan() {
    }

    public static List<Point> generate(long seed, int length, int width, int depth, int branches) {
        DeterministicRandom random = new DeterministicRandom(seed);
        List<Point> points = new ArrayList<>();
        double heading = random.range(0, Math.PI * 2);
        int half = Math.max(2, Math.min(length, 1024)) / 2;
        walk(points, random, 0, 0, heading, half, width, depth, seed);
        walk(points, random, 0, 0, heading + Math.PI, half, width, depth, seed + 1);
        int b = Math.max(0, Math.min(branches, 16));
        for (int i = 0; i < b && !points.isEmpty(); i++) {
            Point from = points.get(random.nextInt(points.size()));
            double branchHeading = heading + (random.chance(0.5) ? 1 : -1) * random.range(0.5, 1.2);
            walk(points, random, from.dx(), from.dz(), branchHeading, half / 3, Math.max(1, width - 1), Math.max(1, depth / 2), seed + 10 + i);
        }
        return points;
    }

    private static void walk(List<Point> out, DeterministicRandom random, double x, double z, double heading, int steps,
                             int width, int depth, long noiseSeed) {
        double h = heading;
        for (int i = 0; i < steps; i++) {
            h += DeterministicRandom.noise1(noiseSeed, i * 0.15) * 0.25;
            x += Math.cos(h);
            z += Math.sin(h);
            double taper = 1.0 - (double) i / Math.max(1, steps); // más estrecha hacia los extremos
            int w = Math.max(1, (int) Math.round(width * (0.5 + 0.5 * taper)));
            int d = Math.max(1, (int) Math.round(depth * (0.4 + 0.6 * taper) * random.range(0.8, 1.2)));
            out.add(new Point((int) Math.round(x), (int) Math.round(z), w, d));
        }
    }
}
