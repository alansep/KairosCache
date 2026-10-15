# Tasks: Indexed Cache Library

**Input**: Design documents from `/specs/001-indexed-cache-library/`
**Prerequisites**: plan.md, spec.md, research.md, data-model.md, contracts/cache-api.md, quickstart.md
**Tests**: Included — Constitution III mandates TDD Red-Green-Refactor; write tests FIRST and verify FAIL before implementation.

## Format: `[ID] [P?] [Story] Description`

- **[P]**: Can run in parallel (different files, no dependencies)
- **[Story]**: Which user story (US1..US4 from spec.md)
- Exact file paths required in every task.

## Phase 1: Setup (Shared Infrastructure)

**Purpose**: Hexagonal skeleton, placeholder removal, build green.

- [x] T001 Create hexagonal package skeleton under src/main/java/com/alansep/kairoscache/ (domain/model, domain/port, domain/service, application, infrastructure/memory, infrastructure/expiry, infrastructure/time) and mirror test dirs src/test/java/com/alansep/kairoscache/{unit,contract,integration}
- [x] T002 Remove placeholder src/main/java/com/alansep/kairoscache/core/Main.java and verify ./gradlew build succeeds with empty skeleton
- [x] T003 [P] Document Clock injection seam in README.md (one paragraph referencing research.md Decision 6, no logic)

---

## Phase 2: Foundational (Blocking Prerequisites)

**Purpose**: Core types + ports + scheduler + facade shell that ALL stories depend on. MUST complete before any US work.

- [x] T004 [P] Implement Status enum + Outcome/ReadOutcome/Stats records in src/main/java/com/alansep/kairoscache/domain/model/Outcome.java (OK, NOT_FOUND, TYPE_MISMATCH, INVALID_ARGUMENT)
- [x] T005 [P] Implement Lifetime value object in src/main/java/com/alansep/kairoscache/domain/model/Lifetime.java (0=persistent, >0 millis, validation rejects <0; fractional unrepresentable via long)
- [x] T006 [P] Implement EntrySnapshot immutable record in src/main/java/com/alansep/kairoscache/domain/model/EntrySnapshot.java (shape SIMPLE/HASH, value or unmodifiable field map, expiresAtMillis, generation)
- [x] T007 [P] Define inbound CachePort interface in src/main/java/com/alansep/kairoscache/domain/port/CachePort.java (put/get/evict/hput/hget/hgetAll/hevict signatures per contracts/cache-api.md)
- [x] T008 [P] Define outbound ExpirySchedulerPort + ClockPort in src/main/java/com/alansep/kairoscache/domain/port/ExpiryPorts.java (schedule(key, generation, delayMillis, onFire), clock.millis())
- [x] T009 Implement InMemoryCacheAdapter state holder in src/main/java/com/alansep/kairoscache/infrastructure/memory/InMemoryCacheAdapter.java (ConcurrentHashMap<TopKey, EntrySnapshot>, compute-based atomic mutate, snapshot-copy reads)
- [x] T010 Implement VirtualThreadExpiryScheduler in src/main/java/com/alansep/kairoscache/infrastructure/expiry/VirtualThreadExpiryScheduler.java (single ScheduledExecutorService with Thread.ofVirtual().factory(), schedule captures (key, generation), fire evicts only on generation match, close() shuts down)
- [x] T011 Implement SystemClockAdapter in src/main/java/com/alansep/kairoscache/infrastructure/time/SystemClockAdapter.java (delegates to System.currentTimeMillis)
- [x] T012 Implement CacheService orchestration skeleton in src/main/java/com/alansep/kairoscache/domain/service/CacheService.java (validation: nulls→INVALID_ARGUMENT, ttl<0→INVALID_ARGUMENT; generation bump on every write; delegates persistence to memory adapter + scheduling to expiry port)
- [x] T013 Implement KairosCache facade shell in src/main/java/com/alansep/kairoscache/application/KairosCache.java (create()/create(Clock), implements AutoCloseable, delegates to CacheService, stats() best-effort monotonic, post-close ops throw IllegalStateException)

**Checkpoint**: Foundation ready — `plan.md` Constitution Check still PASS; user-story work can begin.

---

## Phase 3: User Story 1 — Simple entry store/read + upsert (Priority: P1) 🎯 MVP

**Goal**: `put/get` simple K→V round-trip with atomic upsert, no previous-value return.
**Independent Test**: Store one key with ttl 0, read back exact value; overwrite same key, read back new value.

### Tests for US1 (write FIRST, verify FAIL)

- [x] T014 [P] [US1] Contract test for put/get round-trip in src/test/java/com/alansep/kairoscache/contract/SimpleEntryContractTest.java (covers FR-001, FR-003, FR-006, FR-007)
- [x] T015 [P] [US1] Unit test for null key/value rejection + type-mismatch on hput-over-simple in src/test/java/com/alansep/kairoscache/unit/SimpleEntryTest.java

### Implementation for US1

- [x] T016 [US1] Implement simple put/get in src/main/java/com/alansep/kairoscache/domain/service/CacheService.java (depends on T014–T015 failing; atomic compute, OK without previous value, miss→NOT_FOUND)
- [x] T017 [US1] Wire facade put/get in src/main/java/com/alansep/kairoscache/application/KairosCache.java (delegates to CacheService, updates hits/misses stats)

**Checkpoint**: US1 fully functional standalone — `./gradlew test --tests "*.contract.SimpleEntryContractTest"` green.

---

## Phase 4: User Story 2 — Hash entry store/read/update (Priority: P1)

**Goal**: `hput/hget/hgetAll` hash shape with per-field update preserving siblings; type isolation vs simple keys.
**Independent Test**: Store key with 3 fields, read one + all, update one field, siblings intact; cross-shape ops → TYPE_MISMATCH, data untouched.

### Tests for US2 (write FIRST, verify FAIL)

- [x] T018 [P] [US2] Contract test for hput/hget/hgetAll + sibling-preservation in src/test/java/com/alansep/kairoscache/contract/HashEntryContractTest.java (covers FR-002, FR-004, FR-005, FR-017)
- [x] T019 [P] [US2] Unit test for hgetAll snapshot-copy semantics + type-mismatch both directions in src/test/java/com/alansep/kairoscache/unit/HashEntryTest.java

### Implementation for US2

- [x] T020 [US2] Implement hash operations with copy-on-write field map in src/main/java/com/alansep/kairoscache/domain/service/CacheService.java (first hput creates entry; field upsert bumps top-level generation; returns unmodifiable copy on hgetAll)
- [x] T021 [US2] Wire facade hput/hget/hgetAll in src/main/java/com/alansep/kairoscache/application/KairosCache.java

**Checkpoint**: US1 + US2 both green and independent.

---

## Phase 5: User Story 3 — TTL expiry, top-level only (Priority: P2)

**Goal**: Automatic disappearance per FR-008..FR-013 with JCache-regression safety (rewrite cancels stale fires).
**Independent Test**: 500 ms entry gone after ~800 ms while persistent sibling survives; ttl 0 never expires; negative rejected with nothing stored; overwrite-with-0 survives 2× original lifetime.

### Tests for US3 (write FIRST, verify FAIL; deterministic Clock, no real sleeps in unit)

- [x] T022 [P] [US3] Unit test with manual Clock for TTL validation rules in src/test/java/com/alansep/kairoscache/unit/TtlValidationTest.java (0→persistent, <0→INVALID_ARGUMENT + nothing stored, whole-millis only)
- [x] T023 [P] [US3] Integration test for expiry visibility + stale-fire no-op in src/test/java/com/alansep/kairoscache/integration/TtlTest.java (500 ms gone ≤1 s, rewrite-with-0 survives 2× lifetime, rewrite-with-new-TTL replaces old)

### Implementation for US3

- [x] T024 [US3] Implement schedule-on-write + generation-guarded fire in src/main/java/com/alansep/kairoscache/domain/service/CacheService.java + src/main/java/com/alansep/kairoscache/infrastructure/expiry/VirtualThreadExpiryScheduler.java (expiresAt = clock.millis()+ttl; delay clamped ≥0; fire evicts only on generation match)
- [x] T025 [US3] Add lazy-expiry read path (expired reads as NOT_FOUND even before scheduler fires) in src/main/java/com/alansep/kairoscache/domain/service/CacheService.java

**Checkpoint**: `./gradlew test --tests "*.integration.TtlTest"` green; SC-003/SC-004/SC-005 satisfied.

---

## Phase 6: User Story 4 — Intentional eviction (Priority: P2)

**Goal**: Immediate idempotent removal: whole top key (all fields together) and single hash field (siblings intact).
**Independent Test**: Evict simple key → NOT_FOUND; evict one hash field → siblings intact; evict whole hash key → all gone; evict missing → OK.

### Tests for US4 (write FIRST, verify FAIL)

- [x] T026 [P] [US4] Contract test for evict/hevict idempotency + whole-vs-field scope in src/test/java/com/alansep/kairoscache/contract/EvictContractTest.java (covers FR-014, FR-015, FR-016)
- [x] T027 [P] [US4] Integration test that evict invalidates pending TTL fires in src/test/java/com/alansep/kairoscache/integration/EvictTest.java (evict then let original deadline pass → stays absent, no resurrection)

### Implementation for US4

- [x] T028 [US4] Implement evict/hevict with generation bump in src/main/java/com/alansep/kairoscache/domain/service/CacheService.java (whole-key removes snapshot; field-evict copy-on-write minus field, empty hash stays present per data-model decision; missing→OK)
- [x] T029 [US4] Wire facade evict/hevict + split eviction counters in src/main/java/com/alansep/kairoscache/application/KairosCache.java (ttlEvictions vs intentionalEvictions in Stats)

**Checkpoint**: All four stories independently functional.

---

## Phase 7: Polish & Cross-Cutting Concerns

**Purpose**: Constitution gates, docs, stress proof.

- [x] T030 [P] Javadoc every public type/method per contracts/cache-api.md + Constitution I (facade, outcomes, stats)
- [x] T031 Concurrency stress test 10k mixed ops in src/test/java/com/alansep/kairoscache/integration/ConcurrencyTest.java (virtual-thread executors, zero exceptions/torn reads, hits+misses==reads)
- [x] T032 Run full validation per specs/001-indexed-cache-library/quickstart.md (./gradlew build, contract suite, TtlTest, ConcurrencyTest --rerun-tasks) and fix fallout in failing source files
- [x] T033 Close() semantics + post-close IllegalStateException test + scheduler shutdown verification in src/test/java/com/alansep/kairoscache/integration/LifecycleTest.java

---

## Dependencies & Execution Order

- Phase 1 → Phase 2 (BLOCKS everything) → US1 → US2 → US3 → US4 → Polish.
- US1 and US2 are both P1 but US2 builds on US1's generation/version machinery — run sequentially.
- US3 needs US1+US2 shapes to expire; US4 needs entries to evict — both after US1/US2.
- Tests within each story MUST fail before its implementation tasks start.

## Parallel Example: US1

```bash
# Tests first, in parallel (different files):
T014 contract/SimpleEntryContractTest.java + T015 unit/SimpleEntryTest.java
# Then implementation sequentially (same service file):
T016 CacheService.java → T017 KairosCache.java
```

## Implementation Strategy

- MVP = Phases 1+2+3 (simple entry only) → STOP, validate, demo.
- Increment: +US2 (hash) → +US3 (TTL) → +US4 (evict) → Polish.
- Commit after each task; `./gradlew test` green before advancing phases.
