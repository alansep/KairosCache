# KairosCache
A lightweight solution for Java caching

## Time seam (testability)
Production wires `System.currentTimeMillis` through an injectable `Clock`; tests inject a
manual/fixed `Clock` to assert TTL deterministically without real sleeps (see
`specs/001-indexed-cache-library/research.md`, Decision 6). 
