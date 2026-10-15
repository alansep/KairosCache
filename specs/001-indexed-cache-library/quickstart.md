# Quickstart: Validate Indexed Cache Library

**Feature**: `specs/001-indexed-cache-library` | **Date**: 2026-10-11

Proves the feature works end-to-end. No implementation code here — only runnable validation. Full suites arrive with `/speckit.tasks` + implementation.

## Prerequisites

- JDK 21+, `./gradlew` wrapper in repo root
- Clean tree on the feature branch (spec + plan merged or checked out)

## 1. Build the library

```bash
./gradlew build
```

Expected: `BUILD SUCCESSFUL`. No server to start — the artifact is a JAR.

## 2. Contract check (mirrors contracts/cache-api.md)

Run once implementation lands:

```bash
./gradlew test --tests "com.alansep.kairoscache.contract.*"
```

Must cover: `put/get` round-trip, `hput/hget/hgetAll` round-trip, upsert overwrite, `TYPE_MISMATCH` both directions, `INVALID_ARGUMENT` for negative TTL + nulls, idempotent evict of missing keys.

## 3. TTL acceptance (deterministic, no real sleeps in unit tests)

```bash
./gradlew test --tests "com.alansep.kairoscache.integration.TtlTest"
```

Scenarios: store with 500 ms → immediate read OK → advance manual clock past deadline / wait ≤1 s in integration → read `NOT_FOUND`; persistent entry alongside remains `OK`; overwrite expiring with `0` survives 2x original lifetime (no stale eviction — the JCache regression test).

## 4. Concurrency stress

```bash
./gradlew test --tests "com.alansep.kairoscache.integration.ConcurrencyTest" --rerun-tasks
```

10k mixed `put/get/hput/evict` across virtual-thread executors. Expected: zero exceptions, zero torn reads, final `stats()` consistent (`hits + misses == reads`).

## 5. Manual smoke (REPL-style, optional)

```java
var cache = KairosCache.<String,String,String>create();
cache.put("user:1", "Alan", 0);
cache.hput("order:99", "status", "OPEN", 0);
System.out.println(cache.get("user:1"));       // OK[Alan]
System.out.println(cache.hget("order:99", "status")); // OK[OPEN]
cache.close();
```

## References

- Behaviours: [spec.md](./spec.md) (FR-001..FR-018, SC-001..SC-006)
- Shapes: [data-model.md](./data-model.md)
- Signatures: [contracts/cache-api.md](./contracts/cache-api.md)
- Decisions: [research.md](./research.md) (scheduler+generation, outcomes, hexagon)
