package com.carpark.sync;

/**
 * Implementation of <b>Peterson's Algorithm</b> for mutual exclusion between two threads.
 * <p>
 * Peterson's solution is a classic software-based approach to the critical-section
 * problem that does not require hardware support (no atomic instructions).
 * It guarantees:
 * <ul>
 *   <li><b>Mutual Exclusion</b>: Only one thread can be in the critical section at a time.</li>
 *   <li><b>Progress</b>: If no thread is in the critical section, one can enter.</li>
 *   <li><b>Bounded Waiting</b>: A thread waiting will eventually get access.</li>
 * </ul>
 * <p>
 * <b>Limitation:</b> Peterson's algorithm only works for exactly <em>two</em> threads
 * (process 0 and process 1). For N threads, higher-level primitives like
 * {@link java.util.concurrent.Semaphore} or {@link java.util.concurrent.locks.ReentrantLock}
 * are preferred — which is what {@link ParkingLot} uses for the main buffer.
 * </p>
 * <p>
 * <b>Usage in this project:</b> This class is used as a secondary lock to protect
 * the parking-lot statistics counters, demonstrating Peterson's algorithm alongside
 * the Semaphore + Condition Variable approach in {@link ParkingLot}.
 * </p>
 *
 * <pre>{@code
 *   PetersonLock lock = new PetersonLock();
 *
 *   // Thread 0:
 *   lock.lock(0);
 *   try { // critical section } finally { lock.unlock(0); }
 *
 *   // Thread 1:
 *   lock.lock(1);
 *   try { // critical section } finally { lock.unlock(1); }
 * }</pre>
 *
 * @author vattanac
 * @see ParkingLot
 * @see <a href="https://en.wikipedia.org/wiki/Peterson%27s_algorithm">Peterson's Algorithm (Wikipedia)</a>
 */
public class PetersonLock {

    /**
     * {@code flag[i] == true} means thread {@code i} wants to enter the critical section.
     * <p>
     * Marked {@code volatile} to ensure visibility across threads without caching.
     * </p>
     */
    private volatile boolean[] flag = new boolean[2];

    /**
     * Indicates whose "turn" it is to wait.
     * <p>
     * If both threads want to enter, the one who set {@code turn} last
     * (i.e., was more "polite") must yield and busy-wait.
     * </p>
     */
    private volatile int turn;

    /**
     * Creates a new Peterson lock with both flags initially {@code false}.
     */
    public PetersonLock() {
        flag[0] = false;
        flag[1] = false;
        turn = 0;
    }

    /**
     * Acquires the lock for the given thread ID (0 or 1).
     * <p>
     * The calling thread announces interest by setting its flag, then
     * yields priority by setting {@code turn} to the <em>other</em> thread.
     * It then busy-waits while the other thread both wants the lock
     * AND it is the other thread's turn.
     * </p>
     *
     * @param threadId the thread's ID; must be 0 or 1
     * @throws IllegalArgumentException if threadId is not 0 or 1
     */
    public void lock(int threadId) {
        validateId(threadId);
        int other = 1 - threadId;

        flag[threadId] = true;   // I want to enter
        turn = other;            // But I'll let you go first

        // ── Busy-wait (spin) ──
        // Only proceeds when:
        //   - The other thread does NOT want to enter (flag[other] == false), OR
        //   - It's MY turn (turn == threadId), meaning the other set turn last
        while (flag[other] && turn == other) {
            Thread.yield(); // Hint to scheduler: allow other threads to run
        }

        // ═══ CRITICAL SECTION BEGINS after this point ═══
    }

    /**
     * Releases the lock for the given thread ID.
     * <p>
     * Simply clears the thread's flag, allowing the other thread
     * (if waiting) to proceed into the critical section.
     * </p>
     *
     * @param threadId the thread's ID; must be 0 or 1
     * @throws IllegalArgumentException if threadId is not 0 or 1
     */
    public void unlock(int threadId) {
        validateId(threadId);
        flag[threadId] = false;  // I no longer need the critical section
        // ═══ CRITICAL SECTION ENDS ═══
    }

    /**
     * Checks whether the given thread currently holds the lock
     * (i.e., its flag is set and it's not the other thread's turn).
     *
     * @param threadId the thread's ID; must be 0 or 1
     * @return {@code true} if the thread appears to hold the lock
     */
    public boolean isHeldBy(int threadId) {
        validateId(threadId);
        int other = 1 - threadId;
        return flag[threadId] && !(flag[other] && turn == other);
    }

    /**
     * Validates that the thread ID is 0 or 1.
     *
     * @param threadId the ID to validate
     * @throws IllegalArgumentException if not 0 or 1
     */
    private void validateId(int threadId) {
        if (threadId < 0 || threadId > 1) {
            throw new IllegalArgumentException(
                    "Peterson's algorithm supports exactly 2 threads. "
                    + "Thread ID must be 0 or 1, got: " + threadId);
        }
    }

    @Override
    public String toString() {
        return String.format("PetersonLock[flag0=%b, flag1=%b, turn=%d]",
                flag[0], flag[1], turn);
    }
}
