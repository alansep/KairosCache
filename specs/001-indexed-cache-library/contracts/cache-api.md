# Contract: Cache Public API

**Feature**: `specs/001-indexed-cache-library` | **Date**: 2026-10-11 | **Type**: Library API (Java signatures — normative for tests)

This is the sole user-facing contract. Tests in `src/test/contract` MUST mirror every clause. Any signature change is a MAJOR version event per Constitution I.

## Facade

```java
// Entry point. Thread-safe. Zero runtime dependencies.
public final class KairosCache<K, F, V> implements AutoCloseable {
  public static <K, F, V> KairosCache<K, F, V> create();
  public static <K, F, V> KairosCache<K, F, V> create(java.time.Clock clock); // visible for tests

  // --- simple shape ---
  Outcome<V>          put(K key, V value, long ttlMillis);   // upsert; 0=persistent
  ReadOutcome<V>      get(K key);                             // OK+value | NOT_FOUND | TYPE_MISMATCH
  Outcome<Void>       evict(K key);                           // idempotent OK

  // --- hash shape ---
  Outcome<Void>       hput(K key, F field, V value, long ttlMillis); // creates entry if absent; ttl governs whole key
  ReadOutcome<V>      hget(K key, F field);                   // OK+value | NOT_FOUND | TYPE_MISMATCH
  ReadOutcome<java.util.Map<F, V>> hgetAll(K key);            // whole field map (unmodifiable copy)
  Outcome<Void>       hevict(K key, F field);                 // single field; OK even if missing

  // --- observability (Constitution V) ---
  Stats               stats();   // hits, misses, size (top-level keys), evictions (ttl + intentional split)
  void                close();   // shuts down scheduler; after close all ops throw IllegalStateException
}
```

## Outcome shapes

```java
public enum Status { OK, NOT_FOUND, TYPE_MISMATCH, INVALID_ARGUMENT }

public record Outcome<T>(Status status, T value, String reason) {
  // put/hevict/evict: value is null on OK. reason is null unless status is TYPE_MISMATCH/INVALID_ARGUMENT.
  public boolean isOk();
}

public record ReadOutcome<T>(Status status, java.util.Optional<T> data, String reason) {}

public record Stats(long hits, long misses, long size, long ttlEvictions, long intentionalEvictions) {}
```

## Behavioural clauses (normative)

1. `ttlMillis == 0` → persistent; `ttlMillis < 0` → `INVALID_ARGUMENT`, nothing stored; whole-millis only (signature makes fractions unrepresentable).
2. Upsert overwrites atomically; never returns previous value; bumps generation; new TTL replaces old (`0` cancels pending expiry).
3. Same top-level key cannot hold both shapes: `put` on a hash key (and `hput` on a simple key) → `TYPE_MISMATCH`, existing data untouched.
4. `get` on missing/expired → `NOT_FOUND`. Expired entries behave exactly as absent (lazy check + scheduler removal).
5. `evict` / `hevict` on missing targets → `OK` (idempotent).
6. `hevict` removing the last field leaves an empty hash entry present (no auto whole-key removal).
7. All methods are thread-safe without caller locking; `hgetAll` returns a snapshot copy.
8. After `close()`, scheduler is shut down; further ops throw `IllegalStateException` (only exceptional case in the API).
9. Null key/field/value → `INVALID_ARGUMENT`.
10. `stats()` is best-effort monotonic; `size` counts top-level keys only.

## Out of contract (v1)

Max-size/LRU/LFU, persistence, serialization, async variants, per-field TTL, events/listeners, metrics registries. Any of these require a new spec + MINOR/MAJOR amendment.
