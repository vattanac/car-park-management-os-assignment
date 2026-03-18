# Car Park Management Sim

A JavaFX GUI-based simulation of automated car park management, demonstrating **Producer-Consumer threading**, **Semaphores**, **Mutexes**, **Peterson's Algorithm**, **Condition Variables**, **deadlock detection**, and **dynamic thread management**.

Built for the **Operating System** course — Royal University of Phnom Penh, Faculty of Engineering, Department of IT Engineering, Master of Data Science and Engineering.

---

## Overview

The simulation models a parking lot as a **bounded buffer** where:

- **Producers** (car generators) create cars at configurable rates and attempt to park them
- **Consumers** (parking guides) remove cars from the lot, simulate processing, and release them
- The **buffer** (parking lot) has a fixed capacity that can be adjusted at runtime

Cars are visualized driving in from a road, queuing when the lot is full, parking in assigned slots, and driving out when consumed — all rendered in an isometric nighttime scene.

---

## Architecture

```
com.carpark
├── model/
│   ├── Car.java                 # Vehicle entity with timestamps
│   ├── SimulationMetrics.java   # Thread-safe stats (AtomicInteger/Long)
│   └── package-info.java        # Package documentation
├── sync/
│   ├── ParkingLot.java          # Bounded buffer (Semaphore + Lock + Condition)
│   ├── PetersonLock.java        # Peterson's mutual exclusion algorithm
│   └── package-info.java        # Package documentation
├── thread/
│   ├── CarProducer.java         # Producer thread (generates cars)
│   ├── ParkingGuide.java        # Consumer thread (guides cars out)
│   └── package-info.java        # Package documentation
├── ui/
│   └── CarParkSimApp.java       # JavaFX GUI with isometric rendering
└── package-info.java            # Root package documentation
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

**Live Demo:** [Peterson's Algorithm Visualization](https://peterson-algorithm.netlify.app/)

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
- **Thread Status** — per-thread indicators with distinct icons (lightning = Active, pause = Waiting, cross = Crashed)

### Live Occupancy Graph

A real-time line chart showing occupancy % over the last 60 seconds. Blue line with gradient fill, grid lines at 25/50/75%, and a red 100% threshold line. Proves the system reaches steady-state and shows the effect of parameter changes visually.

### Dynamic Thread Management

Add or remove Producer and Consumer threads while the simulation is running using `+` and `-` buttons. This demonstrates true multi-threading capabilities — flood the lot by adding producers, or drain it by adding consumers. Thread count is displayed live.

### Deadlock Detection

Automatic monitoring for system stalls. If no progress occurs for several seconds, the system checks whether all threads are blocked and displays warnings:

- "DEADLOCK DETECTED — All threads blocked!" (all producers AND consumers waiting)
- "Producers blocked — lot is full" (all producers waiting, consumers active)
- "Consumers blocked — lot is empty" (all consumers waiting, producers active)

### Speed Control (1x / 2x / 4x / 8x)

Speed multiplier buttons to fast-forward the simulation. At 8x speed, cars zip in and out rapidly. Internally divides all thread delays by the multiplier. Useful during presentations to quickly demonstrate steady-state behavior.

### Real-Time Controls

All adjustable while the simulation is running:

| Control | Range | Effect |
|---------|-------|--------|
| Buffer Capacity | 1-18 | Number of parking spaces |
| Production Rate | 200-5000 ms | Delay between car arrivals |
| Consumption Rate | 200-5000 ms | Delay between car departures |
| Processing Time | 100-5000 ms | Simulated work time per car |

Each control has both a **slider** and an **editable text field** — type exact values and press Enter.

### Isometric Nighttime Scene

The parking lot is rendered as an isometric nighttime diorama featuring a dark sky gradient with twinkling animated stars, a glowing moon with halo effect, a building silhouette with lit and unlit windows, street lamps with warm flickering glow halos, pine trees at each corner, a road with yellow center dashes and white edge lines, white parking-line markings per slot, and modern car models with LED headlights, DRL strips, tail light bars, sloped windshields, glass cabins, side mirrors, front grilles, and detailed wheels with rims.

### Car Driving Animations

- Cars **drive in from the road** to their assigned parking slot (two-phase animation with smoothstep easing)
- When the lot is full, cars **queue on the road** visibly waiting with a "X waiting" counter
- When consumed, cars **drive out** from the slot back down the road
- Headlights glow while driving, dim when parked
- Cars are **depth-sorted** for correct isometric overlap

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
2. Set Capacity to **5** and Prod Rate to **500ms** to see the queue build up on the road
3. Click **+** on Producers to add a 4th producer — watch the queue grow faster
4. Switch to **4x** speed to fast-forward
5. Click **+** on Consumers twice — watch the lot drain and occupancy graph drop
6. Set Consumers to 1 and wait — see the "Producers blocked" deadlock warning appear
7. Click **Stop** and **Reset** to restart

---

## Improvements

### Round 1: Assignment Requirements

#### 1. Peterson's Algorithm Implementation (Synchronization - 15%)

Added `PetersonLock.java` demonstrating the classical two-process mutual exclusion algorithm, complementing the Semaphore + Condition Variable approach. Fully documented with Javadoc explaining mutual exclusion, progress, and bounded waiting guarantees.

#### 2. Explicit Critical Section Marking (Synchronization - 15%)

Added clear visual comment blocks in `ParkingLot.java` marking exactly where the critical section starts and ends, with annotations documenting which synchronization primitive protects the section and why.

#### 3. Car Lifecycle Logging (Functional Correctness - 40%)

The event log shows individual car events such as "Car #12 driving to slot P3" and "Car #12 departing from P3". Makes thread-car interaction visible and verifies synchronization correctness.

#### 4. Parking Duration Metric (Functional Correctness - 40%)

Added `departedAt` timestamp to `Car`, `getParkingDurationMs()` method, and "Avg Park Duration" on the dashboard. Tracks the complete car lifecycle: creation, wait, park, depart.

#### 5. Package-Level Documentation (Software Engineering Quality - 10%)

Added `package-info.java` for all four packages, documenting the architectural overview, class responsibilities, synchronization strategy summary, and cross-references between related classes.

#### 6. Improved Thread Status Icons (Visual Dashboard)

Replaced generic colored dots with distinct Unicode icons for better visual clarity in the thread status panel.

### Round 2: Advanced Features

#### 7. Live Occupancy Graph

Real-time line chart tracking occupancy % over the last 60 seconds with gradient fill, grid lines, and a red 100% threshold. Visually proves the system reaches steady-state.

#### 8. Dynamic Thread Add/Remove

`+` and `-` buttons for both Producers and Consumers, allowing threads to be added or removed while the simulation is running. Demonstrates true dynamic multi-threading and shows how the system adapts in real-time.

#### 9. Deadlock Detection

Automatic monitoring that detects when all threads are blocked (no progress for 6+ seconds). Displays context-aware warnings distinguishing full deadlock from partial blocks (producers-only or consumers-only).

#### 10. Speed Control (1x / 2x / 4x / 8x)

Speed multiplier buttons that divide all thread delays by the selected factor. Allows fast-forwarding during presentations to quickly reach steady-state or demonstrate edge cases.

---

## Project Files

| File | Purpose |
|------|---------|
| `Car.java` | Vehicle model with ID, plate, creation/park/depart timestamps |
| `SimulationMetrics.java` | Thread-safe stats: throughput, wait time, parking duration |
| `ParkingLot.java` | Bounded buffer with Semaphore + Lock + Condition Variables |
| `PetersonLock.java` | Peterson's mutual exclusion algorithm for 2 threads |
| `CarProducer.java` | Producer thread with configurable rate and status tracking |
| `ParkingGuide.java` | Consumer thread with processing time and departure recording |
| `CarParkSimApp.java` | JavaFX GUI: isometric renderer, animation engine, dashboard, graph, deadlock detection, dynamic threads, speed control |
| `package-info.java` (x4) | Package-level architectural documentation |
| `module-info.java` | Java module descriptor |
| `pom.xml` | Maven build with JavaFX 21 dependencies |

---

## Author

**vattanac** — RUPP, Master of Data Science and Engineering, 2026
