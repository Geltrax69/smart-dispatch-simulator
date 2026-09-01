package com.dispatch.model;

import java.util.Collections;
import java.util.List;

/**
 * A computed route: ordered list of waypoints plus the effective length
 * after traffic. Used by the dispatch engine and movement thread.
 */
public final class Route {
    private final List<Location> waypoints;
    private final double effectiveLength;
    private final long computedAtMillis;

    public Route(List<Location> waypoints, double effectiveLength) {
        this.waypoints = Collections.unmodifiableList(waypoints);
        this.effectiveLength = effectiveLength;
        this.computedAtMillis = System.currentTimeMillis();
    }

    public List<Location> getWaypoints() { return waypoints; }
    public double getEffectiveLength() { return effectiveLength; }
    public long getComputedAtMillis() { return computedAtMillis; }
}
