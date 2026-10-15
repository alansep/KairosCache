package com.alansep.kairoscache.domain.model;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

/**
 * Immutable snapshot of one top-level entry.
 *
 * <p>Readers hold tear-free views: hash-field updates are copy-on-write, so a snapshot
 * never changes under a reader. Each write bumps {@code generation}; scheduled expiry
 * fires evict only when the live generation still matches the scheduled one, making
 * stale fires no-ops.
 *
 * @param shape entry shape
 * @param simpleValue simple payload; non-null iff shape is {@code SIMPLE}
 * @param hashFields field map (unmodifiable); non-null iff shape is {@code HASH}
 * @param expiresAtMillis epoch millis of expiry, or {@link #NO_EXPIRY} when persistent
 * @param generation monotonic version per top-level key, starting at 1
 * @param <F> field type
 * @param <V> value type
 */
public record EntrySnapshot<F, V>(
        Shape shape,
        V simpleValue,
        Map<F, V> hashFields,
        long expiresAtMillis,
        long generation) {

    /** Entry shape: exactly one of simple or hash per top-level key. */
    public enum Shape {
        /** One key maps to one value. */
        SIMPLE,
        /** One key maps to a field-value map. */
        HASH
    }

    /** Sentinel for persistent entries that never expire. */
    public static final long NO_EXPIRY = Long.MAX_VALUE;

    /** Compact validation and defensive copy. */
    public EntrySnapshot {
        Objects.requireNonNull(shape, "shape");
        if (generation <= 0) {
            throw new IllegalArgumentException("generation must be > 0");
        }
        if (shape == Shape.SIMPLE) {
            Objects.requireNonNull(simpleValue, "simpleValue");
            if (hashFields != null) {
                throw new IllegalArgumentException("SIMPLE entries carry no field map");
            }
        } else {
            Objects.requireNonNull(hashFields, "hashFields");
            if (simpleValue != null) {
                throw new IllegalArgumentException("HASH entries carry no simple value");
            }
            hashFields = Collections.unmodifiableMap(new HashMap<>(hashFields));
        }
    }

    /**
     * Creates a simple-entry snapshot.
     *
     * @param value payload
     * @param expiresAtMillis expiry epoch millis or {@link #NO_EXPIRY}
     * @param generation version
     * @param <F> field type
     * @param <V> value type
     * @return snapshot
     */
    public static <F, V> EntrySnapshot<F, V> simple(V value, long expiresAtMillis, long generation) {
        return new EntrySnapshot<>(Shape.SIMPLE, value, null, expiresAtMillis, generation);
    }

    /**
     * Creates a hash-entry snapshot (defensive copy; unmodifiable inside).
     *
     * @param fields field map
     * @param expiresAtMillis expiry epoch millis or {@link #NO_EXPIRY}
     * @param generation version
     * @param <F> field type
     * @param <V> value type
     * @return snapshot
     */
    public static <F, V> EntrySnapshot<F, V> hash(
            Map<F, V> fields, long expiresAtMillis, long generation) {
        return new EntrySnapshot<>(Shape.HASH, null, fields, expiresAtMillis, generation);
    }

    /**
     * Returns {@code true} when this snapshot is persistent.
     *
     * @return true when {@code expiresAtMillis == NO_EXPIRY}
     */
    public boolean isPersistent() {
        return expiresAtMillis == NO_EXPIRY;
    }

    /**
     * Returns {@code true} when this snapshot is expired at {@code nowMillis}.
     *
     * @param nowMillis current epoch millis
     * @return true when expiring and the deadline has passed
     */
    public boolean isExpired(long nowMillis) {
        return expiresAtMillis != NO_EXPIRY && nowMillis >= expiresAtMillis;
    }
}
