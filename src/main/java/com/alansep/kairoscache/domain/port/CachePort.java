package com.alansep.kairoscache.domain.port;

import com.alansep.kairoscache.domain.model.Outcome;
import com.alansep.kairoscache.domain.model.ReadOutcome;
import java.util.Map;

/**
 * Inbound port: the use-case boundary every driver (facade, future adapters) talks to.
 *
 * @param <K> top-level key type
 * @param <F> hash field type
 * @param <V> value type
 */
public interface CachePort<K, F, V> {
    /**
     * Stores a simple entry (upsert; never returns the previous value).
     *
     * @param key top-level key
     * @param value payload
     * @param ttlMillis {@code 0} persistent, {@code >0} whole millis, {@code <0} invalid
     * @return {@code OK}, {@code TYPE_MISMATCH} (key holds a hash), or {@code INVALID_ARGUMENT}
     */
    Outcome<V> put(K key, V value, long ttlMillis);

    /**
     * Reads a simple entry.
     *
     * @param key top-level key
     * @return {@code OK} with value, {@code NOT_FOUND}, {@code TYPE_MISMATCH}, or
     *         {@code INVALID_ARGUMENT}
     */
    ReadOutcome<V> get(K key);

    /**
     * Removes a whole top-level entry (idempotent).
     *
     * @param key top-level key
     * @return {@code OK} always (unless the key is null)
     */
    Outcome<Void> evict(K key);

    /**
     * Stores one hash field (creates the entry on first field; TTL governs the whole key).
     *
     * @param key top-level key
     * @param field field name
     * @param value payload
     * @param ttlMillis {@code 0} persistent, {@code >0} whole millis, {@code <0} invalid
     * @return {@code OK}, {@code TYPE_MISMATCH} (key holds a simple value), or
     *         {@code INVALID_ARGUMENT}
     */
    Outcome<Void> hput(K key, F field, V value, long ttlMillis);

    /**
     * Reads one hash field.
     *
     * @param key top-level key
     * @param field field name
     * @return {@code OK} with value, {@code NOT_FOUND}, {@code TYPE_MISMATCH}, or
     *         {@code INVALID_ARGUMENT}
     */
    ReadOutcome<V> hget(K key, F field);

    /**
     * Reads all hash fields as a snapshot copy.
     *
     * @param key top-level key
     * @return {@code OK} with an unmodifiable copy, {@code NOT_FOUND},
     *         {@code TYPE_MISMATCH}, or {@code INVALID_ARGUMENT}
     */
    ReadOutcome<Map<F, V>> hgetAll(K key);

    /**
     * Removes one hash field (idempotent; siblings intact; last-field removal leaves an
     * empty hash present).
     *
     * @param key top-level key
     * @param field field name
     * @return {@code OK} (or {@code TYPE_MISMATCH} / {@code INVALID_ARGUMENT})
     */
    Outcome<Void> hevict(K key, F field);
}
