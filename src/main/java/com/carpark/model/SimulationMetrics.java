package com.carpark.model;

import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Thread-safe accumulator for simulation statistics displayed on the dashboard.
 * <p>
 * All counters use atomic types so that multiple producer/consumer threads
 * can update them concurrently without additional locking.
 * </p>
 *
 * @author vattanac
 */
public class SimulationMetrics {

    /** Total number of cars produced (entered the gate). */
    private final AtomicInteger totalProduced = new AtomicInteger(0);

    /** Total number of cars consumed (guided to a spot and departed). */
    private final AtomicInteger totalConsumed = new AtomicInteger(0);

    /** Running sum of wait-time milliseconds (for computing the average). */
    private final AtomicLong totalWaitTimeMs = new AtomicLong(0);

    /** Timestamp (epoch ms) when the simulation started. */
    private volatile long startTimeMs;

    // ── Constructor ─────────────────────────────────────────

    /**
     * Creates a new metrics instance with all counters at zero.
     */
    public SimulationMetrics() {
        this.startTimeMs = System.currentTimeMillis();
    }

    // ── Recording methods (called by threads) ───────────────

    /** Increments the "cars produced" counter by one. */
    public void recordProduced() {
        totalProduced.incrementAndGet();
    }

    /**
     * Increments the "cars consumed" counter and accumulates wait time.
     *
     * @param waitTimeMs the wait time (in ms) the car experienced
     */
    public void recordConsumed(long waitTimeMs) {
        totalConsumed.incrementAndGet();
        if (waitTimeMs > 0) {
            totalWaitTimeMs.addAndGet(waitTimeMs);
        }
    }

    // ── Query methods (called by UI) ────────────────────────

    /** @return the total number of cars produced so far */
    public int getTotalProduced() {
        return totalProduced.get();
    }

    /** @return the total number of cars consumed so far */
    public int getTotalConsumed() {
        return totalConsumed.get();
    }

    /**
     * Computes the average wait time across all consumed cars.
     *
     * @return average wait time in milliseconds, or 0 if none consumed
     */
    public double getAverageWaitTimeMs() {
        int consumed = totalConsumed.get();
        if (consumed == 0) return 0.0;
        return totalWaitTimeMs.get() / (double) consumed;
    }

    /**
     * Computes throughput as consumed items per second since simulation start.
     *
     * @return throughput (items/sec)
     */
    public double getThroughput() {
        long elapsed = System.currentTimeMillis() - startTimeMs;
        if (elapsed <= 0) return 0.0;
        return totalConsumed.get() / (elapsed / 1000.0);
    }

    /**
     * Returns the elapsed simulation time in seconds.
     *
     * @return elapsed seconds
     */
    public double getElapsedSeconds() {
        return (System.currentTimeMillis() - startTimeMs) / 1000.0;
    }

    // ── Reset ───────────────────────────────────────────────

    /**
     * Resets all counters and restarts the timer.
     */
    public void reset() {
        totalProduced.set(0);
        totalConsumed.set(0);
        totalWaitTimeMs.set(0);
        startTimeMs = System.currentTimeMillis();
    }

    @Override
    public String toString() {
        return String.format("Metrics[produced=%d, consumed=%d, avgWait=%.0fms, throughput=%.2f/s]",
                getTotalProduced(), getTotalConsumed(),
                getAverageWaitTimeMs(), getThroughput());
    }
}
