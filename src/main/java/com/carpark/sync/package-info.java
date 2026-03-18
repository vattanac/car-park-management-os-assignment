/**
 * Synchronization primitives for the Car Park Management Simulation.
 *
 * <ul>
 *   <li>{@link com.carpark.sync.ParkingLot} — Thread-safe bounded buffer
 *       implementing the Producer-Consumer pattern using Semaphores,
 *       ReentrantLock (mutex), and Condition Variables.</li>
 *   <li>{@link com.carpark.sync.PetersonLock} — Implementation of Peterson's
 *       classical mutual exclusion algorithm for two threads, demonstrating
 *       software-based synchronization without hardware atomics.</li>
 * </ul>
 *
 * @author vattanac
 */
package com.carpark.sync;
