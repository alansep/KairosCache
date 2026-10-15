package com.alansep.kairoscache.domain.port;

/**
 * Outbound ports: time and expiry scheduling seams.
 *
 * <p>Nested interfaces keep both seams in the single file mandated by the task plan
 * while staying public across packages.
 */
public final class ExpiryPorts {
    private ExpiryPorts() {
    }

    /**
     * Schedules a generation-guarded expiry fire.
     *
     * @param <K> top-level key type
     */
    public interface ExpirySchedulerPort<K> {
        /**
         * Schedules {@code onFire} after {@code delayMillis}.
         *
         * <p>The callback must evict only when the live entry still carries
         * {@code generation}; otherwise it is a stale-fire no-op.
         *
         * @param key top-level key
         * @param generation entry version captured at schedule time
         * @param delayMillis non-negative delay
         * @param onFire expiry callback
         */
        void schedule(K key, long generation, long delayMillis, Runnable onFire);
    }

    /**
     * Millis clock seam (production: system time; tests: manual clock).
     */
    public interface ClockPort {
        /**
         * Returns the current epoch millis.
         *
         * @return now in millis
         */
        long millis();
    }
}
