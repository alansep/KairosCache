package com.alansep.kairoscache.domain.model;

import java.util.Objects;

/**
 * Result of a mutation (or a read that carries no payload).
 *
 * @param status outcome status
 * @param value payload; always {@code null} for {@code Void} operations and for
 *              {@code put}, which never returns the previous value
 * @param reason stable reason code; {@code null} unless the status is
 *               {@code TYPE_MISMATCH} or {@code INVALID_ARGUMENT}
 * @param <T> payload type
 */
public record Outcome<T>(Status status, T value, String reason) {
    /** Compact validation. */
    public Outcome {
        Objects.requireNonNull(status, "status");
        if (status == Status.OK && reason != null) {
            throw new IllegalArgumentException("OK outcomes carry no reason");
        }
    }

    /**
     * Returns {@code true} when the operation succeeded.
     *
     * @return true on {@code OK}
     */
    public boolean isOk() {
        return status == Status.OK;
    }

    /**
     * Successful outcome without payload.
     *
     * @param <T> payload type
     * @return OK outcome
     */
    public static <T> Outcome<T> ok() {
        return new Outcome<>(Status.OK, null, null);
    }

    /**
     * Successful outcome with payload.
     *
     * @param value payload (may be null)
     * @param <T> payload type
     * @return OK outcome
     */
    public static <T> Outcome<T> ok(T value) {
        return new Outcome<>(Status.OK, value, null);
    }

    /**
     * Type-mismatch outcome; existing data is untouched.
     *
     * @param reason stable reason code
     * @param <T> payload type
     * @return TYPE_MISMATCH outcome
     */
    public static <T> Outcome<T> typeMismatch(String reason) {
        return new Outcome<>(Status.TYPE_MISMATCH, null, reason);
    }

    /**
     * Invalid-argument outcome; nothing was stored or removed.
     *
     * @param reason stable reason code
     * @param <T> payload type
     * @return INVALID_ARGUMENT outcome
     */
    public static <T> Outcome<T> invalidArgument(String reason) {
        return new Outcome<>(Status.INVALID_ARGUMENT, null, reason);
    }
}
