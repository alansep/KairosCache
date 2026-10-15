package com.alansep.kairoscache.application;

import com.alansep.kairoscache.domain.model.Outcome;
import com.alansep.kairoscache.domain.model.ReadOutcome;
import com.alansep.kairoscache.domain.model.Stats;
import com.alansep.kairoscache.domain.service.CacheService;
import com.alansep.kairoscache.infrastructure.expiry.VirtualThreadExpiryScheduler;
import com.alansep.kairoscache.infrastructure.memory.InMemoryCacheAdapter;
import com.alansep.kairoscache.infrastructure.time.SystemClockAdapter;
import java.time.Clock;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Public facade: the sole user-facing entry point (thread-safe, zero runtime dependencies).
 *
 * <p>Usage:
 * <pre>{@code
 * var cache = KairosCache.<String, String, String>create();
 * cache.put("user:1", "Alan", 0);
 * cache.hput("order:99", "status", "OPEN", 0);
 * cache.close();
 * }</pre>
 *
 * @param <K> top-level key type
 * @param <F> hash field type
 * @param <V> value type
 */
public final class KairosCache<K, F, V> implements AutoCloseable {
    private final CacheService<K, F, V> service;
    private final VirtualThreadExpiryScheduler<K> scheduler;
    private final AtomicBoolean closed = new AtomicBoolean(false);

    private KairosCache(CacheService<K, F, V> service, VirtualThreadExpiryScheduler<K> scheduler) {
        this.service = service;
        this.scheduler = scheduler;
    }

    /**
     * Creates a cache on the system clock.
     *
     * @param <K> key type
     * @param <F> field type
     * @param <V> value type
     * @return new cache
     */
    public static <K, F, V> KairosCache<K, F, V> create() {
        return create(Clock.systemUTC());
    }

    /**
     * Creates a cache on the given clock (visible for tests: inject a fixed/manual clock
     * for deterministic TTL assertions without real sleeps).
     *
     * @param clock time source
     * @param <K> key type
     * @param <F> field type
     * @param <V> value type
     * @return new cache
     */
    public static <K, F, V> KairosCache<K, F, V> create(Clock clock) {
        Objects.requireNonNull(clock, "clock");
        InMemoryCacheAdapter<K, F, V> memory = new InMemoryCacheAdapter<>();
        VirtualThreadExpiryScheduler<K> scheduler = new VirtualThreadExpiryScheduler<>();
        CacheService<K, F, V> service =
                new CacheService<>(memory, scheduler, new SystemClockAdapter(clock));
        return new KairosCache<>(service, scheduler);
    }

    /**
     * Stores a simple entry (upsert; never returns the previous value).
     *
     * @param key top-level key
     * @param value payload
     * @param ttlMillis {@code 0} persistent, {@code >0} whole millis, {@code <0} invalid
     * @return outcome
     */
    public Outcome<V> put(K key, V value, long ttlMillis) {
        ensureOpen();
        return service.put(key, value, ttlMillis);
    }

    /**
     * Reads a simple entry.
     *
     * @param key top-level key
     * @return {@code OK} with value, {@code NOT_FOUND}, {@code TYPE_MISMATCH}, or
     *         {@code INVALID_ARGUMENT}
     */
    public ReadOutcome<V> get(K key) {
        ensureOpen();
        return service.get(key);
    }

    /**
     * Removes a whole top-level entry (idempotent).
     *
     * @param key top-level key
     * @return {@code OK} or {@code INVALID_ARGUMENT}
     */
    public Outcome<Void> evict(K key) {
        ensureOpen();
        return service.evict(key);
    }

    /**
     * Stores one hash field (creates the entry on first field; TTL governs the whole key).
     *
     * @param key top-level key
     * @param field field name
     * @param value payload
     * @param ttlMillis {@code 0} persistent, {@code >0} whole millis, {@code <0} invalid
     * @return outcome
     */
    public Outcome<Void> hput(K key, F field, V value, long ttlMillis) {
        ensureOpen();
        return service.hput(key, field, value, ttlMillis);
    }

    /**
     * Reads one hash field.
     *
     * @param key top-level key
     * @param field field name
     * @return {@code OK}, {@code NOT_FOUND}, {@code TYPE_MISMATCH}, or {@code INVALID_ARGUMENT}
     */
    public ReadOutcome<V> hget(K key, F field) {
        ensureOpen();
        return service.hget(key, field);
    }

    /**
     * Reads all hash fields as a snapshot copy.
     *
     * @param key top-level key
     * @return {@code OK} with an unmodifiable copy, or miss/mismatch/invalid
     */
    public ReadOutcome<Map<F, V>> hgetAll(K key) {
        ensureOpen();
        return service.hgetAll(key);
    }

    /**
     * Removes one hash field (idempotent; siblings intact).
     *
     * @param key top-level key
     * @param field field name
     * @return {@code OK}, {@code TYPE_MISMATCH}, or {@code INVALID_ARGUMENT}
     */
    public Outcome<Void> hevict(K key, F field) {
        ensureOpen();
        return service.hevict(key, field);
    }

    /**
     * Returns a best-effort monotonic observability snapshot.
     *
     * @return stats
     */
    public Stats stats() {
        ensureOpen();
        return new Stats(
                service.hits(), service.misses(), service.size(),
                service.ttlEvictions(), service.intentionalEvictions());
    }

    /**
     * Shuts the expiry scheduler down; all further operations throw
     * {@link IllegalStateException}.
     */
    @Override
    public void close() {
        if (closed.compareAndSet(false, true)) {
            scheduler.close();
        }
    }

    private void ensureOpen() {
        if (closed.get()) {
            throw new IllegalStateException("KairosCache is closed");
        }
    }
}
