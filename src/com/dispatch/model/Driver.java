package com.dispatch.model;

import com.dispatch.model.Vehicle.VehicleType;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

/**
 * A driver. Position is mutable; everything else is set at construction
 * except {@code online} and {@code currentOrderId}, which move with
 * AtomicReference to keep lock-free hot-path updates safe.
 */
public final class Driver {
    private static final AtomicInteger SEQ = new AtomicInteger(1);

    public enum Status { AVAILABLE, ASSIGNED, EN_ROUTE_PICKUP, DELIVERING, OFFLINE, ACCIDENT }

    private final int id;
    private final String name;
    private final Vehicle vehicle;
    private final int priority;            // 1=low, 5=high — affects dispatch scoring
    private final Location homeDepot;

    private final AtomicReference<Location> position = new AtomicReference<>();
    private final AtomicReference<Status> status = new AtomicReference<>(Status.AVAILABLE);
    private final AtomicReference<String> currentOrderId = new AtomicReference<>(null);

    private final AtomicInteger workload = new AtomicInteger(0); // current package load

    public Driver(String name, Location homeDepot, VehicleType vtype, int priority) {
        this.id = SEQ.getAndIncrement();
        this.name = name;
        this.homeDepot = homeDepot;
        this.position.set(homeDepot);
        this.priority = Math.max(1, Math.min(5, priority));
        this.vehicle = switch (vtype) {
            case BIKE   -> new Vehicle("V" + id, VehicleType.BIKE,   2,  60.0);
            case SCOOTER-> new Vehicle("V" + id, VehicleType.SCOOTER,4,  80.0);
            case CAR    -> new Vehicle("V" + id, VehicleType.CAR,    8, 100.0);
            case VAN    -> new Vehicle("V" + id, VehicleType.VAN,    20, 90.0);
        };
    }

    public int getId() { return id; }
    public String getName() { return name; }
    public Vehicle getVehicle() { return vehicle; }
    public int getPriority() { return priority; }
    public Location getHomeDepot() { return homeDepot; }

    public Location getPosition() { return position.get(); }
    public void setPosition(Location loc) { position.set(loc); }

    public Status getStatus() { return status.get(); }
    public void setStatus(Status s) { status.set(s); }

    public String getCurrentOrderId() { return currentOrderId.get(); }
    public void setCurrentOrderId(String orderId) { currentOrderId.set(orderId); }

    public int getWorkload() { return workload.get(); }
    public int addWorkload(int delta) { return workload.addAndGet(delta); }

    public boolean isAvailable() {
        Status s = status.get();
        return s == Status.AVAILABLE;
    }

    public boolean canTake(int packageSize) {
        return isAvailable()
            && workload.get() + packageSize <= vehicle.getCapacity()
            && status.get() != Status.OFFLINE
            && status.get() != Status.ACCIDENT;
    }

    @Override
    public String toString() {
        return "Driver#" + id + " " + name + " [" + status.get() + "] @ " + position.get();
    }
}
