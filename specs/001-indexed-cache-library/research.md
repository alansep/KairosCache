# Research: Indexed Cache Library

**Feature**: `specs/001-indexed-cache-library` | **Date**: 2026-10-11

All `NEEDS CLARIFICATION` items from Technical Context were already resolved by constitution + user answers. This file records the decisions, rationale, and rejected alternatives so planning is auditable.

## Decision 1: Expiry engine — ScheduledExecutorService on virtual threads + generation counters

- **Decision**: Use a single shared `ScheduledExecutorService` created with `Thread.ofVirtual().factory()` (single-threaded scheduler) for all TTLs. Each stored top-level entry carries a monotonically increasing `generation` (long). Scheduling captures `(key, generation)`; on fire, evict only if the live entry still has the same generation. Overwrite bumps generation and re-schedules (or cancels via generation invalidation); write with `0` bumps generation and schedules nothing.
- **Rationale**: Fixes both JCache bugs at once — (a) no one-thread-per-key leak (one scheduler thread total, virtual so cheap parking), (b) no stale eviction (generation check makes old fires no-ops). `schedule()` avoids manual `sleep` math and handles cancellation cleanly. Virtual threads keep parking cheap under thousands of pending expiries.
- **Implementation note (2026-10-14)**: the fire guard compares the scheduled snapshot by
  *identity* (`==`), not only by generation. Generations restart at 1 after a full removal,
  so an evict-then-recreate sequence could otherwise let a stale fire match the fresh entry's
  generation. Identity makes stale fires no-ops in every interleaving; generations are kept
  as a secondary version stamp per the data model.
- **Alternatives considered**:
  - *One virtual thread + `Thread.sleep(ttl)` per key (JCache approach)* — rejected: thread-per-key leak, stale-eviction race, untestable timing.
  - *DelayQueue + single sweeper thread* — rejected: more hand-rolled code for equal semantics; scheduler already provides it in-JDK.
  - *Passive (lazy) expiry only on read* — rejected: violates SC-003 (entries must become unavailable promptly) and leaks memory for never-read keys.

## Decision 2: State — ConcurrentHashMap with immutable entry snapshots

- **Decision**: `ConcurrentHashMap<TopKey, EntrySnapshot>` where `EntrySnapshot` is an immutable record holding shape (SIMPLE/HASH), value or unmodifiable field map, `expiresAtMillis` (or `NO_EXPIRY`), and `generation`. Mutations use `ConcurrentHashMap.compute` for atomic upsert/evict. Hash-field updates copy-on-write the field map (fields count expected small; documented assumption).
- **Rationale**: `compute` gives atomic upsert without caller locking (FR-018). Immutable snapshots give readers tear-free views (no half-written hashes). Copy-on-write keeps code simple vs. nested concurrent maps with per-field locking.
- **Alternatives considered**:
  - *Nested `ConcurrentHashMap<K, ConcurrentHashMap<Field,V>>`* — rejected: harder atomic whole-entry TTL/evict, per-field expiry confusion (spec mandates top-level TTL only).
  - *Synchronized `HashMap` + explicit locks* — rejected: coarser contention, more deadlock surface, worse than proven concurrent primitives (Constitution IV).

## Decision 3: TTL surface — whole long millis, validated at boundary

- **Decision**: Public API accepts `long ttlMillis` (not `Duration`, not `double`). Validation in application façade before touching domain: `< 0` → invalid-argument outcome; `== 0` → persistent; `> 0` → schedule. Fractional inputs cannot exist in a `long` signature — any string/double parsing layer (future adapters) must reject non-integers before calling core.
- **Rationale**: Makes FR-009/010/011 unrepresentable-to-misuse. `Duration` permits nanos/negatives and invites `sleep(ZERO)` bugs; `long` millis matches user's exact rule and keeps the contract tiny.
- **Alternatives considered**:
  - *`java.time.Duration` parameter* — rejected: allows nanos/negatives, widens API, reintroduces ZERO-means-what ambiguity.
  - *Separate `put` / `putWithTtl` overloads* — rejected: two ways to do one thing; single method with `0` = persistent is simpler.

## Decision 4: Outcome model — typed result, no exceptions for expected cases, no previous-value return

- **Decision**: Every operation returns a small immutable `Outcome` (status enum: `OK` / `NOT_FOUND` / `TYPE_MISMATCH` / `INVALID_ARGUMENT` + optional value/fields). Missing keys, type mismatches, and invalid TTLs are *outcomes*, never exceptions. Upserts return `OK` without previous value (per user answer 2).
- **Rationale**: Directly implements spec's Operation Outcome entity; forces callers to handle miss/mismatch explicitly (unlike JCache's always-SUCCESS `read`). Keeps hot path allocation minimal (single record).
- **Alternatives considered**:
  - *Exceptions for miss/mismatch* — rejected: exceptional control flow on hot path, against simplicity.
  - *`Optional` returns* — rejected: cannot distinguish miss vs. type-mismatch vs. invalid-argument with one shape.

## Decision 5: Hexagonal packaging in one Gradle module

- **Decision**: `domain/` (models, ports, service — zero infra imports), `application/` (facade + validation), `infrastructure/` (`memory`, `expiry`, `time` adapters). Ports: inbound `CachePort`, outbound `ExpirySchedulerPort` + `ClockPort`. Single Gradle module.
- **Rationale**: Satisfies user's hexagonal requirement with minimum machinery (Constitution V). Ports make the scheduler and clock swappable in tests (manual-scheduler + fixed-clock) without touching domain. Multi-module Gradle split deferred until a second adapter family (e.g., persistence) appears.
- **Alternatives considered**:
  - *Three Gradle modules (`:domain`, `:application`, `:infrastructure`)* — rejected: YAGNI overhead for v1, slower builds, no independent release need.
  - *Flat package (no hexagon)* — rejected: violates explicit user requirement and would re-couple expiry threads to domain logic as in JCache.

## Decision 6: Time — injectable Clock, millis since epoch internally

- **Decision**: Domain computes `expiresAt = clock.millis() + ttlMillis`. Production wires `System.currentTimeMillis` clock; tests wire fixed/manual clocks. Expiry scheduler uses delay = `expiresAt - now`, clamped `>= 0`.
- **Rationale**: Deterministic TTL tests without real sleeps (Constitution III bans flaky timing tests). Millis match the `long` TTL surface with no conversion loss.
- **Alternatives considered**:
  - *`System.currentTimeMillis()` called directly in domain* — rejected: untestable, flaky.
  - *Nano-precision deadlines* — rejected: public contract is millis; nanos add false precision.
