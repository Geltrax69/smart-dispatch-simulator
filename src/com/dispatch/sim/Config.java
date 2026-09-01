package com.dispatch.sim;

import com.dispatch.model.Location.LocationType;
import com.dispatch.model.Vehicle.VehicleType;

/**
 * Knobs the user can change at runtime. Default values produce a busy
 * but readable simulation.
 */
public final class Config {
    // Map
    public volatile int mapWidth = 900;
    public volatile int mapHeight = 600;
    public volatile int minLocations = 25;
    public volatile int maxLocations = 45;
    public volatile int minDrivers = 10;
    public volatile int maxDrivers = 20;

    // Simulation
    public volatile double simulationSpeed = 1.0;     // 0.25x .. 4x
    public volatile int tickMillis = 50;              // base movement tick
    public volatile double orderGenRate = 1.0;        // orders/sec target
    public volatile int burstSize = 1;                // set >1 for "Generate 10 Orders"
    public volatile double trafficIntensity = 0.0;    // 0..1
    public volatile double failureRate = 0.0;         // 0..1 random failure chance per delivery

    // Dispatch scoring weights — configurable, the user asked for this
    public volatile double wDistance  = 1.0;
    public volatile double wTime      = 1.0;
    public volatile double wCapacity  = 0.5;
    public volatile double wWorkload  = 1.5;
    public volatile double wPriority  = 1.0;
    public volatile double wDeadline  = 2.0;

    // Defaults
    public static final VehicleType[] VEHICLE_MIX = {
        VehicleType.BIKE, VehicleType.SCOOTER, VehicleType.SCOOTER,
        VehicleType.CAR, VehicleType.CAR, VehicleType.VAN
    };
    public static final LocationType[] PICKUP_TYPES = {
        LocationType.RESTAURANT, LocationType.WAREHOUSE
    };
    public static final LocationType[] DROPOFF_TYPE = {
        LocationType.CUSTOMER
    };
}
