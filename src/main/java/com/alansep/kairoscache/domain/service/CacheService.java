package com.alansep.kairoscache.domain.service;

import com.alansep.kairoscache.domain.model.EntrySnapshot;
import com.alansep.kairoscache.domain.model.Outcome;
import com.alansep.kairoscache.domain.model.ReadOutcome;
import com.alansep.kairoscache.domain.port.CachePort;
import com.alansep.kairoscache.domain.port.ExpiryPorts.ClockPort;
import com.alansep.kairoscache.domain.port.ExpiryPorts.ExpirySchedulerPort;
import com.alansep.kairoscache.infrastructure.memory.InMemoryCacheAdapter;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Use-case orchestration: validation, generation bumps, expiry scheduling.
 *
 * <p>All writes are atomic via the memory adapter's {@code compute}; every write bumps
 * the per-key generation so stale scheduler fires become no-ops. Expired entries read
 * as absent (lazy path) even before the scheduler fires. Counters are best-effort
 * monotonic.
 *
 * @param <K> top-level key type
 * @param <F> hash field type
 * @param <V> value type
 */
public final class CacheService<K, F, V> implements CachePort<K, F, V> {
    private final InMemoryCacheAdapter<K, F, V> memory;
    private final ExpirySchedulerPort<K> scheduler;
    private final ClockPort clock;
    private final AtomicLong hits = new AtomicLong();
    private final AtomicLong misses = new AtomicLong();
    private final AtomicLong ttlEvictions = new AtomicLong();
    private final AtomicLong intentionalEvictions = new AtomicLong();

    /**
     * Creates the service.
     *
     * @param memory state holder
     * @param scheduler expiry engine
     * @param clock time seam
     */
    public CacheService(
            InMemoryCacheAdapter<K, F, V> memory,
            ExpirySchedulerPort<K> scheduler,
            ClockPort clock) {
        this.memory = Objects.requireNonNull(memory, "memory");
        this.scheduler = Objects.requireNonNull(scheduler, "scheduler");
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    /**
     * Stores a simple entry (upsert; never returns the previous value).
     *
     * @param key top-level key
     * @param value payload
     * @param ttlMillis {@code 0} persistent, {@code >0} whole millis
     * @return {@code OK}, {@code TYPE_MISMATCH}, or {@code INVALID_ARGUMENT}
     */
    @Override
    public Outcome<V> put(K key, V value, long ttlMillis) {
        if (key == null || value == null) {
            return Outcome.invalidArgument("null key or value");
        }
        if (ttlMillis < 0) {
            return Outcome.invalidArgument("negative ttl");
        }
        long expiresAt = expiresAtFor(ttlMillis);
        AtomicBoolean mismatch = new AtomicBoolean(false);
        AtomicReference<EntrySnapshot<F, V>> installed = new AtomicReference<>();
        memory.compute(key, (k, live) -> {
            if (isLive(live) && live.shape() == EntrySnapshot.Shape.HASH) {
                mismatch.set(true);
                return live;
            }
            long gen = live == null ? 1 : live.generation() + 1;
            EntrySnapshot<F, V> next = EntrySnapshot.simple(value, expiresAt, gen);
            installed.set(next);
            return next;
        });
        if (mismatch.get()) {
            return Outcome.typeMismatch("key holds a hash entry");
        }
        if (ttlMillis > 0) {
            EntrySnapshot<F, V> snapshot = installed.get();
            scheduler.schedule(key, snapshot.generation(), ttlMillis, () -> expireOnFire(key, snapshot));
        }
        return Outcome.ok();
    }

    /**
     * Reads a simple entry.
     *
     * @param key top-level key
     * @return {@code OK}, {@code NOT_FOUND}, {@code TYPE_MISMATCH}, or {@code INVALID_ARGUMENT}
     */
    @Override
    public ReadOutcome<V> get(K key) {
        if (key == null) {
            return ReadOutcome.invalidArgument("null key");
        }
        EntrySnapshot<F, V> live = memory.get(key);
        if (live == null) {
            misses.incrementAndGet();
            return ReadOutcome.notFound();
        }
        if (live.isExpired(clock.millis())) {
            expireQuietly(key, live);
            misses.incrementAndGet();
            return ReadOutcome.notFound();
        }
        if (live.shape() != EntrySnapshot.Shape.SIMPLE) {
            misses.incrementAndGet();
            return ReadOutcome.typeMismatch("key holds a hash entry");
        }
        hits.incrementAndGet();
        return ReadOutcome.ok(live.simpleValue());
    }

    /**
     * Removes a whole top-level entry (idempotent).
     *
     * @param key top-level key
     * @return {@code OK} or {@code INVALID_ARGUMENT}
     */
    @Override
    public Outcome<Void> evict(K key) {
        if (key == null) {
            return Outcome.invalidArgument("null key");
        }
        EntrySnapshot<F, V> live = memory.get(key);
        if (live == null) {
            return Outcome.ok();
        }
        if (live.isExpired(clock.millis())) {
            expireQuietly(key, live);
            return Outcome.ok();
        }
        if (memory.remove(key, live)) {
            intentionalEvictions.incrementAndGet();
        }
        return Outcome.ok();
    }

    /**
     * Stores one hash field (creates the entry on first field; TTL governs the whole key).
     *
     * @param key top-level key
     * @param field field name
     * @param value payload
     * @param ttlMillis {@code 0} persistent, {@code >0} whole millis
     * @return {@code OK}, {@code TYPE_MISMATCH}, or {@code INVALID_ARGUMENT}
     */
    @Override
    public Outcome<Void> hput(K key, F field, V value, long ttlMillis) {
        if (key == null || field == null || value == null) {
            return Outcome.invalidArgument("null key, field or value");
        }
        if (ttlMillis < 0) {
            return Outcome.invalidArgument("negative ttl");
        }
        long expiresAt = expiresAtFor(ttlMillis);
        AtomicBoolean mismatch = new AtomicBoolean(false);
        AtomicReference<EntrySnapshot<F, V>> installed = new AtomicReference<>();
        memory.compute(key, (k, live) -> {
            boolean valid = isLive(live);
            if (valid && live.shape() == EntrySnapshot.Shape.SIMPLE) {
                mismatch.set(true);
                return live;
            }
            long gen = live == null ? 1 : live.generation() + 1;
            Map<F, V> base =
                    valid && live.shape() == EntrySnapshot.Shape.HASH ? live.hashFields() : Map.of();
            Map<F, V> next = new HashMap<>(base);
            next.put(field, value);
            EntrySnapshot<F, V> snapshot = EntrySnapshot.hash(next, expiresAt, gen);
            installed.set(snapshot);
            return snapshot;
        });
        if (mismatch.get()) {
            return Outcome.typeMismatch("key holds a simple entry");
        }
        if (ttlMillis > 0) {
            EntrySnapshot<F, V> snapshot = installed.get();
            scheduler.schedule(key, snapshot.generation(), ttlMillis, () -> expireOnFire(key, snapshot));
        }
        return Outcome.ok();
    }

    /**
     * Reads one hash field.
     *
     * @param key top-level key
     * @param field field name
     * @return {@code OK}, {@code NOT_FOUND}, {@code TYPE_MISMATCH}, or {@code INVALID_ARGUMENT}
     */
    @Override
    public ReadOutcome<V> hget(K key, F field) {
        if (key == null || field == null) {
            return ReadOutcome.invalidArgument("null key or field");
        }
        EntrySnapshot<F, V> live = memory.get(key);
        if (live == null) {
            misses.incrementAndGet();
            return ReadOutcome.notFound();
        }
        if (live.isExpired(clock.millis())) {
            expireQuietly(key, live);
            misses.incrementAndGet();
            return ReadOutcome.notFound();
        }
        if (live.shape() != EntrySnapshot.Shape.HASH) {
            misses.incrementAndGet();
            return ReadOutcome.typeMismatch("key holds a simple entry");
        }
        V value = live.hashFields().get(field);
        if (value == null) {
            misses.incrementAndGet();
            return ReadOutcome.notFound();
        }
        hits.incrementAndGet();
        return ReadOutcome.ok(value);
    }

    /**
     * Reads all hash fields as a snapshot copy.
     *
     * @param key top-level key
     * @return {@code OK} with an unmodifiable copy, {@code NOT_FOUND},
     *         {@code TYPE_MISMATCH}, or {@code INVALID_ARGUMENT}
     */
    @Override
    public ReadOutcome<Map<F, V>> hgetAll(K key) {
        if (key == null) {
            return ReadOutcome.invalidArgument("null key");
        }
        EntrySnapshot<F, V> live = memory.get(key);
        if (live == null) {
            misses.incrementAndGet();
            return ReadOutcome.notFound();
        }
        if (live.isExpired(clock.millis())) {
            expireQuietly(key, live);
            misses.incrementAndGet();
            return ReadOutcome.notFound();
        }
        if (live.shape() != EntrySnapshot.Shape.HASH) {
            misses.incrementAndGet();
            return ReadOutcome.typeMismatch("key holds a simple entry");
        }
        hits.incrementAndGet();
        return ReadOutcome.ok(Collections.unmodifiableMap(new HashMap<>(live.hashFields())));
    }

    /**
     * Removes one hash field (idempotent; empty hash stays present).
     *
     * @param key top-level key
     * @param field field name
     * @return {@code OK}, {@code TYPE_MISMATCH}, or {@code INVALID_ARGUMENT}
     */
    @Override
    public Outcome<Void> hevict(K key, F field) {
        if (key == null || field == null) {
            return Outcome.invalidArgument("null key or field");
        }
        AtomicBoolean mismatch = new AtomicBoolean(false);
        AtomicBoolean wasExpired = new AtomicBoolean(false);
        AtomicBoolean removedField = new AtomicBoolean(false);
        memory.compute(key, (k, cur) -> {
            if (cur == null) {
                return null;
            }
            if (cur.isExpired(clock.millis())) {
                wasExpired.set(true);
                return null;
            }
            if (cur.shape() != EntrySnapshot.Shape.HASH) {
                mismatch.set(true);
                return cur;
            }
            if (!cur.hashFields().containsKey(field)) {
                return cur;
            }
            Map<F, V> next = new HashMap<>(cur.hashFields());
            next.remove(field);
            removedField.set(true);
            return EntrySnapshot.hash(next, cur.expiresAtMillis(), cur.generation() + 1);
        });
        if (mismatch.get()) {
            return Outcome.typeMismatch("key holds a simple entry");
        }
        if (wasExpired.get()) {
            ttlEvictions.incrementAndGet();
            return Outcome.ok();
        }
        if (removedField.get()) {
            intentionalEvictions.incrementAndGet();
        }
        return Outcome.ok();
    }

    /**
     * Returns read hits (best-effort monotonic).
     *
     * @return hits
     */
    public long hits() {
        return hits.get();
    }

    /**
     * Returns read misses (best-effort monotonic).
     *
     * @return misses
     */
    public long misses() {
        return misses.get();
    }

    /**
     * Returns TTL-driven removals (best-effort monotonic).
     *
     * @return ttl evictions
     */
    public long ttlEvictions() {
        return ttlEvictions.get();
    }

    /**
     * Returns intentional removals (best-effort monotonic).
     *
     * @return intentional evictions
     */
    public long intentionalEvictions() {
        return intentionalEvictions.get();
    }

    /**
     * Returns the number of top-level keys currently held (best-effort).
     *
     * @return size
     */
    public int size() {
        return memory.size();
    }

    private long expiresAtFor(long ttlMillis) {
        if (ttlMillis == 0) {
            return EntrySnapshot.NO_EXPIRY;
        }
        long now = clock.millis();
        if (ttlMillis > Long.MAX_VALUE - now) {
            return EntrySnapshot.NO_EXPIRY;
        }
        return now + ttlMillis;
    }

    private boolean isLive(EntrySnapshot<F, V> snapshot) {
        return snapshot != null && !snapshot.isExpired(clock.millis());
    }

    private void expireOnFire(K key, EntrySnapshot<F, V> expected) {
        if (memory.removeIfSame(key, expected)) {
            ttlEvictions.incrementAndGet();
        }
    }

    private void expireQuietly(K key, EntrySnapshot<F, V> live) {
        if (memory.removeIfSame(key, live)) {
            ttlEvictions.incrementAndGet();
        }
    }
}
