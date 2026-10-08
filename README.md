# SmartDispatch Simulator

> ## Status: 🟢 Completed
>
> <progress value="95" max="100"></progress>
>
> **Progress: 95%** — Complete real-time delivery dispatch sim: weighted dispatch scoring, city map generation, live metrics, thread-safe engine, full Swing dashboard. Zero TODOs, cleanest codebase of the batch.

<p align="center">
  <img src="banner.webp" alt="SmartDispatch Simulator banner" width="100%" />
</p>

![Java](https://img.shields.io/badge/Java-25-blue)
![UI](https://img.shields.io/badge/UI-Swing%2FAWT-green)
![Platform](https://img.shields.io/badge/Platform-Windows%20%7C%20macOS%20%7C%20Linux-lightgrey)
![Deps](https://img.shields.io/badge/Deps-none%20(pure%20Java)-brightgreen)

## What it is

SmartDispatch Simulator is a real-time delivery dispatch simulation built with **pure Java + Swing + AWT** — no JavaFX, no Spring, no external libraries. A generated city map spawns delivery orders; the `DispatchEngine` scores every available driver with a weighted formula (distance, travel time, vehicle capacity, workload, driver priority, deadline urgency — all weights tunable live via sliders) and assigns the best one. Vehicles move on a ~50ms sim tick, metrics are sampled every second, and the Swing dashboard shows the map, live stats, event log, and controls. Threading is carefully designed: `CopyOnWriteArrayList` for drivers, `AtomicReference` order states, `ReadWriteLock` on the dispatch engine, immutable snapshots for the UI.

## What works (verified)

- ✅ **Dispatch scoring** — `DispatchEngine` implements the documented weighted formula; all weights adjustable in real time via UI sliders (`Config.java` holds every tunable parameter).
- ✅ **City generation** — `CityMap` builds the map and road network; nearest-neighbour greedy routing (driver → pickup → dropoff).
- ✅ **Simulation engine** — `SimulationEngine` coordinates threads: `sim-tick` (movement/dispatch/traffic, ~50ms), `sim-metrics` (1s sampling), EDT for all painting/input.
- ✅ **Thread safety** — documented race-condition prevention: `CopyOnWriteArrayList<Driver>`, `AtomicReference` order status with `compareAndSet`, snapshot-per-frame driver positions, immutable `List.copyOf()` UI snapshots.
- ✅ **Live metrics** — `MetricsManager` tracks live + rolling metrics; `EventLogger` is a thread-safe event log with async push to the log panel.
- ✅ **Full UI** — `Main.java` (EDT-safe entry) → `MainFrame` + `MapPanel`, `DashboardPanel`, `EventLogPanel`, `ControlPanel`.
- ✅ **Zero TODOs** — cleanest codebase of the batch; grep found no TODO/FIXME/stub markers at all.

*Verified by: reading the full source tree (`model/`, `sim/`, `ui/`), the dispatch scoring formula, the threading model docs, and `Main.java`. Not compiled here — no JDK in this environment; no CI runs on the repo.*

## Tech stack

| Layer | Technology |
|---|---|
| Language | Java 25 |
| UI | Swing / AWT |
| Dispatch | Weighted scoring (distance, time, capacity, workload, priority, deadline) |
| Concurrency | `CopyOnWriteArrayList`, `AtomicReference`, `ReadWriteLock`, immutable snapshots |
| Build | `javac` directly (no build tool, no deps) |
| Platform | Windows, macOS, Linux |

## How to run

```bash
# Requires: Java 25 (badge) — Java 17+ likely works, pure Java with no deps

cd smart-dispatch-simulator

# Compile
find src -name "*.java" > sources.txt
javac -d out @sources.txt

# Run
java -cp out com.dispatch.Main
```

> No `build.sh` or Maven file ships with the repo, so these are the standard `javac` commands for its flat `src/` layout (entry point `com.dispatch.Main`, verified in code). Not executed here — no JDK in this sandbox.

## Screenshots

No screenshots in the repo. The banner above is the generated visual.

## What you can add more

- [ ] **Add a dashboard screenshot** — capture `MainFrame` running and ship it
- [ ] **A* routing option** — replace/augment the greedy nearest-neighbour router for comparison
- [ ] **Traffic model** — congestion that slows vehicles on busy roads, like the sibling sims have
- [ ] **Shift scheduling** — driver shifts, breaks, and end-of-day handover
- [ ] **Export run data** — CSV of deliveries, wait times, and driver utilization per run

## Project structure

```
smart-dispatch-simulator/
├── banner.webp                        # Project banner
└── src/com/dispatch/
    ├── Main.java                      # EDT-safe entry point
    ├── model/      # Location, Road, Route, Vehicle, Driver, Order
    │               #   (immutable where possible, AtomicReference hot fields)
    ├── sim/        # Config (tunables), CityMap, EventLogger,
    │               #   MetricsManager, DispatchEngine (scoring),
    │               #   SimulationEngine (thread coordinator)
    └── ui/         # MainFrame, MapPanel, DashboardPanel,
                    #   EventLogPanel, ControlPanel
```

---
*README written after code audit on 2026-10-08.*
