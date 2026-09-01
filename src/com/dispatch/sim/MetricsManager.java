package com.dispatch.sim;

import com.dispatch.model.Driver;
import com.dispatch.model.Order;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Live and rolling metrics. Cheap counters; the metrics thread samples
 * and stores a history window the UI can plot.
 */
public final class MetricsManager {
    private final AtomicLong totalOrders = new AtomicLong();
    private final AtomicLong pendingOrders = new AtomicLong();
    private final AtomicLong activeOrders = new AtomicLong();
    private final AtomicLong completedOrders = new AtomicLong();
    private final AtomicLong failedOrders = new AtomicLong();
    private final AtomicLong cancelledOrders = new AtomicLong();
    private final AtomicLong availableDrivers = new AtomicLong();
    private final AtomicLong busyDrivers = new AtomicLong();

    // rolling sum of latencies (ms) and counts for averages
    private final AtomicLong sumAssignMs = new AtomicLong();
    private final AtomicLong assignSamples = new AtomicLong();
    private final AtomicLong sumDeliveryMs = new AtomicLong();
    private final AtomicLong deliverySamples = new AtomicLong();

    // rolling window of orders/sec, last 10s
    private final Deque<long[]> rateWindow = new ArrayDeque<>();
    private static final long WINDOW_MS = 10_000L;
    private final Object rateLock = new Object();

    public void incTotal()        { totalOrders.incrementAndGet(); pendingOrders.incrementAndGet(); }
    public void incActive()       { activeOrders.incrementAndGet(); pendingOrders.decrementAndGet(); }
    public void incCompleted(long latencyMs) {
        completedOrders.incrementAndGet(); activeOrders.decrementAndGet();
        sumDeliveryMs.addAndGet(latencyMs); deliverySamples.incrementAndGet();
    }
    public void incFailed(String reason) {
        failedOrders.incrementAndGet(); activeOrders.decrementAndGet();
    }
    public void incCancelled()    { cancelledOrders.incrementAndGet(); pendingOrders.decrementAndGet(); }
    public void recordAssign(long latencyMs) {
        sumAssignMs.addAndGet(latencyMs); assignSamples.incrementAndGet();
    }
    public void tickRate(long nowMs) {
        synchronized (rateLock) {
            rateWindow.addLast(new long[]{nowMs, 1L});
            while (!rateWindow.isEmpty() && nowMs - rateWindow.peekFirst()[0] > WINDOW_MS) {
                rateWindow.pollFirst();
            }
        }
    }

    public Snapshot snapshot(List<Driver> drivers) {
        long now = System.currentTimeMillis();
        long av = 0, busy = 0;
        for (Driver d : drivers) {
            switch (d.getStatus()) {
                case AVAILABLE -> av++;
                case ASSIGNED, EN_ROUTE_PICKUP, DELIVERING -> busy++;
                default -> {}
            }
        }
        availableDrivers.set(av);
        busyDrivers.set(busy);
        long rate;
        synchronized (rateLock) {
            rate = rateWindow.stream().mapToLong(a -> a[1]).sum();
        }
        double avgAssign = assignSamples.get() == 0 ? 0 : (double) sumAssignMs.get() / assignSamples.get();
        double avgDeliv  = deliverySamples.get() == 0 ? 0 : (double) sumDeliveryMs.get() / deliverySamples.get();
        double ordersPerSec = rate / (WINDOW_MS / 1000.0);
        double utilization = drivers.isEmpty() ? 0 : (double) busy / drivers.size();
        return new Snapshot(
            totalOrders.get(), pendingOrders.get(), activeOrders.get(),
            completedOrders.get(), failedOrders.get(), cancelledOrders.get(),
            av, busy, avgAssign, avgDeliv, ordersPerSec, utilization
        );
    }

    public void reset() {
        totalOrders.set(0); pendingOrders.set(0); activeOrders.set(0);
        completedOrders.set(0); failedOrders.set(0); cancelledOrders.set(0);
        availableDrivers.set(0); busyDrivers.set(0);
        sumAssignMs.set(0); assignSamples.set(0);
        sumDeliveryMs.set(0); deliverySamples.set(0);
        synchronized (rateLock) { rateWindow.clear(); }
    }

    public record Snapshot(
        long total, long pending, long active, long completed, long failed, long cancelled,
        long available, long busy,
        double avgAssignMs, double avgDeliveryMs,
        double ordersPerSec, double driverUtilization
    ) {}
}
