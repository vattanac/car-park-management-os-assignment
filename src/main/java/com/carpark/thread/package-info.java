/**
 * Thread classes for the Car Park Management Simulation.
 *
 * <p>Each Producer and Consumer runs on its own {@link Thread}.</p>
 * <ul>
 *   <li>{@link com.carpark.thread.CarProducer} — Producer thread that generates
 *       {@link com.carpark.model.Car} objects at a configurable rate and parks them
 *       in the shared {@link com.carpark.sync.ParkingLot} buffer.</li>
 *   <li>{@link com.carpark.thread.ParkingGuide} — Consumer thread that removes
 *       cars from the lot, simulates processing work, and records departure metrics.</li>
 * </ul>
 *
 * <p>Thread status is exposed via {@code getStatus()} returning
 * {@code ACTIVE}, {@code WAITING}, or {@code CRASHED} for the visual dashboard.</p>
 *
 * @author vattanac
 */
package com.carpark.thread;
