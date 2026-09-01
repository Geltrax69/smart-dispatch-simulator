package com.dispatch.model;

/**
 * A road segment between two locations with a base length and a
 * mutable traffic factor. Traffic factor multiplies travel time.
 */
public final class Road {
    private final Location a;
    private final Location b;
    private final double baseLength;
    private volatile double trafficFactor = 1.0; // 1.0 = free flow, >1.0 = congestion

    public Road(Location a, Location b) {
        this.a = a;
        this.b = b;
        this.baseLength = a.distanceTo(b);
    }

    public Location getA() { return a; }
    public Location getB() { return b; }
    public double getBaseLength() { return baseLength; }
    public double getTrafficFactor() { return trafficFactor; }

    public void setTrafficFactor(double f) { this.trafficFactor = Math.max(1.0, f); }

    public double effectiveLength() { return baseLength * trafficFactor; }

    public boolean connects(Location x) { return x == a || x == b; }

    public Location other(Location x) { return x == a ? b : a; }
}
