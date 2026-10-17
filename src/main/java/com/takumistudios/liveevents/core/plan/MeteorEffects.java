package com.takumistudios.liveevents.core.plan;

/** Shared, bounded physical and presentation scales. Radius is the server-announced crater radius. */
public final class MeteorEffects {
    private MeteorEffects() { }
    public static double sizeMultiplier(double roll) {
        return roll < 0.35 ? 0.5 : roll < 0.65 ? 0.8 : roll < 0.85 ? 1 : roll < 0.97 ? 1.5 : 2;
    }
    public static double scale(double radius) { return Math.clamp(radius / 5.0, 0.1, 9.6); }
    public static double bodyRadius(double radius) { return radius >= 100 ? 30 : Math.clamp(radius * 0.24, 0.12, 11.52); }
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
    public static double waveRadius(double ageTicks) { return Math.clamp(ageTicks*4, 0, 300); }
    public static double waveFalloff(double distance) { return Math.clamp(1-distance/300,0,1); }
    public static double giantFlashDuration(double distance) {
        return 20*(0.25+1.75*Math.clamp((300-distance)/240,0,1));
    }
    public static double giantFlash(double ageTicks,double distance) {
        double duration=giantFlashDuration(distance);
        if(ageTicks<0 || ageTicks>=duration || distance>=300) return 0;
        double peak=0.8*Math.clamp((300-distance)/240,0,1);
        return peak*Math.min(1,ageTicks/1.5)*Math.pow(1-ageTicks/duration,2);
    }
    public static boolean giantRoll(long seed,int index) {
        return new com.takumistudios.liveevents.core.random.DeterministicRandom(
                com.takumistudios.liveevents.core.random.DeterministicRandom.derive(seed,"giant:"+index)).nextDouble()<0.01;
    }
    public static double smokeSeconds(double configured, double radius) {
        return radius>=100 ? Math.clamp(configured,0,180) : Math.clamp(configured * Math.clamp(Math.sqrt(scale(radius)), 0.65, 1.5), 0, 600);
    }
}
