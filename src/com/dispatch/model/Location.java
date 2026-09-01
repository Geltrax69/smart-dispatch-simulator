package com.dispatch.model;

/**
 * Immutable 2D point on the city map. Used for restaurants, customers,
 * drivers, and warehouses.
 */
public final class Location {
    private final int x;
    private final int y;
    private final String name;
    private final LocationType type;

    public enum LocationType { RESTAURANT, WAREHOUSE, CUSTOMER, DEPOT }

    public Location(int x, int y, String name, LocationType type) {
        this.x = x;
        this.y = y;
        this.name = name;
        this.type = type;
    }

    public int getX() { return x; }
    public int getY() { return y; }
    public String getName() { return name; }
    public LocationType getType() { return type; }

    /** Euclidean distance to another location. */
    public double distanceTo(Location other) {
        double dx = this.x - other.x;
        double dy = this.y - other.y;
        return Math.sqrt(dx * dx + dy * dy);
    }

    /** Estimated travel time at a given speed (units/sec). */
    public double travelTimeTo(Location other, double speed) {
        if (speed <= 0) return Double.POSITIVE_INFINITY;
        return distanceTo(other) / speed;
    }

    @Override
    public String toString() {
        return name + "(" + x + "," + y + ")";
    }
}
