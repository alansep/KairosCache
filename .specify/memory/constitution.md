# KairosCache Constitution

## Core Principles

### I. Library-First & API Simplicity

Every feature MUST start as a standalone, self-contained library unit with a clear
purpose; no organizational-only modules. The public API MUST be minimal, fluent,
and self-documenting (e.g., `Cache<K,V>` with `get/put/invalidate`), with Javadoc
on every public type. Breaking API changes MUST require a MAJOR bump and a
migration note.

### II. Zero-Dependency Lightness (NON-NEGOTIABLE)

Runtime MUST have zero external dependencies — only the JDK. Test scope MAY use
JUnit 6 and related test tooling. Any proposal to add a runtime dependency MUST be
rejected unless it proves, with benchmarks, that JDK-only implementation cannot
meet the requirement, and MUST trigger a MINOR version discussion.

### III. Test-First (NON-NEGOTIABLE)

TDD is mandatory: tests written → reviewed → observed failing → then implement.
Red-Green-Refactor MUST be strictly enforced. Every cache behavior (hit/miss,
eviction, TTL/TTI expiry, concurrency) MUST have a deterministic unit test. Flaky
timing-based tests are forbidden; time MUST be injectable (e.g., `Clock`) for
expiry tests.

### IV. Correctness & Thread-Safety

Correctness overrides performance. Every public operation MUST be thread-safe
under concurrent access without external synchronization. Implementations MUST
prefer `java.util.concurrent` primitives and lock-free reads where possible.
Data races, lost updates, or visibility failures MUST be treated as P0 defects.
Integration tests MUST cover concurrent put/get/evict storms and expiry races.

### V. Simplicity, Observability & Pragmatic Performance

Start simple per YAGNI; no premature strategies (distributed, off-heap, serialization)
until a spec demands them. Every cache MUST expose hit/miss counts, size, and
eviction counts via a minimal stats API and structured logging hooks — no
`System.out`. Performance work MUST be benchmark-driven (JMH when introduced);
no optimization without a failing performance test or benchmark delta.

## Additional Constraints

### Technology & Performance Standards

- **Stack:** Java 21 (Corretto 21), Gradle 9.x with Kotlin DSL, JUnit 6
  (`junit-bom:6.0.0`). Group `com.alansep.kairoscache`, version `1.0-SNAPSHOT`
  until first SemVer release.
- **Compatibility:** Baseline Java 21; no incubator/preview features without
  explicit constitution amendment.
- **Performance budgets (initial):** `get/put` p99 < 1µs for in-memory hit on
  reference hardware;least-recently-used eviction O(1); memory overhead
  documented per entry in specs. Budgets are refined per-spec, never silently.
- **Security:** No deserialization of untrusted data, no reflection hacks, no
  dynamic classloading. Dependencies (test scope) MUST be scanned before release.

## Development Workflow

### Quality Gates & Review Process

- **Spec-driven:** No code without `speckit.specify → plan → tasks`; spec is the
  source of truth and MUST be updated before behavior changes.
- **Gates:** `./gradlew build` green, new tests for new behavior, JaCoCo-style
  coverage not dropping, public API Javadoc complete, `./gradlew test` stable
  under `--rerun-tasks` and parallel execution.
- **Reviews:** Every PR MUST verify constitution compliance in the description
  (which principle, how tested). Complexity (new abstractions, concurrency
  tricks) MUST be justified against Principle V. At least one reviewer approval
  before merge to `main`; `main` MUST always build.
- **Commits:** Conventional commits with scope, e.g.,
  `feat(KRC-xxxx): ...`, `chore(KRC-xxxx): ...`. Branch naming:
  `<type>/KRC-xxxx-short-kebab`.

## Governance

This constitution supersedes all other practices. Amendments MUST be documented
via PR with rationale, migration plan if behavior/API changes, and version bump:

- **MAJOR:** Backward-incompatible governance or principle removals/redefinitions.
- **MINOR:** New principle or materially expanded guidance.
- **PATCH:** Clarifications, wording, typo fixes.

All PRs and reviews MUST check compliance; violations MUST block merge unless a
waiver with expiry is recorded in the PR. Use `build.gradle.kts` and
`.specify/templates/` as runtime guidance; keep this file lean and enforceable.

**Version**: 1.0.0 | **Ratified**: 2026-10-11 | **Last Amended**: 2026-10-11
