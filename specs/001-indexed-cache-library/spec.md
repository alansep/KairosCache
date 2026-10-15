# Feature Specification: Indexed Cache Library

**Feature Branch**: `feature/KRC-0004-indexed-cache-library`

**Created**: 2026-10-11

**Status**: Draft

**Input**: User description: "Build a library similar to caffeine cache for my use cases. It must support indexing: keys-value (key and value, simple entry) and keys->key-value (key and a map of keys-values, hash-like). It must support indexing with TTL on the top-level key. TTL rules: 0 = no expiry, < 0 = invalid input, > 0 integer millis (no decimals). It must support intentional evicts and upserts (upsert does not need to return previous value). Hexagonal architecture, Java runtime, virtual threads for TTL — deferred to planning."

## User Scenarios & Testing *(mandatory)*

### User Story 1 - Store and read a simple entry (Priority: P1)

A developer using the library stores a single value under a key and reads it back later in the same process.

**Why this priority**: This is the minimum viable cache. Without reliable simple-entry storage, nothing else has value.

**Independent Test**: Can be fully tested by storing one key-value pair with no expiry, reading it back, and confirming the returned value matches. Delivers a usable in-memory map with explicit eviction.

**Acceptance Scenarios**:

1. **Given** an empty cache, **When** the user stores key `user:1` with value `Alan` and no expiry, **Then** reading `user:1` returns `Alan`.
2. **Given** a stored key `user:1`, **When** the user overwrites (upserts) it with value `Gabriel` and no expiry, **Then** reading `user:1` returns `Gabriel` and no error occurs.

---

### User Story 2 - Store and read a hash-like entry (Priority: P1)

A developer stores a group of field-values under one top-level key (for example, `order:99` holding `status`, `total`, `owner`) and reads back individual fields or the whole group.

**Why this priority**: Co-equal with simple entries — the user's core use case requires both shapes from day one.

**Independent Test**: Can be fully tested by storing a top-level key with three fields, reading one field, reading all fields, updating one field, and confirming the other fields are untouched.

**Acceptance Scenarios**:

1. **Given** an empty cache, **When** the user stores top-level key `order:99` with fields `{status: OPEN, total: 100}`, **Then** reading field `status` of `order:99` returns `OPEN` and reading all fields returns both.
2. **Given** stored `order:99` with `{status: OPEN, total: 100}`, **When** the user upserts field `status` to `PAID`, **Then** `status` reads `PAID` and `total` still reads `100`.

---

### User Story 3 - Expire entries automatically by TTL (Priority: P2)

A developer stores entries with a limited lifetime so stale data disappears without manual cleanup. TTL applies to the top-level key only.

**Why this priority**: Correctness of freshness matters, but the cache is useful even without expiry. P2 because P1 already delivers value.

**Independent Test**: Can be fully tested by storing entries with short lifetimes, waiting past expiry, and confirming they are gone while non-expiring entries remain.

**Acceptance Scenarios**:

1. **Given** an empty cache, **When** the user stores `session:1` with a 500-millisecond lifetime, **Then** reading it immediately returns the value and reading it after 800 milliseconds reports it as missing.
2. **Given** stored `config:theme` with no expiry and `session:1` with a 500-millisecond lifetime, **When** 800 milliseconds pass, **Then** `session:1` is missing and `config:theme` still returns its value.
3. **Given** an empty cache, **When** the user stores a key with lifetime `0`, **Then** the entry never expires automatically.
4. **Given** an empty cache, **When** the user attempts to store a key with a negative lifetime, **Then** the operation is rejected as invalid input and nothing is stored.

---

### User Story 4 - Intentionally remove entries (Priority: P2)

A developer explicitly removes data they know is stale (single key or single hash field) and the removal is immediately visible to all readers.

**Why this priority**: Manual control complements TTL; required for invalidation flows.

**Independent Test**: Can be fully tested by storing entries, evicting them, and confirming subsequent reads report them missing.

**Acceptance Scenarios**:

1. **Given** stored key `user:1`, **When** the user evicts `user:1`, **Then** reading `user:1` reports it as missing.
2. **Given** stored `order:99` with fields `{status, total}`, **When** the user evicts field `total` of `order:99`, **Then** reading `total` reports missing while `status` still returns its value.
3. **Given** stored `order:99`, **When** the user evicts top-level `order:99`, **Then** all its fields are gone together.

---

### Edge Cases

- What happens when a hash field is read from a key that holds a simple value (type mismatch)? System reports a type-mismatch error and stores nothing.
- What happens when a simple read targets a key that holds a hash? System reports a type-mismatch error.
- What happens when TTL is re-set by overwriting an expiring key with no expiry (`0`)? The new write cancels the previous expiry; the entry becomes persistent.
- What happens when overwriting an expiring key with a new positive TTL? The new lifetime replaces the old one; the old scheduled expiry must not remove the fresh value.
- What happens when a negative TTL is supplied for a hash field operation? Rejected as invalid input; existing data untouched.
- What happens when fractional lifetimes are supplied? Rejected as invalid input — only whole-number milliseconds are accepted.
- What happens under concurrent reads, writes, and expiries of the same key? All operations remain safe; readers never see torn or half-written values.
- What happens when evicting a key that does not exist? Operation succeeds silently (idempotent) and reports success.

## Requirements *(mandatory)*

### Functional Requirements

- **FR-001**: System MUST allow storing a simple entry mapping one key to one value.
- **FR-002**: System MUST allow storing a hash-like entry mapping one top-level key to a set of field-value pairs.
- **FR-003**: System MUST allow reading a simple entry by key, distinguishing found vs. missing.
- **FR-004**: System MUST allow reading a single hash field by top-level key and field name.
- **FR-005**: System MUST allow reading all fields of a hash entry at once.
- **FR-006**: System MUST support upsert semantics — storing under an existing key or field overwrites the prior value atomically without requiring the caller to delete first.
- **FR-007**: System MUST NOT require upsert operations to return the previous value.
- **FR-008**: System MUST support TTL on the top-level key for both simple and hash-like entries (one lifetime governs the whole top-level entry including all its hash fields).
- **FR-009**: System MUST interpret lifetime `0` as "no expiry" (persistent until intentionally evicted).
- **FR-010**: System MUST reject negative lifetimes as invalid input, storing nothing and reporting an invalid-argument outcome.
- **FR-011**: System MUST accept only whole-number millisecond lifetimes greater than zero; fractional values MUST be rejected as invalid input.
- **FR-012**: System MUST automatically make expired entries unavailable for reads after their lifetime elapses.
- **FR-013**: System MUST cancel or supersede a prior expiry when the same top-level key is overwritten (new lifetime replaces old; `0` cancels expiry).
- **FR-014**: System MUST support intentional eviction of a whole top-level key (removing all its hash fields together).
- **FR-015**: System MUST support intentional eviction of a single hash field without affecting sibling fields.
- **FR-016**: Eviction of a missing key or field MUST succeed idempotently.
- **FR-017**: System MUST report a type-mismatch outcome when an operation targets the wrong entry shape (e.g., field read on a simple key).
- **FR-018**: All operations MUST be safe under concurrent access without caller-side locking.

### Key Entities

- **Simple Entry**: One top-level key mapped to one opaque value, plus an optional lifetime. Either present (readable) or absent (expired or evicted).
- **Hash Entry**: One top-level key mapped to a set of field-value pairs sharing a single top-level lifetime. Supports per-field read/write/evict and whole-entry evict.
- **Lifetime**: Whole-number milliseconds (`0` = persistent, `> 0` = expires after that many milliseconds, `< 0` or fractional = invalid). Bound to the top-level key only.
- **Operation Outcome**: For every mutation or read, a result distinguishing success, missing, type-mismatch, and invalid-argument — without leaking internal details.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: Developers can store and retrieve a simple entry in under 5 minutes of first exposure (measured by time-to-first-successful-read in usability walkthrough).
- **SC-002**: Developers can store, update one field, and re-read a 3-field hash entry with all unrelated fields intact on first attempt (100% task completion in acceptance walkthrough).
- **SC-003**: 100% of entries stored with a 500-millisecond lifetime are unreadable within 1 second after expiry, while persistent entries remain readable (measured by automated acceptance run).
- **SC-004**: 100% of negative or fractional lifetime inputs are rejected without storing data (measured by invalid-input acceptance suite).
- **SC-005**: Overwriting an expiring entry with a persistent one keeps it readable 2x past the original lifetime (no stale eviction, measured by rewrite acceptance test).
- **SC-006**: Concurrent mixed read/write/evict/expiry workloads complete without errors or torn reads across 10,000 operations (measured by stress acceptance run).

## Assumptions

- Target users are application developers embedding the library in-process (not operators of a standalone server — the prior TCP/STDIO server is out of scope for v1).
- Only in-memory storage is required for v1; persistence, replication, and distribution are out of scope.
- No maximum-size or eviction-policy (LRU/LFU) requirements for v1 — removal is strictly by TTL expiry or intentional evict.
- Key, field, and value types, serialization, and size limits are implementation choices deferred to planning; the spec constrains only behavior and outcomes.
- Runtime platform (Java), concurrency mechanism (virtual threads), and structural style (hexagonal architecture) are planning decisions governed by the project constitution, not user-facing behavior, and are therefore excluded from acceptance criteria.
