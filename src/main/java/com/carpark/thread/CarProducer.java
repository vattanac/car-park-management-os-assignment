package com.carpark.thread;

import com.carpark.model.Car;
import com.carpark.model.SimulationMetrics;
import com.carpark.sync.ParkingLot;

/**
 * Producer thread that generates {@link Car} objects and attempts to park them.
 * <p>
 * Each producer runs on its own thread and sleeps for a configurable interval
 * between producing cars. The production rate can be adjusted at runtime.
 * </p>
 *
 * @author vattanac
 */
public class CarProducer implements Runnable {

    /**
     * Thread status enumeration displayed on the visual dashboard.
     */
    public enum Status { ACTIVE, WAITING, CRASHED }

    // ── Fields ──────────────────────────────────────────────

    private final int producerId;
    private final ParkingLot parkingLot;
    private final SimulationMetrics metrics;

    /** Milliseconds to sleep between producing cars. */
    private volatile int productionDelayMs;

    /** Current visual status of this producer thread. */
    private volatile Status status;

    /** Flag to gracefully stop the thread. */
    private volatile boolean running;

    // ── Constructor ─────────────────────────────────────────

    /**
     * Creates a new car producer.
     *
     * @param producerId        unique identifier for this producer
     * @param parkingLot        the shared parking-lot buffer
     * @param metrics           shared metrics accumulator
     * @param productionDelayMs initial delay (ms) between producing cars
     * @throws IllegalArgumentException if any argument is invalid
     */
    public CarProducer(int producerId, ParkingLot parkingLot,
                       SimulationMetrics metrics, int productionDelayMs) {
        if (parkingLot == null || metrics == null) {
            throw new IllegalArgumentException("parkingLot and metrics must not be null");
        }
        if (productionDelayMs < 0) {
            throw new IllegalArgumentException("productionDelayMs must be >= 0");
        }
        this.producerId = producerId;
        this.parkingLot = parkingLot;
        this.metrics = metrics;
        this.productionDelayMs = productionDelayMs;
        this.status = Status.ACTIVE;
        this.running = true;
    }

    // ── Runnable ────────────────────────────────────────────

    @Override
    public void run() {
        try {
            while (running && !Thread.currentThread().isInterrupted()) {
                // Simulate "driving to the car park" delay
                status = Status.ACTIVE;
                Thread.sleep(productionDelayMs);

                // Create a new car and attempt to park it
                Car car = new Car();
                status = Status.WAITING; // waiting for an empty slot
                parkingLot.parkCar(car);

                // Record in metrics
                metrics.recordProduced();
                status = Status.ACTIVE;
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } catch (Exception e) {
            status = Status.CRASHED;
        } finally {
            if (status != Status.CRASHED) {
                status = Status.ACTIVE; // cleanly stopped
            }
        }
    }

    // ── Getters ─────────────────────────────────────────────

    /** @return the unique identifier of this producer */
    public int getProducerId() {
        return producerId;
    }

    /** @return the current visual status */
    public Status getStatus() {
        return status;
    }

    /** @return the current production delay in milliseconds */
    public int getProductionDelayMs() {
        return productionDelayMs;
    }

    /** @return whether this producer is still running */
    public boolean isRunning() {
        return running;
    }

    // ── Setters ─────────────────────────────────────────────

    /**
     * Adjusts the production rate at runtime.
     *
     * @param delayMs new delay in milliseconds; must be &ge; 0
     */
    public void setProductionDelayMs(int delayMs) {
        if (delayMs < 0) {
            throw new IllegalArgumentException("delayMs must be >= 0");
        }
        this.productionDelayMs = delayMs;
    }

    /** Gracefully signals this producer to stop after the current iteration. */
    public void stop() {
        this.running = false;
    }

    @Override
    public String toString() {
        return String.format("Producer-%d [%s]", producerId, status);
    }
}
