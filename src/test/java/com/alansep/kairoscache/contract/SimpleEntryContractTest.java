package com.alansep.kairoscache.contract;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.alansep.kairoscache.application.KairosCache;
import com.alansep.kairoscache.domain.model.Outcome;
import com.alansep.kairoscache.domain.model.ReadOutcome;
import com.alansep.kairoscache.domain.model.Status;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Contract: simple entry store/read + upsert (FR-001, FR-003, FR-006, FR-007).
 */
class SimpleEntryContractTest {
    private KairosCache<String, String, String> cache;

    /** Fresh cache per test. */
    @BeforeEach
    void setUp() {
        cache = KairosCache.create();
    }

    /** Releases the scheduler thread. */
    @AfterEach
    void tearDown() {
        cache.close();
    }

    /** Stores one key with no expiry and reads it back. */
    @Test
    void putGetRoundTrip() {
        Outcome<String> put = cache.put("user:1", "Alan", 0);
        assertEquals(Status.OK, put.status());
        assertNull(put.value(), "upsert must not return a previous value");

        ReadOutcome<String> got = cache.get("user:1");
        assertEquals(Status.OK, got.status());
        assertTrue(got.data().isPresent());
        assertEquals("Alan", got.data().orElseThrow());
    }

    /** Overwriting a key atomically replaces the value without error. */
    @Test
    void upsertOverwritesWithoutPreviousValue() {
        cache.put("user:1", "Alan", 0);
        Outcome<String> second = cache.put("user:1", "Gabriel", 0);
        assertEquals(Status.OK, second.status());
        assertNull(second.value());

        ReadOutcome<String> got = cache.get("user:1");
        assertEquals(Status.OK, got.status());
        assertEquals("Gabriel", got.data().orElseThrow());
    }

    /** Reading an absent key reports missing. */
    @Test
    void missingKeyIsNotFound() {
        ReadOutcome<String> got = cache.get("absent");
        assertEquals(Status.NOT_FOUND, got.status());
        assertTrue(got.data().isEmpty());
    }
}
