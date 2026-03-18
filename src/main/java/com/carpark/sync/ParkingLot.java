package com.carpark.sync;

import com.carpark.model.Car;

import java.time.Instant;
import java.util.LinkedList;
import java.util.Queue;
import java.util.concurrent.Semaphore;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Thread-safe bounded buffer representing the parking lot.
 * <p>
 * Implements the classic Producer-Consumer pattern using:
 * <ul>
 *   <li><b>Semaphores</b> – to track available/occupied slots and prevent
 *       two threads from grabbing the same slot.</li>
 *   <li><b>ReentrantLock + Condition Variables</b> – to signal producers
 *       to sleep when full and wake up when space is available, and vice-versa
 *       for consumers.</li>
 * </ul>
 * The capacity can be changed at runtime via {@link #setCapacity(int)}.
 * </p>
 *
 * @author vattanac
 */
public class ParkingLot {

    /** Default number of parking spaces. */
    private static final int DEFAULT_CAPACITY = 10;

    // ── Fields ──────────────────────────────────────────────

    /** Current maximum number of parking spaces. */
    private volatile int capacity;

    /** Internal FIFO queue of parked cars (the critical section). */
    private final Queue<Car> slots;

    /** Semaphore counting empty spaces (permits = free slots). */
    private Semaphore emptySlots;

    /** Semaphore counting occupied spaces (permits = parked cars). */
    private Semaphore occupiedSlots;

    /** Mutex lock protecting the internal queue. */
    private final ReentrantLock lock;

    /** Condition: signalled when a space becomes available. */
    private final Condition notFull;

    /** Condition: signalled when a car is parked (slot becomes occupied). */
    private final Condition notEmpty;

    // ── Constructors ────────────────────────────────────────

    /**
     * Creates a parking lot with the default capacity.
     */
    public ParkingLot() {
        this(DEFAULT_CAPACITY);
    }

    /**
     * Creates a parking lot with the specified capacity.
     *
     * @param capacity the maximum number of parking spaces; must be &ge; 1
     * @throws IllegalArgumentException if capacity &lt; 1
     */
    public ParkingLot(int capacity) {
        if (capacity < 1) {
            throw new IllegalArgumentException("Capacity must be >= 1, got " + capacity);
        }
        this.capacity = capacity;
        this.slots = new LinkedList<>();
        this.emptySlots = new Semaphore(capacity);
        this.occupiedSlots = new Semaphore(0);
        this.lock = new ReentrantLock(true); // fair lock
        this.notFull = lock.newCondition();
        this.notEmpty = lock.newCondition();
    }

    // ── Producer operation ──────────────────────────────────

    /**
     * Parks a car in the lot (Producer action).
     * <p>
     * Blocks the calling thread if the lot is full, until a space opens up.
     * Uses both the semaphore and the condition variable for synchronisation.
     * </p>
     *
     * @param car the car to park; must not be {@code null}
     * @throws InterruptedException if the thread is interrupted while waiting
     */
    public void parkCar(Car car) throws InterruptedException {
        // ┌──────────────────────────────────────────────────────────┐
        // │  SYNCHRONIZATION: Semaphore blocks if lot is full.      │
        // │  This is equivalent to: wait(emptySlots)                │
        // └──────────────────────────────────────────────────────────┘
        emptySlots.acquire();

        lock.lock(); // ← Mutex: acquire exclusive access to buffer
        try {
            // ╔══════════════════════════════════════════════════════╗
            // ║       CRITICAL SECTION START — Shared Buffer        ║
            // ║  Only one thread can modify the queue at a time.    ║
            // ║  Protected by: ReentrantLock (mutex)                ║
            // ║  Signaling: Condition Variable (notFull / notEmpty) ║
            // ╚══════════════════════════════════════════════════════╝

            // Condition Variable: sleep while buffer is at capacity
            while (slots.size() >= capacity) {
                notFull.await();  // releases lock, sleeps, re-acquires on wake
            }

            car.setParkedAt(Instant.now());
            slots.add(car);  // ← CRITICAL: modifying shared state

            // Condition Variable: wake one waiting Consumer
            notEmpty.signal();

            // ╔══════════════════════════════════════════════════════╗
            // ║       CRITICAL SECTION END                          ║
            // ╚══════════════════════════════════════════════════════╝
        } finally {
            lock.unlock(); // ← Mutex: release exclusive access
        }

        // Signal that one more slot is now occupied
        occupiedSlots.release();
    }

    // ── Consumer operation ──────────────────────────────────

    /**
     * Removes a car from the lot (Consumer / guide action).
     * <p>
     * Blocks the calling thread if the lot is empty, until a car arrives.
     * </p>
     *
     * @return the car that was removed
     * @throws InterruptedException if the thread is interrupted while waiting
     */
    public Car removeCar() throws InterruptedException {
        // ┌──────────────────────────────────────────────────────────┐
        // │  SYNCHRONIZATION: Semaphore blocks if lot is empty.     │
        // │  This is equivalent to: wait(occupiedSlots)             │
        // └──────────────────────────────────────────────────────────┘
        occupiedSlots.acquire();

        Car car;
        lock.lock(); // ← Mutex: acquire exclusive access to buffer
        try {
            // ╔══════════════════════════════════════════════════════╗
            // ║       CRITICAL SECTION START — Shared Buffer        ║
            // ╚══════════════════════════════════════════════════════╝

            // Condition Variable: sleep while buffer is empty
            while (slots.isEmpty()) {
                notEmpty.await();  // releases lock, sleeps, re-acquires on wake
            }

            car = slots.poll();  // ← CRITICAL: modifying shared state

            // Condition Variable: wake one waiting Producer
            notFull.signal();

            // ╔══════════════════════════════════════════════════════╗
            // ║       CRITICAL SECTION END                          ║
            // ╚══════════════════════════════════════════════════════╝
        } finally {
            lock.unlock(); // ← Mutex: release exclusive access
        }

        // Signal that one more slot is now empty
        emptySlots.release();

        return car;
    }

    // ── Getters ─────────────────────────────────────────────

    /** @return the current number of parked cars */
    public int getOccupiedCount() {
        lock.lock();
        try {
            return slots.size();
        } finally {
            lock.unlock();
        }
    }

    /** @return the maximum capacity of the parking lot */
    public int getCapacity() {
        return capacity;
    }

    /** @return the occupancy as a percentage (0–100) */
    public double getOccupancyPercent() {
        return (getOccupiedCount() / (double) capacity) * 100.0;
    }

    // ── Setters ─────────────────────────────────────────────

    /**
     * Dynamically changes the capacity of the parking lot at runtime.
     * <p>
     * If the new capacity is larger, additional semaphore permits are released.
     * If smaller, permits are reduced (existing cars are not evicted).
     * </p>
     *
     * @param newCapacity the new capacity; must be &ge; 1
     * @throws IllegalArgumentException if newCapacity &lt; 1
     */
    public void setCapacity(int newCapacity) {
        if (newCapacity < 1) {
            throw new IllegalArgumentException("Capacity must be >= 1, got " + newCapacity);
        }
        lock.lock();
        try {
            int diff = newCapacity - this.capacity;
            this.capacity = newCapacity;

            if (diff > 0) {
                // More space — release extra empty permits & wake producers
                emptySlots.release(diff);
                notFull.signalAll();
            }
            // If diff < 0, we simply tighten the cap; the semaphore will
            // naturally block once existing permits are consumed.
        } finally {
            lock.unlock();
        }
    }

    // ── Utility ─────────────────────────────────────────────

    /**
     * Returns a snapshot copy of currently parked cars (for UI rendering).
     *
     * @return an array of currently parked {@link Car} objects
     */
    public Car[] getSnapshot() {
        lock.lock();
        try {
            return slots.toArray(new Car[0]);
        } finally {
            lock.unlock();
        }
    }

    @Override
    public String toString() {
        return String.format("ParkingLot[%d/%d]", getOccupiedCount(), capacity);
    }
}
