/**
 * <b>Car Park Management Simulation</b> — Producer-Consumer Pattern with Multi-Threading.
 *
 * <p>A JavaFX GUI-based simulation demonstrating Operating System concepts
 * including multi-threading, synchronization, semaphores, mutexes,
 * Peterson's algorithm, and condition variables.</p>
 *
 * <h2>Architecture</h2>
 * <ul>
 *   <li>{@link com.carpark.model} — Domain entities: {@code Car}, {@code SimulationMetrics}</li>
 *   <li>{@link com.carpark.sync} — Synchronization: {@code ParkingLot} (buffer with Semaphores
 *       + Condition Variables), {@code PetersonLock} (Peterson's mutual exclusion)</li>
 *   <li>{@link com.carpark.thread} — Threads: {@code CarProducer} (Producer),
 *       {@code ParkingGuide} (Consumer)</li>
 *   <li>{@link com.carpark.ui} — JavaFX GUI with isometric nighttime parking-lot visualization</li>
 * </ul>
 *
 * <h2>Synchronization Strategy</h2>
 * <p>The {@link com.carpark.sync.ParkingLot} buffer uses a <b>dual-semaphore pattern</b>
 * with condition variables:</p>
 * <ul>
 *   <li>{@code emptySlots} semaphore — counts free parking spaces</li>
 *   <li>{@code occupiedSlots} semaphore — counts parked cars</li>
 *   <li>{@code ReentrantLock} — protects the internal queue (mutex)</li>
 *   <li>{@code notFull / notEmpty} — condition variables that coordinate
 *       producers and consumers</li>
 * </ul>
 * <p>Additionally, {@link com.carpark.sync.PetersonLock} demonstrates Peterson's
 * classical two-process mutual exclusion algorithm for educational purposes.</p>
 *
 * @author vattanac
 * @see com.carpark.sync.ParkingLot
 * @see com.carpark.sync.PetersonLock
 */
package com.carpark;
