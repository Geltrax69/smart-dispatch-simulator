package com.dispatch.model;

/**
 * Vehicle carried by a driver. Capacity is in arbitrary "package units".
 */
public final class Vehicle {
    private final String id;
    private final int capacity;
    private final double baseSpeed;   // map units per simulated second
    private final VehicleType type;

    public enum VehicleType { BIKE, SCOOTER, CAR, VAN }

    public Vehicle(String id, VehicleType type, int capacity, double baseSpeed) {
        this.id = id;
        this.type = type;
        this.capacity = capacity;
        this.baseSpeed = baseSpeed;
    }

    public String getId() { return id; }
    public int getCapacity() { return capacity; }
    public double getBaseSpeed() { return baseSpeed; }
    public VehicleType getType() { return type; }

    @Override
    public String toString() {
        return "Vehicle{" + type + " cap=" + capacity + " speed=" + baseSpeed + "}";
    }
}
