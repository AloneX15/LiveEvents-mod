package com.takumistudios.liveevents.core.plan;

/** Portable immutable server decision; ticks are on the event clock, coordinates are world space. */
public record AnnouncedMeteor(int module, int index, double tick, double x, double y, double z,
                              double radius, double power, double azimuth, double angle, boolean big,
                              String surface, long seed, double visualPower, double coolingSeconds, double smokeSeconds, double windX, double windZ) {
    public AnnouncedMeteor(int module,int index,double tick,double x,double y,double z,double radius,double power,double azimuth,double angle,boolean big,String surface,long seed) {
        this(module,index,tick,x,y,z,radius,power,azimuth,angle,big,surface,seed,power,120,120,0.2,0.05);
    }
    public boolean valid() {
        return Double.isFinite(coolingSeconds) && coolingSeconds>=0 && coolingSeconds<=600
                && Double.isFinite(smokeSeconds) && smokeSeconds>=0 && smokeSeconds<=600
                && Double.isFinite(windX) && Math.abs(windX)<=4 && Double.isFinite(windZ) && Math.abs(windZ)<=4
                && module >= 0 && index >= 0 && Double.isFinite(tick) && tick >= 0 && tick <= 1728000
                && Double.isFinite(x) && Math.abs(x) <= 30000000 && Double.isFinite(z) && Math.abs(z) <= 30000000
                && Double.isFinite(y) && Math.abs(y) <= 4096 && Double.isFinite(radius) && radius >= 0.5 && radius <= 48
                && Double.isFinite(visualPower) && visualPower >= 0 && visualPower <= 1
                && Double.isFinite(power) && power >= 0 && power <= 1 && Double.isFinite(azimuth)
                && Double.isFinite(angle) && angle >= 0 && angle <= 90
                && ("earth".equals(surface) || "water".equals(surface) || "snow".equals(surface));
    }
    public double lifetimeTicks() { return Math.max(coolingSeconds,smokeSeconds)*20; }
    public String key() { return module + ":" + index; }
}
