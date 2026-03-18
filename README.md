# Car Park Management Sim

A JavaFX GUI-based simulation of automated car park management, demonstrating **Producer-Consumer threading**, **Semaphores**, **Mutexes**, **Peterson's Algorithm**, and **Condition Variables**.

Built for the **Operating System** course — Royal University of Phnom Penh, Faculty of Engineering, Department of IT Engineering, Master of Data Science and Engineering.

---

## Overview

The simulation models a parking lot as a **bounded buffer** where:

- **Producers** (car generators) create cars at configurable rates and attempt to park them
- **Consumers** (parking guides) remove cars from the lot, simulate processing, and release them
- The **buffer** (parking lot) has a fixed capacity that can be adjusted at runtime

The entire system runs with real Java threads, synchronized using OS-level primitives.

---

## Architecture

```
com.carpark
├── model/
│   ├── Car.java                 # Vehicle entity with timestamps
│   └── SimulationMetrics.java   # Thread-safe stats (AtomicInteger/Long)
├── sync/
│   ├── ParkingLot.java          # Bounded buffer (Semaphore + Lock + Condition)
│   └── PetersonLock.java        # Peterson's mutual exclusion algorithm
├── thread/
│   ├── CarProducer.java         # Producer thread (generates cars)
│   └── ParkingGuide.java        # Consumer thread (guides cars out)
└── ui/
    └── CarParkSimApp.java       # JavaFX GUI with isometric rendering
```

---

## Synchronization Strategy

### Primary: Semaphore + ReentrantLock + Condition Variables

`ParkingLot.java` uses a **dual-semaphore pattern**:

- `emptySlots` semaphore — blocks producers when the lot is full
- `occupiedSlots` semaphore — blocks consumers when the lot is empty
- `ReentrantLock` — mutex protecting the internal queue (critical section)
- `notFull` / `notEmpty` — condition variables that signal threads to sleep/wake

```
Producer                          Consumer
────────                          ────────
emptySlots.acquire()              occupiedSlots.acquire()
lock.lock()                       lock.lock()
  ┌─ CRITICAL SECTION ─┐           ┌─ CRITICAL SECTION ─┐
  │ while(full) await   │           │ while(empty) await  │
  │ queue.add(car)      │           │ car = queue.poll()  │
  │ notEmpty.signal()   │           │ notFull.signal()    │
  └─────────────────────┘           └─────────────────────┘
lock.unlock()                     lock.unlock()
occupiedSlots.release()           emptySlots.release()
```

### Secondary: Peterson's Algorithm

`PetersonLock.java` implements Peterson's classical two-process mutual exclusion using:

- `flag[i]` — indicates thread `i` wants to enter the critical section
- `turn` — determines who yields when both want access
- Busy-wait with `Thread.yield()` for cooperative scheduling

This demonstrates software-based synchronization without hardware atomics.

---

## Thread Safety Mechanisms

| Mechanism | Where Used | Purpose |
|-----------|-----------|---------|
| `Semaphore` | ParkingLot | Counting permits for empty/occupied slots |
| `ReentrantLock` | ParkingLot | Mutex for the shared queue |
| `Condition` | ParkingLot | Sleep/wake signaling between producers and consumers |
| `volatile` | CarProducer, ParkingGuide | Thread-safe visibility of status and rate fields |
| `AtomicInteger/Long` | SimulationMetrics | Lock-free counter updates |
| `PetersonLock` | PetersonLock | Classical two-process mutual exclusion |

---

## Features

### Visual Dashboard (Right Panel)

- **Occupancy %** — color-coded (green < 60%, yellow < 90%, red >= 90%)
- **Throughput** — cars processed per second
- **Avg Wait Time** — time cars spend waiting before parking
- **Avg Park Duration** — time cars spend parked before departing
- **Produced / Consumed** — running totals
- **Elapsed Time** — simulation duration
- **Thread Status** — per-thread indicators with distinct icons and colors

### Real-Time Controls

All adjustable while the simulation is running:

| Control | Range | Effect |
|---------|-------|--------|
| Buffer Capacity | 1-18 | Number of parking spaces |
| Production Rate | 200-5000 ms | Delay between car arrivals |
| Consumption Rate | 200-5000 ms | Delay between car departures |
| Processing Time | 100-5000 ms | Simulated work time per car |

Each control has both a **slider** and an **editable text field** for precise input.

### Isometric Nighttime Scene

The parking lot is rendered as an isometric nighttime diorama with a dark sky, twinkling stars, glowing moon, building silhouette with lit windows, street lamps with warm flickering glow, pine trees, road with center dashes, white parking-line markings, and modern car models with LED headlights, tail lights, glass cabin, and detailed wheels.

### Car Driving Animations

- Cars **drive in from the road** to their assigned parking slot (two-phase animation with easing)
- When the lot is full, cars **queue on the road** visibly waiting
- When consumed, cars **drive out** from the slot back down the road
- Headlights glow while driving, dim when parked

---

## How to Run

### Prerequisites

- **JDK 17+** (tested with JDK 17 and 21)
- **Maven 3.8+**

### Build and Run

```bash
cd CarParkManagementSim
mvn javafx:run
```

The application starts maximized and the canvas auto-resizes to fill available space.

### Quick Demo

1. Click **Start** to watch cars drive in from the road and park
2. Set Capacity to **5** and Prod Rate to **500ms** to see the queue build up
3. Increase Cons Rate to **200ms** to watch cars flow out faster
4. Click **Stop** and **Reset** to restart

---

## Improvements Made

### 1. Peterson's Algorithm Implementation (Synchronization - 15%)

Added `PetersonLock.java` demonstrating the classical two-process mutual exclusion algorithm, complementing the Semaphore + Condition Variable approach. Fully documented with Javadoc explaining mutual exclusion, progress, and bounded waiting guarantees.

### 2. Explicit Critical Section Marking (Synchronization - 15%)

Added clear visual comment blocks in `ParkingLot.java` marking exactly where the critical section starts and ends, with annotations documenting which synchronization primitive protects the section and why.

### 3. Car Lifecycle Logging (Functional Correctness - 40%)

The event log now shows individual car events such as "Car #12 driving to slot P3" and "Car #12 departing from P3". This makes thread-car interaction visible and verifies synchronization correctness.

### 4. Parking Duration Metric (Functional Correctness - 40%)

Added `departedAt` timestamp to `Car`, `getParkingDurationMs()` method, and "Avg Park Duration" on the dashboard. Tracks the complete car lifecycle: creation, wait, park, depart.

### 5. Package-Level Documentation (Software Engineering Quality - 10%)

Added `package-info.java` for all four packages, documenting the architectural overview, class responsibilities, synchronization strategy summary, and cross-references between related classes.

### 6. Improved Thread Status Icons (Visual Dashboard)

Replaced generic colored dots with distinct Unicode icons for better visual clarity in the thread status panel.

---

## Project Files

| File | Purpose |
|------|---------|
| `Car.java` | Vehicle model with ID, plate, timestamps, wait/duration calculation |
| `SimulationMetrics.java` | Thread-safe stats with AtomicInteger/Long |
| `ParkingLot.java` | Bounded buffer with Semaphore + Lock + Condition |
| `PetersonLock.java` | Peterson's mutual exclusion for 2 threads |
| `CarProducer.java` | Producer thread with configurable rate |
| `ParkingGuide.java` | Consumer thread with processing time |
| `CarParkSimApp.java` | JavaFX GUI, isometric renderer, animation engine |
| `package-info.java` (x4) | Package documentation |
| `module-info.java` | Java module descriptor |
| `pom.xml` | Maven build with JavaFX 21 |

---

## Author

**vattanac** — RUPP, Master of Data Science and Engineering, 2026
