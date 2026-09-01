package com.dispatch.sim;

import com.dispatch.model.Location;
import com.dispatch.model.Location.LocationType;
import com.dispatch.model.Road;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * The simulated city. Holds locations and roads, generates them randomly.
 * Locations and roads are COW lists so the painter can iterate while
 * other threads mutate (e.g. traffic factor updates).
 */
public final class CityMap {
    private final Config cfg;
    private final Random rng = new Random(42);
    private final CopyOnWriteArrayList<Location> locations = new CopyOnWriteArrayList<>();
    private final CopyOnWriteArrayList<Road> roads = new CopyOnWriteArrayList<>();

    public CityMap(Config cfg) { this.cfg = cfg; }

    public List<Location> getLocations() { return Collections.unmodifiableList(locations); }
    public List<Road> getRoads() { return Collections.unmodifiableList(roads); }

    public List<Location> getRestaurants() {
        return locations.stream().filter(l -> l.getType() == LocationType.RESTAURANT).toList();
    }
    public List<Location> getWarehouses() {
        return locations.stream().filter(l -> l.getType() == LocationType.WAREHOUSE).toList();
    }
    public List<Location> getCustomers() {
        return locations.stream().filter(l -> l.getType() == LocationType.CUSTOMER).toList();
    }
    public List<Location> getDepots() {
        return locations.stream().filter(l -> l.getType() == LocationType.DEPOT).toList();
    }

    public void generate() {
        locations.clear();
        roads.clear();
        int n = cfg.minLocations + rng.nextInt(Math.max(1, cfg.maxLocations - cfg.minLocations + 1));
        int width = cfg.mapWidth;
        int height = cfg.mapHeight;

        // 1 depot, several restaurants/warehouses, the rest customers.
        addLocation(60, height / 2, "Depot", LocationType.DEPOT);
        int pickupCount = Math.max(2, n / 5);
        int customerCount = n - pickupCount - 1;
        String[] foods = {"Sushi", "Pizza", "Tacos", "Curry", "Pasta", "Burgers", "Ramen", "Salad", "BBQ", "Pho"};
        for (int i = 0; i < pickupCount; i++) {
            int x = 80 + rng.nextInt(width / 2 - 80);
            int y = 40 + rng.nextInt(height - 80);
            String name = (i % 2 == 0 ? "R-" : "W-") + foods[i % foods.length] + (i / foods.length + 1);
            addLocation(x, y, name, i % 2 == 0 ? LocationType.RESTAURANT : LocationType.WAREHOUSE);
        }
        for (int i = 0; i < customerCount; i++) {
            int x = width / 2 + 20 + rng.nextInt(width / 2 - 40);
            int y = 30 + rng.nextInt(height - 60);
            addLocation(x, y, "Cust-" + (i + 1), LocationType.CUSTOMER);
        }

        // Build a sparse road network. Each non-customer connects to its
        // 2 nearest customers, plus a handful of cross-links for variety.
        for (Location src : locations) {
            if (src.getType() == LocationType.CUSTOMER) continue;
            List<Location> sorted = new ArrayList<>(getCustomers());
            sorted.sort((a, b) -> Double.compare(src.distanceTo(a), src.distanceTo(b)));
            for (int k = 0; k < Math.min(3, sorted.size()); k++) {
                addRoad(src, sorted.get(k));
            }
        }
        // Random cross-links between customers for visual variety
        List<Location> custs = getCustomers();
        for (int i = 0; i < custs.size() / 2; i++) {
            Location a = custs.get(rng.nextInt(custs.size()));
            Location b = custs.get(rng.nextInt(custs.size()));
            if (a != b) addRoad(a, b);
        }
    }

    private void addLocation(int x, int y, String name, LocationType type) {
        locations.add(new Location(x, y, name, type));
    }

    private void addRoad(Location a, Location b) {
        // Avoid duplicates
        for (Road r : roads) {
            if ((r.getA() == a && r.getB() == b) || (r.getA() == b && r.getB() == a)) return;
        }
        roads.add(new Road(a, b));
    }

    /** Reset all traffic factors back to free-flow. */
    public void clearTraffic() {
        for (Road r : roads) r.setTrafficFactor(1.0);
    }

    /** Apply a small probability of traffic spikes. */
    public void tickTraffic() {
        for (Road r : roads) {
            if (rng.nextDouble() < 0.02 * cfg.trafficIntensity) {
                r.setTrafficFactor(1.0 + rng.nextDouble() * 3.0 * cfg.trafficIntensity);
            } else {
                // gradual decay
                double f = r.getTrafficFactor();
                if (f > 1.0) r.setTrafficFactor(Math.max(1.0, f * 0.97));
            }
        }
    }

    public Location randomPickup() {
        List<Location> ps = new ArrayList<>();
        for (Location l : locations) {
            if (l.getType() == LocationType.RESTAURANT || l.getType() == LocationType.WAREHOUSE) ps.add(l);
        }
        return ps.isEmpty() ? null : ps.get(rng.nextInt(ps.size()));
    }
    public Location randomDropoff() {
        List<Location> cs = getCustomers();
        return cs.isEmpty() ? null : cs.get(rng.nextInt(cs.size()));
    }
}
