# SmartDispatch Simulator

A real-time delivery dispatch simulation built with **pure Java + Swing + AWT** — no JavaFX, no Spring, no external libraries.

![Java](https://img.shields.io/badge/Java-25-blue)
![Platform](https://img.shields.io/badge/Platform-Windows%20%7C%20macOS%20%7C%20Linux-green)

---

## Architecture

```
com.dispatch/
├── model/          # Domain objects (immutable where possible, AtomicReference for hot fields)
│   ├── Location.java
│   ├── Road.java
│   ├── Route.java
│   ├── Vehicle.java
│   ├── Driver.java
│   └── Order.java
├── sim/            # Business logic, decoupled from Swing
│   ├── Config.java          # All tunable parameters
│   ├── CityMap.java         # Map generation + road network
│   ├── EventLogger.java     # Thread-safe event log
│   ├── MetricsManager.java  # Live + rolling metrics
│   ├── DispatchEngine.java  # Scoring algorithm
│   └── SimulationEngine.java # Thread coordinator
└── ui/             # Swing views
    ├── MainFrame.java
    ├── MapPanel.java
    ├── DashboardPanel.java
    ├── EventLogPanel.java
    └── ControlPanel.java
```

---

## Algorithms

### Dispatch Scoring Formula

When an order arrives, every **available** driver is scored:

```
score = wDistance  * (1 − dist/maxDist)
       + wTime     * (1 − travelTime/maxTime)
       + wCapacity * (remainingCapacity / maxCapacity)
       + wWorkload * (1 − currentLoad/5)
       + wPriority * (driverPriority / 5)
       + wDeadline * urgency(deadline)
```

- **distance** — normalised to map diagonal; closer = higher
- **travelTime** — dist / vehicle base speed; normalised to 120 s ceiling
- **capacity** — headroom left on the vehicle (0..1)
- **workload** — inverse of current package count; idle drivers preferred
- **driver priority** — driver skill rating 1–5
- **deadline urgency** — 1.0 if near deadline, 0 if plenty of time

The driver with the **highest weighted sum** wins. All weights are configurable
in real-time via sliders.

### Route Finding
A nearest-neighbour greedy path is used (driver → pickup → dropoff).
No full pathfinding graph required for this simulation; the dispatch engine
only needs distance, not a route plan.

---

## Threading Model

| Thread | Responsibility |
|--------|---------------|
| `sim-tick` | Movement, dispatch, traffic tick — runs every ~50 ms |
| `sim-metrics` | Samples metrics every second |
| EDT | All Swing painting and user input |
| EventLogger listeners | Async push to the log panel |

### Race-condition Prevention

| Problem | Solution |
|---------|----------|
| Concurrent driver list reads + mutations | `CopyOnWriteArrayList<Driver>` |
| Order status updates across threads | `AtomicReference<Order.Status>` with `compareAndSet` |
| Driver position updates during paint | AtomicReference + snapshot-per-frame |
| Dispatch reads drivers while tick mutates | `ReadWriteLock` on dispatch engine |
| UI reading while engine writes | Engine exposes immutable `List.copyOf()` snapshots |

---

## Time Complexity

| Operation | Complexity |
|-----------|-----------|
| Dispatch (score all drivers) | O(D) — D = number of drivers |
| Order generation | O(1) |
| Driver movement tick | O(D) |
| Traffic tick | O(R) — R = number of roads |
| Map paint | O(R + L + D) — roads + locations + drivers |

---

## Failure Handling

| Failure | Behaviour |
|---------|-----------|
| Driver goes offline mid-delivery | Order marked FAILED, driver freed |
| Driver accident | Order FAILED, driver → ACCIDENT status |
| Random failure event | Active order randomly chosen and FAILED |
| No eligible driver | Order stays PENDING; re-evaluated next tick |
| Capacity exceeded | Driver skipped in scoring loop |
| Deadline passed | deadlineScore = 1.0 (highest urgency) |

---

## Running

```bash
# Compile
cd src
find . -name "*.java" > sources.txt
javac @sources.txt -d ../out

# Run (from project root)
java -cp out com.dispatch.Main
```

Or open in any Java IDE (IntelliJ, Eclipse, VS Code) and run `com.dispatch.Main`.

---

## Extending to a Distributed Backend

The current architecture keeps business logic in `sim/` and UI in `ui/`. To
extract a backend:

1. **Extract `DispatchEngine`** as a microservice — expose via REST or gRPC.
   The scoring algorithm is pure functions over immutable inputs, ideal for
   stateless pods.

2. **Replace `SimulationEngine` movement** with real GPS telemetry from a
   driver app. The `Driver.Status` state machine maps directly to a
   `Order.Status` workflow.

3. **Replace `CopyOnWriteArrayList`** with an in-memory event store
   (e.g. Kafka + Flink) for ordering guarantees across instances.

4. **Scale dispatch horizontally** — since scoring is read-only per order,
   multiple dispatchers can evaluate in parallel with no coordination
   (just need to CAS-assign the winner).

5. **Add database persistence** for Orders, Drivers, and Vehicles using
   JPA/Hibernate or jOOQ — schema maps 1:1 to the current model classes.

6. **Add a real-time feed** (WebSocket/SSE) from the simulation engine
   so a web dashboard can display the same map and metrics without
   rewriting the business logic.
