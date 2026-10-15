# Data Model: Indexed Cache Library

**Feature**: `specs/001-indexed-cache-library` | **Date**: 2026-10-11

Derived from spec Key Entities + FR-001..FR-018. Technology-agnostic shapes; Java record sketch in contracts only.

## Entity 1: Simple Entry

- **What**: One top-level key mapped to one opaque value with an optional lifetime.
- **Attributes**:
  - `key` — unique top-level identifier (opaque to core; equality by caller-provided semantics).
  - `value` — opaque payload.
  - `lifetimeMillis` — `0` = persistent; `> 0` = whole millis until expiry; never `< 0` or fractional (rejected before creation).
  - `generation` — internal monotonic version per top-level key; bumped on every overwrite/evict-schedule; used only to invalidate stale expiry fires. Never exposed to callers.
- **Lifecycle**: absent → present (via upsert) → present-with-newer-generation (via upsert) → absent (via expiry or intentional evict). Overwrite with `0` transitions expiring → persistent.
- **Validation**: key/value must be non-null (invalid-argument otherwise); lifetime validated per FR-009/010/011.

## Entity 2: Hash Entry

- **What**: One top-level key mapped to a set of field-value pairs sharing one top-level lifetime.
- **Attributes**:
  - `key` — top-level identifier; same namespace as simple keys (a top-level key holds *either* a simple *or* a hash entry, never both).
  - `fields` — map of field-name → value. Non-empty after creation; empty via removing last field leaves the top-level key present-but-empty unless explicitly evicted (decision: evicting the last field does NOT auto-remove the top key — explicit whole-key evict required; keeps FR-014/015 orthogonal).
  - `lifetimeMillis`, `generation` — same semantics as Simple Entry, bound to the top-level key, never per-field.
- **Lifecycle**: absent → present (first field upsert creates entry) → mutated (field upsert/evict bumps generation, lifetime unchanged unless the operation carries a new lifetime for the whole entry) → absent (whole-key evict or expiry).
- **Validation**: key/field/value non-null; lifetime rules identical to Simple Entry; field ops on a simple key (and vice versa) yield type-mismatch, never coercion.

## Entity 3: Lifetime

- **What**: Value object governing automatic disappearance.
- **States**: `PERSISTENT (0)` | `EXPIRING (>0 whole millis)` | `INVALID (<0 or fractional — never stored)`.
- **Transitions**: set at write time; replaced wholesale on overwrite; cancelled by overwrite-with-`0`; consumed by scheduler firing (present → absent if generation matches).
- **Rules**: single lifetime per top-level key; no per-field lifetimes in v1; `expiresAt = writeTime + lifetime` computed with injected clock.

## Entity 4: Operation Outcome

- **What**: Result of every read/mutation. Statuses: `OK` | `NOT_FOUND` | `TYPE_MISMATCH` | `INVALID_ARGUMENT`.
- **Carries**: for reads, the value/fields on `OK`, nothing on `NOT_FOUND`; for mismatches/invalid, a stable reason code (no stack traces, no internal state).
- **Rules**: missing evict targets → `OK` (idempotent, FR-016); type errors → `TYPE_MISMATCH`; bad lifetimes/nulls → `INVALID_ARGUMENT`; upserts never return prior values.

## Relationships

```text
TopKey (1) ──holds──> (0..1) SimpleEntry
TopKey (1) ──holds──> (0..1) HashEntry   [exclusive with SimpleEntry]
TopKey (1) ──has───> (1) Lifetime        [0 = persistent]
TopKey (1) ──versioned──> (1) Generation [internal]
Operation ──yields──> (1) Outcome
```

## State-transition summary

| From | Event | To |
|------|-------|----|
| absent | upsert (any valid lifetime) | present (gen+1, scheduled iff lifetime > 0) |
| present (gen N) | upsert new lifetime | present (gen N+1, old fire becomes no-op) |
| present expiring | upsert with `0` | present persistent (gen N+1, no schedule) |
| present | whole-key evict | absent (gen+1 tombstone, pending fires no-op) |
| present hash | field evict (siblings remain) | present hash minus field (gen+1) |
| present | TTL fire with matching gen | absent |
| present (gen N+1) | TTL fire with stale gen N | no change |
