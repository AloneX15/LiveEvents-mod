package com.takumistudios.liveevents.core.plan;

/** Shared, bounded physical and presentation scales. Radius is the server-announced crater radius. */
public final class MeteorEffects {
    private MeteorEffects() { }
    public static double sizeMultiplier(double roll) {
        return roll < 0.35 ? 0.5 : roll < 0.65 ? 0.8 : roll < 0.85 ? 1 : roll < 0.97 ? 1.5 : 2;
    }
    public static double scale(double radius) { return Math.clamp(radius / 5.0, 0.1, 9.6); }
    public static double bodyRadius(double radius) { return Math.clamp(radius * 0.24, 0.12, 11.52); }
    public static double flash(double ageTicks) {
        if (ageTicks < 0 || ageTicks >= 12) return 0;
        double attack = Math.clamp(ageTicks / 1.5, 0, 1);
        double release = 1 - ageTicks / 12;
        return attack * release * release;
    }
    public static float pitch(double radius, long seed) {
        double variation = ((seed & 255) / 255.0 - 0.5) * 0.08;
        return (float)Math.clamp(1.18 - 0.22 * Math.log1p(scale(radius)) + variation, 0.62, 1.2);
    }
    public static double smokeSeconds(double configured, double radius) {
        return Math.clamp(configured * Math.clamp(Math.sqrt(scale(radius)), 0.65, 1.5), 0, 600);
    }
}
