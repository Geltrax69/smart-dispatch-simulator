package com.dispatch.model;

import java.util.concurrent.atomic.AtomicReference;

/**
 * A delivery order. State is an AtomicReference so the dispatch thread
 * can hand it to a movement thread without an extra lock.
 */
public final class Order {
    private static final java.util.concurrent.atomic.AtomicInteger SEQ =
        new java.util.concurrent.atomic.AtomicInteger(1000);

    public enum Status { PENDING, ASSIGNED, PICKED_UP, DELIVERED, FAILED, CANCELLED }

    private final String id;
    private final Location pickup;
    private final Location dropoff;
    private final int packageSize;
    private final int priority;             // 1=low, 5=high
    private final long createdAtMillis;
    private final long deadlineMillis;      // soft deadline

    private final AtomicReference<Status> status = new AtomicReference<>(Status.PENDING);
    private final AtomicReference<String> assignedDriverId = new AtomicReference<>(null);
    private final AtomicReference<Long> assignedAtMillis = new AtomicReference<>(null);
    private final AtomicReference<Long> deliveredAtMillis = new AtomicReference<>(null);
    private final AtomicReference<String> failureReason = new AtomicReference<>(null);

    public Order(Location pickup, Location dropoff, int packageSize, int priority,
                 long deadlineMillis) {
        this.id = "O" + SEQ.getAndIncrement();
        this.pickup = pickup;
        this.dropoff = dropoff;
        this.packageSize = Math.max(1, packageSize);
        this.priority = Math.max(1, Math.min(5, priority));
        this.createdAtMillis = System.currentTimeMillis();
        this.deadlineMillis = deadlineMillis;
    }

    public String getId() { return id; }
    public Location getPickup() { return pickup; }
    public Location getDropoff() { return dropoff; }
    public int getPackageSize() { return packageSize; }
    public int getPriority() { return priority; }
    public long getCreatedAtMillis() { return createdAtMillis; }
    public long getDeadlineMillis() { return deadlineMillis; }

    public Status getStatus() { return status.get(); }
    public void setStatus(Status s) { status.set(s); }
    public boolean compareAndSetStatus(Status expected, Status update) {
        return status.compareAndSet(expected, update);
    }

    public String getAssignedDriverId() { return assignedDriverId.get(); }
    public void setAssignedDriverId(String driverId) { assignedDriverId.set(driverId); }

    public Long getAssignedAtMillis() { return assignedAtMillis.get(); }
    public void setAssignedAtMillis(long t) { assignedAtMillis.set(t); }

    public Long getDeliveredAtMillis() { return deliveredAtMillis.get(); }
    public void setDeliveredAtMillis(long t) { deliveredAtMillis.set(t); }

    public String getFailureReason() { return failureReason.get(); }
    public void setFailureReason(String r) { failureReason.set(r); }

    public long getAssignmentLatencyMillis() {
        Long t = assignedAtMillis.get();
        return t == null ? -1 : t - createdAtMillis;
    }

    public long getDeliveryLatencyMillis() {
        Long d = deliveredAtMillis.get();
        return d == null ? -1 : d - createdAtMillis;
    }

    @Override
    public String toString() {
        return id + " " + status.get() + " " + pickup.getName() + "->" + dropoff.getName();
    }
}
