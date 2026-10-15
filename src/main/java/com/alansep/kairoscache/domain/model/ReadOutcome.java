package com.alansep.kairoscache.domain.model;

import java.util.Objects;
import java.util.Optional;

/**
 * Result of a read carrying an optional payload.
 *
 * @param status outcome status
 * @param data value or field map on {@code OK}; empty otherwise
 * @param reason stable reason code; {@code null} unless the status is
 *               {@code TYPE_MISMATCH} or {@code INVALID_ARGUMENT}
 * @param <T> payload type
 */
public record ReadOutcome<T>(Status status, Optional<T> data, String reason) {
    /** Compact validation. */
    public ReadOutcome {
        Objects.requireNonNull(status, "status");
        Objects.requireNonNull(data, "data");
        if (status == Status.OK && data.isEmpty()) {
            throw new IllegalArgumentException("OK reads must carry data");
        }
        if (status == Status.OK && reason != null) {
            throw new IllegalArgumentException("OK outcomes carry no reason");
        }
    }

    /**
     * Returns {@code true} when the read found data.
     *
     * @return true on {@code OK}
     */
    public boolean isOk() {
        return status == Status.OK;
    }

    /**
     * Successful read.
     *
     * @param value payload
     * @param <T> payload type
     * @return OK outcome
     */
    public static <T> ReadOutcome<T> ok(T value) {
        return new ReadOutcome<>(Status.OK, Optional.of(value), null);
    }

    /**
     * Missing (or expired) entry.
     *
     * @param <T> payload type
     * @return NOT_FOUND outcome
     */
    public static <T> ReadOutcome<T> notFound() {
        return new ReadOutcome<>(Status.NOT_FOUND, Optional.empty(), null);
    }

    /**
     * Type-mismatch outcome; existing data is untouched.
     *
     * @param reason stable reason code
     * @param <T> payload type
     * @return TYPE_MISMATCH outcome
     */
    public static <T> ReadOutcome<T> typeMismatch(String reason) {
        return new ReadOutcome<>(Status.TYPE_MISMATCH, Optional.empty(), reason);
    }

    /**
     * Invalid-argument outcome.
     *
     * @param reason stable reason code
     * @param <T> payload type
     * @return INVALID_ARGUMENT outcome
     */
    public static <T> ReadOutcome<T> invalidArgument(String reason) {
        return new ReadOutcome<>(Status.INVALID_ARGUMENT, Optional.empty(), reason);
    }
}
