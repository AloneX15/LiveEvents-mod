package com.takumistudios.liveevents.core.plan;
/** The configured count is the nominal count at brutality 0.5. */
public final class MeteorIntensity {
    private MeteorIntensity() { }
    public static double retain(double brutality) { return Math.max(0,Math.min(1,brutality*2)); }
    public static double extra(double brutality) { return Math.max(0,Math.min(1,(brutality-0.5)*2)); }
}
