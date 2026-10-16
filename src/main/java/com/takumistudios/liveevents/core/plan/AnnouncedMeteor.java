package com.takumistudios.liveevents.core.plan;

/** Portable immutable server decision; ticks are on the event clock, coordinates are world space. */
public record AnnouncedMeteor(int module, int index, double tick, double x, double y, double z,
                              double radius, double power, double azimuth, double angle, boolean big,
                              String surface, long seed, double visualPower) {
    public AnnouncedMeteor(int module,int index,double tick,double x,double y,double z,double radius,double power,double azimuth,double angle,boolean big,String surface,long seed) {
        this(module,index,tick,x,y,z,radius,power,azimuth,angle,big,surface,seed,power);
    }
    public boolean valid() {
        return module >= 0 && index >= 0 && Double.isFinite(tick) && tick >= 0 && tick <= 1728000
                && Double.isFinite(x) && Math.abs(x) <= 30000000 && Double.isFinite(z) && Math.abs(z) <= 30000000
                && Double.isFinite(y) && Math.abs(y) <= 4096 && Double.isFinite(radius) && radius >= 0.5 && radius <= 48
                && Double.isFinite(visualPower) && visualPower >= 0 && visualPower <= 1
                && Double.isFinite(power) && power >= 0 && power <= 1 && Double.isFinite(azimuth)
                && Double.isFinite(angle) && angle >= 0 && angle <= 90
                && ("earth".equals(surface) || "water".equals(surface) || "snow".equals(surface));
    }
    public String key() { return module + ":" + index; }
}
