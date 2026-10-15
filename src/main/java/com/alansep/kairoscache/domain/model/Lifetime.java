package com.alansep.kairoscache.domain.model;

/**
 * Whole-millisecond lifetime bound to a top-level key.
 *
 * <p>{@code 0} means persistent (no expiry); positive values expire that many
 * milliseconds after the write. Negative values are invalid and rejected at the
 * service boundary before a {@code Lifetime} is ever constructed. Fractional
 * lifetimes are unrepresentable: the surface is a {@code long}.
 *
 * @param millis {@code 0} for persistent, otherwise positive millis
 */
public record Lifetime(long millis) {
    /** Compact validation: negatives never become lifetimes. */
    public Lifetime {
        if (millis < 0) {
            throw new IllegalArgumentException("lifetime must be >= 0, got " + millis);
        }
    }

    /**
     * Returns {@code true} when this lifetime never expires.
     *
     * @return true for {@code 0}
     */
    public boolean isPersistent() {
        return millis == 0;
    }

    /**
     * Persistent lifetime factory.
     *
     * @return persistent lifetime
     */
    public static Lifetime persistent() {
        return new Lifetime(0);
    }

    /**
     * Expiring lifetime factory.
     *
     * @param millis positive millis
     * @return expiring lifetime
     */
    public static Lifetime ofMillis(long millis) {
        return new Lifetime(millis);
    }
}
