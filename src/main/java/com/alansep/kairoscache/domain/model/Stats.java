package com.alansep.kairoscache.domain.model;

/**
 * Best-effort monotonic observability snapshot.
 *
 * @param hits reads that found data
 * @param misses reads that found nothing (missing, expired, or type-mismatched)
 * @param size number of top-level keys currently held (best-effort; may briefly
 *             include not-yet-swept expired entries)
 * @param ttlEvictions entries reclaimed by TTL expiry
 * @param intentionalEvictions entries or fields removed by explicit evict calls
 */
public record Stats(long hits, long misses, long size, long ttlEvictions, long intentionalEvictions) {
}
