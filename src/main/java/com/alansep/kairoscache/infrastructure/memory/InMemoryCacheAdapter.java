package com.alansep.kairoscache.infrastructure.memory;

import com.alansep.kairoscache.domain.model.EntrySnapshot;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BiFunction;

/**
 * In-memory state holder: one {@link ConcurrentHashMap} of immutable snapshots.
 *
 * <p>All whole-entry mutations go through {@link ConcurrentHashMap#compute} for atomicity;
 * reads are lock-free snapshot loads. No expiry or validation logic lives here — that is
 * the service's job.
 *
 * @param <K> top-level key type
 * @param <F> hash field type
 * @param <V> value type
 */
public final class InMemoryCacheAdapter<K, F, V> {
    private final ConcurrentHashMap<K, EntrySnapshot<F, V>> store = new ConcurrentHashMap<>();

    /**
     * Loads the live snapshot for a key.
     *
     * @param key top-level key
     * @return snapshot or {@code null} when absent
     */
    public EntrySnapshot<F, V> get(K key) {
        return store.get(key);
    }

    /**
     * Atomically remaps one key.
     *
     * @param key top-level key
     * @param remapping function receiving the live snapshot (or {@code null})
     *                  and returning the replacement (or {@code null} to remove)
     * @return the new snapshot, or {@code null} when removed
     */
    public EntrySnapshot<F, V> compute(
            K key,
            BiFunction<? super K, ? super EntrySnapshot<F, V>, ? extends EntrySnapshot<F, V>> remapping) {
        return store.compute(key, remapping);
    }

    /**
     * Removes the entry only when the live instance is exactly the expected one
     * (identity comparison). This is the stale-fire guard: a recreated entry is always
     * a new instance, so an old fire can never remove fresh data — even when generations
     * coincide after a full removal.
     *
     * @param key top-level key
     * @param expected the exact snapshot instance to remove
     * @return true when an entry was actually removed
     */
    public boolean removeIfSame(K key, EntrySnapshot<F, V> expected) {
        AtomicBoolean removed = new AtomicBoolean(false);
        store.computeIfPresent(key, (k, live) -> {
            if (live == expected) {
                removed.set(true);
                return null;
            }
            return live;
        });
        return removed.get();
    }

    /**
     * Removes the entry only when its generation still matches (stale-fire guard).
     *
     * @param key top-level key
     * @param generation expected version
     * @return true when an entry was actually removed
     */
    public boolean removeIfGenerationMatches(K key, long generation) {
        AtomicReference<EntrySnapshot<F, V>> seen = new AtomicReference<>();
        store.computeIfPresent(key, (k, live) -> {
            seen.set(live);
            return live.generation() == generation ? null : live;
        });
        EntrySnapshot<F, V> prev = seen.get();
        return prev != null && prev.generation() == generation;
    }

    /**
     * Removes the entry only when it is still the expected snapshot instance.
     *
     * @param key top-level key
     * @param expected snapshot seen by the caller
     * @return true when removed
     */
    public boolean remove(K key, EntrySnapshot<F, V> expected) {
        return store.remove(key, expected);
    }

    /**
     * Removes whichever snapshot is currently mapped, if any.
     *
     * @param key top-level key
     * @return true when something was removed
     */
    public boolean removeAny(K key) {
        return store.remove(key) != null;
    }

    /**
     * Returns the number of top-level keys currently held (best-effort).
     *
     * @return size
     */
    public int size() {
        return store.size();
    }
}
