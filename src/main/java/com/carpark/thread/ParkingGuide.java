package com.carpark.thread;

import com.carpark.model.Car;
import com.carpark.model.SimulationMetrics;
import com.carpark.sync.ParkingLot;

/**
 * Consumer thread that removes (guides out) {@link Car} objects from the parking lot.
 * <p>
 * Each consumer represents a security guard or directional sign that processes
 * a parked car: the "processing time" simulates the work of guiding the car
 * out of its space and clearing it for the next vehicle.
 * </p>
 *
 * @author vattanac
 */
public class ParkingGuide implements Runnable {

    /**
     * Thread status enumeration displayed on the visual dashboard.
     */
    public enum Status { ACTIVE, WAITING, CRASHED }

    // ── Fields ──────────────────────────────────────────────

    private final int guideId;
    private final ParkingLot parkingLot;
    private final SimulationMetrics metrics;

    /** Milliseconds to sleep to simulate "processing work". */
    private volatile int processingTimeMs;

    /** Milliseconds between consumption attempts. */
    private volatile int consumptionDelayMs;

    /** Current visual status of this consumer thread. */
    private volatile Status status;

    /** Flag to gracefully stop the thread. */
    private volatile boolean running;

    // ── Constructor ─────────────────────────────────────────

    /**
     * Creates a new parking guide (consumer).
     *
     * @param guideId           unique identifier for this consumer
     * @param parkingLot        the shared parking-lot buffer
     * @param metrics           shared metrics accumulator
     * @param consumptionDelayMs delay (ms) between consumption attempts
     * @param processingTimeMs  simulated processing/work time (ms)
     * @throws IllegalArgumentException if any argument is invalid
     */
    public ParkingGuide(int guideId, ParkingLot parkingLot,
                        SimulationMetrics metrics,
                        int consumptionDelayMs, int processingTimeMs) {
        if (parkingLot == null || metrics == null) {
            throw new IllegalArgumentException("parkingLot and metrics must not be null");
        }
        this.guideId = guideId;
        this.parkingLot = parkingLot;
        this.metrics = metrics;
        this.consumptionDelayMs = consumptionDelayMs;
        this.processingTimeMs = processingTimeMs;
        this.status = Status.ACTIVE;
        this.running = true;
    }

    // ── Runnable ────────────────────────────────────────────

    @Override
    public void run() {
        try {
            while (running && !Thread.currentThread().isInterrupted()) {
                // Wait for a car to become available
                status = Status.WAITING;
                Car car = parkingLot.removeCar();

                // Simulate processing work (guiding the car out)
                status = Status.ACTIVE;
                Thread.sleep(processingTimeMs);

                // Record metrics
                long waitTime = car.getWaitTimeMs();
                metrics.recordConsumed(waitTime);

                // Delay before looking for the next car
                Thread.sleep(consumptionDelayMs);
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } catch (Exception e) {
            status = Status.CRASHED;
        } finally {
            if (status != Status.CRASHED) {
                status = Status.ACTIVE;
            }
        }
    }

    // ── Getters ─────────────────────────────────────────────

    /** @return the unique identifier of this guide */
    public int getGuideId() {
        return guideId;
    }

    /** @return the current visual status */
    public Status getStatus() {
        return status;
    }

    /** @return the current processing time in ms */
    public int getProcessingTimeMs() {
        return processingTimeMs;
    }

    /** @return the current consumption delay in ms */
    public int getConsumptionDelayMs() {
        return consumptionDelayMs;
    }

    /** @return whether this consumer is still running */
    public boolean isRunning() {
        return running;
    }

    // ── Setters ─────────────────────────────────────────────

    /**
     * Adjusts the processing time at runtime.
     *
     * @param timeMs new processing time in ms; must be &ge; 0
     */
    public void setProcessingTimeMs(int timeMs) {
        if (timeMs < 0) {
            throw new IllegalArgumentException("timeMs must be >= 0");
        }
        this.processingTimeMs = timeMs;
    }

    /**
     * Adjusts the consumption delay at runtime.
     *
     * @param delayMs new delay in ms; must be &ge; 0
     */
    public void setConsumptionDelayMs(int delayMs) {
        if (delayMs < 0) {
            throw new IllegalArgumentException("delayMs must be >= 0");
        }
        this.consumptionDelayMs = delayMs;
    }

    /** Gracefully signals this consumer to stop after the current iteration. */
    public void stop() {
        this.running = false;
    }

    @Override
    public String toString() {
        return String.format("Guide-%d [%s]", guideId, status);
    }
}
