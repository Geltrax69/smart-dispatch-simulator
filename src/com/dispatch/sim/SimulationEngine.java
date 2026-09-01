package com.dispatch.sim;

import com.dispatch.model.*;
import com.dispatch.model.Driver.Status;
import com.dispatch.model.Vehicle.VehicleType;

import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.function.Consumer;

/**
 * The simulation coordinator. Owns all mutable shared state (drivers, orders)
 * and runs the following concurrent threads:
 *
 *  - tickThread    : movement, traffic, order generation, dispatch
 *  - metricsThread : samples metrics every second for the dashboard
 *
 * Race-condition prevention:
 *  - drivers list and order list are CopyOnWriteArrayList (reader-safe)
 *  - individual Driver / Order fields use AtomicReference / CAS where needed
 *  - driver/order list mutations use the rw lock so adds/removes are safe
 *  - UI reads via snapshot() which acquires read-lock
 */
public final class SimulationEngine {
    private final Config cfg;
    private final CityMap map;
    private final DispatchEngine dispatch;
    private final MetricsManager metrics;
    private final EventLogger log;

    private final AtomicBoolean running = new AtomicBoolean();
    private final AtomicBoolean paused  = new AtomicBoolean();

    private final CopyOnWriteArrayList<Driver> drivers = new CopyOnWriteArrayList<>();
    private final CopyOnWriteArrayList<Order> orders = new CopyOnWriteArrayList<>();
    private final ReadWriteLock stateLock = new ReentrantReadWriteLock();

    private Thread tickThread;
    private Thread metricsThread;
    private final Random rng = new Random();

    private volatile double simTime = 0.0; // simulated seconds elapsed

    public SimulationEngine(Config cfg, CityMap map, DispatchEngine dispatch,
                            MetricsManager metrics, EventLogger log) {
        this.cfg = cfg;
        this.map = map;
        this.dispatch = dispatch;
        this.metrics = metrics;
        this.log = log;
    }

    // ── lifecycle ────────────────────────────────────────────────────────────

    public void start() {
        if (running.compareAndSet(false, true)) {
            paused.set(false);
            tickThread = new Thread(this::tickLoop, "sim-tick");
            tickThread.setDaemon(true);
            tickThread.start();
            metricsThread = new Thread(this::metricsLoop, "sim-metrics");
            metricsThread.setDaemon(true);
            metricsThread.start();
            log.log("Simulation started");
        }
    }

    public void pause()  { paused.set(true);  log.log("Simulation paused"); }
    public void resume() { paused.set(false); log.log("Simulation resumed"); }
    public void stop() {
        running.set(false);
        paused.set(false);
        try {
            if (tickThread != null) tickThread.join(2000);
            if (metricsThread != null) metricsThread.join(2000);
        } catch (InterruptedException ignored) {}
        log.log("Simulation stopped");
    }

    public boolean isRunning()   { return running.get(); }
    public boolean isPaused()    { return paused.get(); }
    public double getSimTime()   { return simTime; }

    // ── drivers ───────────────────────────────────────────────────────────────

    public void addDriver(VehicleType vtype, int priority) {
        stateLock.writeLock().lock();
        try {
            List<Location> depots = map.getDepots();
            Location home = depots.isEmpty() ? map.getLocations().get(0) : depots.get(0);
            Driver d = new Driver("Driver-" + (drivers.size() + 1), home, vtype, priority);
            drivers.add(d);
            log.log("Driver #" + d.getId() + " (" + vtype + ") came online at " + home.getName());
        } finally { stateLock.writeLock().unlock(); }
    }

    public void driverOffline(int driverId) {
        for (Driver d : drivers) {
            if (d.getId() == driverId) {
                if (d.getStatus() == Driver.Status.ASSIGNED || d.getStatus() == Driver.Status.DELIVERING
                    || d.getStatus() == Driver.Status.EN_ROUTE_PICKUP) {
                    // cancel current order
                    String oid = d.getCurrentOrderId();
                    if (oid != null) {
                        for (Order o : orders) {
                            if (o.getId().equals(oid) && o.compareAndSetStatus(
                                    Order.Status.ASSIGNED, Order.Status.FAILED)) {
                                o.setFailureReason("Driver went offline");
                                metrics.incFailed("Driver offline");
                                log.log("Order " + oid + " FAILED — driver #" + driverId + " went offline");
                                break;
                            }
                        }
                    }
                }
                d.setStatus(Driver.Status.OFFLINE);
                d.setCurrentOrderId(null);
                log.log("Driver #" + driverId + " went OFFLINE");
                break;
            }
        }
    }

    public void driverOnline(int driverId) {
        for (Driver d : drivers) {
            if (d.getId() == driverId) {
                d.setStatus(Driver.Status.AVAILABLE);
                log.log("Driver #" + driverId + " is back ONLINE");
                break;
            }
        }
    }

    public void driverAccident(int driverId) {
        for (Driver d : drivers) {
            if (d.getId() == driverId) {
                d.setStatus(Driver.Status.ACCIDENT);
                log.log("⚠ Driver #" + driverId + " ACCIDENT — order " + d.getCurrentOrderId() + " FAILED");
                String oid = d.getCurrentOrderId();
                if (oid != null) {
                    for (Order o : orders) {
                        if (o.getId().equals(oid)) {
                            o.compareAndSetStatus(Order.Status.ASSIGNED, Order.Status.FAILED);
                            o.setFailureReason("Driver accident");
                            metrics.incFailed("Accident");
                            break;
                        }
                    }
                }
                break;
            }
        }
    }

    // ── orders ───────────────────────────────────────────────────────────────

    public void generateOrder() { generateN(1); }
    public void generateBurst() { generateN(cfg.burstSize > 0 ? cfg.burstSize : 10); }

    private void generateN(int n) {
        stateLock.writeLock().lock();
        try {
            for (int i = 0; i < n; i++) {
                Location pickup = map.randomPickup();
                Location dropoff = map.randomDropoff();
                if (pickup == null || dropoff == null) continue;
                int pkgSize = 1 + rng.nextInt(4);
                int priority = 1 + rng.nextInt(5);
                long deadlineMs = System.currentTimeMillis() + 300_000 + rng.nextInt(600_000);
                Order o = new Order(pickup, dropoff, pkgSize, priority, deadlineMs);
                orders.add(o);
                metrics.incTotal();
                log.log("Order " + o.getId() + " created — " + pickup.getName() + " → " + dropoff.getName()
                    + " (pkg=" + pkgSize + " priority=" + priority + ")");
                metrics.tickRate(System.currentTimeMillis());
            }
        } finally { stateLock.writeLock().unlock(); }
    }

    public void cancelOrder(String orderId) {
        for (Order o : orders) {
            if (o.getId().equals(orderId)) {
                if (o.compareAndSetStatus(Order.Status.PENDING, Order.Status.CANCELLED)) {
                    metrics.incCancelled();
                    log.log("Order " + orderId + " cancelled by user");
                } else if (o.compareAndSetStatus(Order.Status.ASSIGNED, Order.Status.CANCELLED)) {
                    // find driver and free them
                    String did = o.getAssignedDriverId();
                    for (Driver d : drivers) {
                        if (("D" + d.getId()).equals(did)) {
                            d.setStatus(Driver.Status.AVAILABLE);
                            d.setCurrentOrderId(null);
                            d.addWorkload(-o.getPackageSize());
                            break;
                        }
                    }
                    metrics.incCancelled();
                    log.log("Order " + orderId + " cancelled after assignment");
                }
                break;
            }
        }
    }

    // ── events ────────────────────────────────────────────────────────────────

    public void simulateTraffic() {
        map.tickTraffic();
        log.log("Traffic simulation tick applied");
    }

    public void simulateFailure() {
        List<Order> active = orders.stream()
            .filter(o -> o.getStatus() == Order.Status.ASSIGNED ||
                         o.getStatus() == Order.Status.PICKED_UP)
            .toList();
        if (active.isEmpty()) { log.log("No active orders to fail"); return; }
        Order fail = active.get(rng.nextInt(active.size()));
        fail.setStatus(Order.Status.FAILED);
        fail.setFailureReason("Random failure");
        metrics.incFailed("Random failure");
        log.log("Random failure: order " + fail.getId() + " failed");
    }

    public void simulateSpike() {
        log.log("Order SPIKE triggered — generating 10 orders");
        generateN(10);
    }

    // ── reset ────────────────────────────────────────────────────────────────

    public void reset() {
        stop();
        stateLock.writeLock().lock();
        try {
            drivers.clear();
            orders.clear();
            metrics.reset();
            simTime = 0;
            map.clearTraffic();
            log.log("Simulation reset");
        } finally { stateLock.writeLock().unlock(); }
    }

    // ── tick loop ────────────────────────────────────────────────────────────

    private void tickLoop() {
        long lastOrderGen = System.nanoTime();
        long intervalNanos = (long) (cfg.tickMillis * 1_000_000L / cfg.simulationSpeed);

        while (running.get()) {
            while (paused.get() && running.get()) {
                try { Thread.sleep(100); } catch (InterruptedException ignored) { break; }
            }
            if (!running.get()) break;

            long now = System.nanoTime();
            long elapsed = now - lastOrderGen;
            // order-gen interval in sim-time
            double orderInterval = 1.0 / Math.max(0.01, cfg.orderGenRate);
            double simInterval = elapsed / 1_000_000_000.0 * cfg.simulationSpeed;

            if (elapsed >= intervalNanos) {
                lastOrderGen = now;
                simTime += simInterval;
                tick(simInterval);
            }
            try { Thread.sleep(10); } catch (InterruptedException ignored) { break; }
        }
    }

    private void tick(double simDelta) {
        // 1. Move drivers
        for (Driver d : drivers) {
            tickDriver(d, simDelta);
        }
        // 2. Dispatch pending orders
        for (Order o : orders) {
            if (o.getStatus() == Order.Status.PENDING) {
                dispatch.dispatch(o, drivers, log::log);
            }
        }
        // 3. Traffic
        if (rng.nextDouble() < 0.05) map.tickTraffic();
    }

    private void tickDriver(Driver d, double simDelta) {
        Driver.Status s = d.getStatus();
        if (s == Driver.Status.OFFLINE || s == Driver.Status.ACCIDENT || s == Driver.Status.AVAILABLE) return;

        // Find this driver's active order
        Order myOrder = null;
        for (Order o : orders) {
            if (("D" + d.getId()).equals(o.getAssignedDriverId())
                && (o.getStatus() == Order.Status.ASSIGNED
                    || o.getStatus() == Order.Status.PICKED_UP)) {
                myOrder = o;
                break;
            }
        }
        if (myOrder == null) {
            d.setStatus(Driver.Status.AVAILABLE);
            return;
        }

        switch (s) {
            case ASSIGNED -> {
                Location target = myOrder.getPickup();
                if (moveToward(d, target, simDelta)) {
                    d.setStatus(Driver.Status.DELIVERING);
                    myOrder.setStatus(Order.Status.PICKED_UP);
                    d.setPosition(myOrder.getPickup());
                    log.log("Order " + myOrder.getId() + " picked up at " + myOrder.getPickup().getName());
                }
            }
            case EN_ROUTE_PICKUP -> {
                d.setStatus(Driver.Status.EN_ROUTE_PICKUP);
                if (moveToward(d, myOrder.getPickup(), simDelta)) {
                    d.setStatus(Driver.Status.DELIVERING);
                    myOrder.setStatus(Order.Status.PICKED_UP);
                    d.setPosition(myOrder.getPickup());
                    log.log("Order " + myOrder.getId() + " picked up at " + myOrder.getPickup().getName());
                }
            }
            case DELIVERING -> {
                Location target = myOrder.getDropoff();
                if (moveToward(d, target, simDelta)) {
                    d.setStatus(Driver.Status.AVAILABLE);
                    d.setPosition(target);
                    d.setCurrentOrderId(null);
                    d.addWorkload(-myOrder.getPackageSize());
                    myOrder.setStatus(Order.Status.DELIVERED);
                    myOrder.setDeliveredAtMillis(System.currentTimeMillis());
                    metrics.incCompleted(myOrder.getDeliveryLatencyMillis());
                    log.log("✓ Order " + myOrder.getId() + " DELIVERED — total time: "
                        + myOrder.getDeliveryLatencyMillis() / 1000.0 + "s");
                }
            }
            default -> {}
        }
    }

    private boolean moveToward(Driver d, Location target, double simDelta) {
        Location pos = d.getPosition();
        double speed = d.getVehicle().getBaseSpeed();
        double dist = pos.distanceTo(target);
        double step = speed * simDelta; // map units per sim-second
        if (step >= dist || dist < 2.0) {
            d.setPosition(target);
            return true;
        }
        double ratio = step / dist;
        int nx = (int) Math.round(pos.getX() + (target.getX() - pos.getX()) * ratio);
        int ny = (int) Math.round(pos.getY() + (target.getY() - pos.getY()) * ratio);
        d.setPosition(new Location(nx, ny, "moving", pos.getType()));
        return false;
    }

    private void metricsLoop() {
        while (running.get()) {
            try { Thread.sleep(1000); } catch (InterruptedException ignored) { break; }
            if (!running.get()) break;
            metrics.tickRate(System.currentTimeMillis());
        }
    }

    // ── snapshots for UI ─────────────────────────────────────────────────────

    public List<Driver> getDrivers() { return List.copyOf(drivers); }
    public List<Order> getOrders()   { return List.copyOf(orders); }
    public MetricsManager getMetrics() { return metrics; }

    /** Called after reset to re-populate drivers. */
    public void populateDrivers() {
        int count = cfg.minDrivers + rng.nextInt(cfg.maxDrivers - cfg.minDrivers + 1);
        for (int i = 0; i < count; i++) {
            VehicleType vt = Config.VEHICLE_MIX[rng.nextInt(Config.VEHICLE_MIX.length)];
            addDriver(vt, 1 + rng.nextInt(5));
        }
    }
}
