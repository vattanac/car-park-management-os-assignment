package com.carpark.model;

import java.time.Instant;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Represents a Car entity in the Car Park Management Simulation.
 * <p>
 * Each car has a unique ID, a license plate, a colour for visual display,
 * and timestamps to track how long it waited before being parked.
 * </p>
 *
 * @author vattanac
 */
public class Car {

    /** Thread-safe counter to generate unique car IDs. */
    private static final AtomicInteger ID_GENERATOR = new AtomicInteger(0);

    /** Palette of pastel car colours used for the visual grid. */
    private static final String[] COLOURS = {
            "#6699FF", // blue
            "#66DDBB", // mint
            "#BB77EE", // purple
            "#FFD644", // yellow
            "#FF9955", // orange
            "#FF88AA", // pink
            "#44CCBB", // teal
            "#CC9966", // brown
    };

    private final int id;
    private final String licensePlate;
    private final String colour;
    private final Instant createdAt;
    private volatile Instant parkedAt;
    private volatile Instant departedAt;

    /**
     * Constructs a new Car with an auto-generated ID, random licence plate,
     * and a colour chosen from the pastel palette.
     */
    public Car() {
        this.id = ID_GENERATOR.incrementAndGet();
        this.licensePlate = generatePlate(this.id);
        this.colour = COLOURS[this.id % COLOURS.length];
        this.createdAt = Instant.now();
        this.parkedAt = null;
    }

    // ── Getters ─────────────────────────────────────────────

    /** @return the unique numeric identifier of this car */
    public int getId() {
        return id;
    }

    /** @return the randomly generated licence-plate string */
    public String getLicensePlate() {
        return licensePlate;
    }

    /** @return the hex colour string used for rendering */
    public String getColour() {
        return colour;
    }

    /** @return the instant when this car was created (entered the queue) */
    public Instant getCreatedAt() {
        return createdAt;
    }

    /** @return the instant when this car was parked, or {@code null} if still waiting */
    public Instant getParkedAt() {
        return parkedAt;
    }

    /** @return the instant when this car departed, or {@code null} if still parked */
    public Instant getDepartedAt() {
        return departedAt;
    }

    // ── Setters ─────────────────────────────────────────────

    /**
     * Records the moment this car was successfully parked.
     *
     * @param parkedAt the parking timestamp; must not be {@code null}
     * @throws IllegalArgumentException if {@code parkedAt} is null
     */
    public void setParkedAt(Instant parkedAt) {
        if (parkedAt == null) {
            throw new IllegalArgumentException("parkedAt must not be null");
        }
        this.parkedAt = parkedAt;
    }

    /**
     * Records the moment this car departed (was guided out of the lot).
     *
     * @param departedAt the departure timestamp; must not be {@code null}
     * @throws IllegalArgumentException if {@code departedAt} is null
     */
    public void setDepartedAt(Instant departedAt) {
        if (departedAt == null) {
            throw new IllegalArgumentException("departedAt must not be null");
        }
        this.departedAt = departedAt;
    }

    // ── Utility ─────────────────────────────────────────────

    /**
     * Calculates the wait time in milliseconds between creation and parking.
     *
     * @return wait time in ms, or {@code -1} if the car has not been parked yet
     */
    public long getWaitTimeMs() {
        if (parkedAt == null) {
            return -1;
        }
        return parkedAt.toEpochMilli() - createdAt.toEpochMilli();
    }

    /**
     * Calculates how long the car was parked (parking duration) in milliseconds.
     *
     * @return parking duration in ms, or {@code -1} if still parked or never parked
     */
    public long getParkingDurationMs() {
        if (parkedAt == null || departedAt == null) {
            return -1;
        }
        return departedAt.toEpochMilli() - parkedAt.toEpochMilli();
    }

    @Override
    public String toString() {
        return String.format("Car[#%d %s]", id, licensePlate);
    }

    // ── Private helpers ─────────────────────────────────────

    /**
     * Generates a short, readable licence plate based on the car ID.
     *
     * @param id the car's unique numeric ID
     * @return a string like "PP-0042"
     */
    private static String generatePlate(int id) {
        return String.format("PP-%04d", id);
    }
}
