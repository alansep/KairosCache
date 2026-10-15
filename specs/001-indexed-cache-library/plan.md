# Implementation Plan: Indexed Cache Library

**Branch**: `feature/KRC-0004-indexed-cache-library` | **Date**: 2026-10-11 | **Spec**: [spec.md](./spec.md)

**Input**: Feature specification from `/specs/001-indexed-cache-library/spec.md`

## Summary

Deliver a JDK-only, in-process `Cache` library supporting two shapes — simple `K→V` and hash-like `K→{field→value}` — with top-level TTL (`0` = persistent, `<0`/fractional = invalid, `>0` integer millis), atomic upserts without previous-value return, and idempotent intentional eviction. Approach: hexagonal single-module Gradle library (domain core + ports, in-memory + scheduler adapters), `ConcurrentHashMap` state, `ScheduledExecutorService` on virtual threads with generation counters for race-free expiry, injectable `Clock` for deterministic tests.

## Technical Context

**Language/Version**: Java 21 (Corretto 21, no preview/incubator features)

**Primary Dependencies**: None at runtime (JDK only per Constitution II). Test scope: JUnit 6 via `org.junit:junit-bom:6.0.0` (`junit-jupiter`, `junit-platform-launcher`)

**Storage**: In-memory only (v1). `ConcurrentHashMap` for entries; no persistence, replication, or off-heap

**Testing**: JUnit 6, `./gradlew test` + `./gradlew build`. TDD Red-Green-Refactor mandatory. Time injectable via `java.time.Clock`. Concurrency stress tests with parallel executors

**Target Platform**: JVM 21+ library (JAR), embeddable in any Java 21 application. No server, no CLI, no I/O in core

**Project Type**: library (single Gradle module, hexagonal)

**Performance Goals**: `get/put` p99 < 1µs for in-memory hit (reference hardware, per Constitution); eviction lookup O(1); TTL expiry visible within 100 ms after deadline under no load; 10k mixed ops without errors in stress test

**Constraints**: Zero runtime dependencies; every public operation thread-safe without caller locking; `Duration`-free public TTL surface (whole `long` millis); no `System.out`, no reflection, no deserialization of untrusted data; public API fully Javadoc'd

**Scale/Scope**: Single process, thousands of keys/fields typical. No max-size/LRU in v1 — removal strictly by TTL or intentional evict

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

- [x] **I. Library-First & API Simplicity** — PASS. Plan exposes one minimal façade (`Cache<K,V>`-style with simple + hash ops). No server/CLI in core. Breaking changes require MAJOR.
- [x] **II. Zero-Dependency Lightness** — PASS. Runtime = JDK only (`java.util.concurrent`, `java.time`). JUnit confined to `testImplementation`. No new dependency proposed.
- [x] **III. Test-First** — PASS. Plan mandates tests-before-impl, `Clock` injection, deterministic expiry tests, no flaky sleeps. Contracts double as test oracles.
- [x] **IV. Correctness & Thread-Safety** — PASS. `ConcurrentHashMap` + per-entry generation counters + `ScheduledExecutorService`; races treated as P0; stress tests required in quickstart.
- [x] **V. Simplicity / Observability / Pragmatic Performance** — PASS. No distribution/off-heap/serialization. Stats (hits/misses/size/evictions) via minimal API. Benchmarks only if perf gate fails; no premature optimization.

No violations. No Complexity Tracking entry required.

## Project Structure

### Documentation (this feature)

```text
specs/001-indexed-cache-library/
├── plan.md              # This file (/speckit.plan command output)
├── research.md          # Phase 0 output (/speckit.plan command)
├── data-model.md        # Phase 1 output (/speckit.plan command)
├── quickstart.md        # Phase 1 output (/speckit.plan command)
├── contracts/           # Phase 1 output (/speckit.plan command)
│   └── cache-api.md     # Public library API contract
├── checklists/
│   └── requirements.md  # Spec quality checklist (from /speckit.specify)
└── tasks.md             # Phase 2 output (/speckit.tasks command - NOT created by /speckit.plan)
```

### Source Code (repository root)

```text
src/
├── main/java/com/alansep/kairoscache/
│   ├── domain/                      # Hexagon core (no infra imports)
│   │   ├── model/                   # SimpleEntry, HashEntry, Lifetime, CacheOutcome
│   │   ├── port/                    # CachePort (inbound), ExpirySchedulerPort, ClockPort
│   │   └── service/                 # CacheService (use-case orchestration, generation counters)
│   ├── application/                 # Facade (KairosCache) wiring domain + ports
│   └── infrastructure/              # Adapters (outbound)
│       ├── memory/                  # InMemoryCacheAdapter (ConcurrentHashMap state)
│       ├── expiry/                  # VirtualThreadExpiryScheduler (ScheduledExecutorService)
│       └── time/                    # SystemClockAdapter
└── test/java/com/alansep/kairoscache/
    ├── unit/                        # Domain + service tests (deterministic Clock)
    ├── contract/                    # Public API contract tests (mirrors contracts/cache-api.md)
    └── integration/                  # Concurrency + TTL expiry races
```

**Structure Decision**: Single Gradle module with hexagonal package split (not multi-module — Constitution V/YAGNI rejects `domain/` vs `infra/` Gradle modules until a second adapter family demands it). Existing `src/main/java/com/alansep/kairoscache/core/Main.java` placeholder is removed/replaced during implementation; `group = com.alansep.kairoscache` retained.

## Complexity Tracking

> No violations — section intentionally empty.

| Violation | Why Needed | Simpler Alternative Rejected Because |
|-----------|------------|-------------------------------------|
| — | — | — |
