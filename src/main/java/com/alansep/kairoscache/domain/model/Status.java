package com.alansep.kairoscache.domain.model;

/**
 * Outcome status for every cache operation.
 *
 * <p>{@code OK} signals success; {@code NOT_FOUND} an absent (or expired) entry;
 * {@code TYPE_MISMATCH} a shape error (simple vs hash); {@code INVALID_ARGUMENT}
 * a rejected input (null key/field/value, negative TTL). Expected cases are outcomes,
 * never exceptions.
 */
public enum Status {
    /** Operation succeeded. */
    OK,
    /** Entry (or field) absent — including expired entries, which read as absent. */
    NOT_FOUND,
    /** Operation targeted the wrong entry shape; existing data is untouched. */
    TYPE_MISMATCH,
    /** Null key/field/value or negative TTL; nothing was stored or removed. */
    INVALID_ARGUMENT
}
