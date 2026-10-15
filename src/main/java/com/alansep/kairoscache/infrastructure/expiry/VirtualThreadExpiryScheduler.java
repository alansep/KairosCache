package com.alansep.kairoscache.infrastructure.expiry;

import com.alansep.kairoscache.domain.port.ExpiryPorts.ExpirySchedulerPort;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * Single-scheduler TTL engine on a virtual thread.
 *
 * <p>One scheduler thread total (not one per key — the JCache leak fix). Each schedule
 * captures {@code (key, generation)}; the service callback evicts only on a generation
 * match, so overwritten entries are immune to stale fires. A {@code close()} race that
 * rejects a schedule is swallowed: shutdown means no further expiry work is needed.
 *
 * @param <K> top-level key type
 */
public final class VirtualThreadExpiryScheduler<K>
        implements ExpirySchedulerPort<K>, AutoCloseable {
    private final ScheduledExecutorService scheduler =
            Executors.newSingleThreadScheduledExecutor(Thread.ofVirtual().factory());

    /**
     * Schedules an expiry callback.
     *
     * @param key top-level key
     * @param generation entry version captured at schedule time
     * @param delayMillis non-negative delay
     * @param onFire generation-guarded expiry callback
     */
    @Override
    public void schedule(K key, long generation, long delayMillis, Runnable onFire) {
        try {
            scheduler.schedule(onFire, Math.max(0, delayMillis), TimeUnit.MILLISECONDS);
        } catch (RejectedExecutionException shutdownRace) {
            // Scheduler is shut down: no further expiry work is needed. Swallow.
        }
    }

    /**
     * Shuts the scheduler down immediately.
     */
    @Override
    public void close() {
        scheduler.shutdownNow();
    }
}
