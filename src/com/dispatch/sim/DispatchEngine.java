package com.dispatch.sim;

import com.dispatch.model.*;
import com.dispatch.model.Driver.Status;

import java.util.*;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.function.Consumer;

/**
 * Core dispatch engine. Scoring formula (all weights configurable via Config):
 *
 *   score = wDistance  * distanceScore
 *         + wTime     * timeScore
 *         + wCapacity * capacityScore
 *         + wWorkload * workloadScore
 *         + wPriority * priorityScore
 *         + wDeadline * deadlineScore
 *
 * distanceScore : 1 - (dist / maxDist), normalised to map diagonal
 * timeScore    : 1 - (travelTime / maxTime), normalised to 120s
 * capacityScore: remaining capacity fraction (0..1)
 * workloadScore: inverse current load (0..1, idle=1)
 * priorityScore: normalised driver priority (0..1)
 * deadlineScore: urgency toward deadline (0..1)
 *
 * Drivers are evaluated under a read-lock; assignment is atomic CAS on Order.
 */
public final class DispatchEngine {
    private final Config cfg;
    private final CityMap map;
    private final MetricsManager metrics;
    private final EventLogger log;
    private final ReadWriteLock rw = new ReentrantReadWriteLock();

    public DispatchEngine(Config cfg, CityMap map, MetricsManager metrics, EventLogger log) {
        this.cfg = cfg;
        this.map = map;
        this.metrics = metrics;
        this.log = log;
    }

    /**
     * Attempts to assign {@code order} to the best available driver.
     * @return the assigned Driver, or null if no driver could take the order.
     */
    public Driver dispatch(Order order, List<Driver> drivers, Consumer<String> onEvent) {
        rw.readLock().lock();
        try {
            List<Candidate> candidates = buildCandidates(order, drivers);
            if (candidates.isEmpty()) {
                onEvent.accept("Dispatch engine: no eligible drivers for " + order.getId());
                return null;
            }
            onEvent.accept("Dispatch engine evaluating " + candidates.size() + " drivers for " + order.getId());

            Candidate best = Collections.max(candidates, Comparator.comparingDouble(Candidate::score));

            // Atomic CAS assignment
            if (!order.compareAndSetStatus(Order.Status.PENDING, Order.Status.ASSIGNED)) {
                onEvent.accept(order.getId() + " was already assigned — skipping.");
                return null;
            }
            order.setAssignedDriverId("D" + best.driver.getId());
            order.setAssignedAtMillis(System.currentTimeMillis());

            best.driver.setStatus(Status.ASSIGNED);
            best.driver.setCurrentOrderId(order.getId());
            best.driver.addWorkload(order.getPackageSize());

            long latency = order.getAssignmentLatencyMillis();
            metrics.recordAssign(latency);
            onEvent.accept("Driver #" + best.driver.getId() + " (" + best.driver.getVehicle().getType()
                + ") assigned to " + order.getId() + " — score=" + String.format("%.3f", best.score)
                + " — dist=" + String.format("%.1f", best.travelDist)
                + " — assign-latency=" + latency + "ms");
            return best.driver;
        } finally {
            rw.readLock().unlock();
        }
    }

    private List<Candidate> buildCandidates(Order order, List<Driver> drivers) {
        double maxDist = Math.sqrt(cfg.mapWidth * cfg.mapWidth + cfg.mapHeight * cfg.mapHeight);
        double maxTime = 120.0; // 2-minute normalisation ceiling

        List<Candidate> out = new ArrayList<>();
        for (Driver d : drivers) {
            if (!d.canTake(order.getPackageSize())) continue;
            double travelDist = d.getPosition().distanceTo(order.getPickup())
                              + order.getPickup().distanceTo(order.getDropoff());
            double travelTime = travelDist / d.getVehicle().getBaseSpeed();

            double distanceScore = 1.0 - Math.min(1.0, travelDist / maxDist);
            double timeScore     = 1.0 - Math.min(1.0, travelTime / maxTime);
            double capacityScore = 1.0 - (double) d.getWorkload() / d.getVehicle().getCapacity();
            double workloadScore = 1.0 - Math.min(1.0, (double) d.getWorkload() / 5.0);
            double priorityScore = (double) d.getPriority() / 5.0;

            // deadline urgency: 1 if very close to deadline, 0 if plenty of time
            double timeLeft = (order.getDeadlineMillis() - System.currentTimeMillis()) / 1000.0;
            double deadlineScore = timeLeft <= 0 ? 1.0 : Math.max(0, 1.0 - timeLeft / 300.0);

            double raw = cfg.wDistance  * distanceScore
                       + cfg.wTime      * timeScore
                       + cfg.wCapacity  * capacityScore
                       + cfg.wWorkload  * workloadScore
                       + cfg.wPriority  * priorityScore
                       + cfg.wDeadline  * deadlineScore;
            out.add(new Candidate(d, raw, travelDist, travelTime,
                distanceScore, timeScore, capacityScore, workloadScore, priorityScore, deadlineScore));
        }
        return out;
    }

    private record Candidate(
        Driver driver, double score,
        double travelDist, double travelTime,
        double distanceScore, double timeScore, double capacityScore,
        double workloadScore, double priorityScore, double deadlineScore
    ) {}
}
