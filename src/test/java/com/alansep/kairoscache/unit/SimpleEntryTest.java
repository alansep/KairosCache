package com.alansep.kairoscache.unit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.alansep.kairoscache.application.KairosCache;
import com.alansep.kairoscache.domain.model.Status;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Unit: null rejection + cross-shape type mismatch for simple keys.
 */
class SimpleEntryTest {
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

    /** Null key/value are invalid and store nothing. */
    @Test
    void nullKeyOrValueIsInvalidArgument() {
        assertEquals(Status.INVALID_ARGUMENT, cache.put(null, "v", 0).status());
        assertEquals(Status.INVALID_ARGUMENT, cache.put("k", null, 0).status());
        assertEquals(Status.INVALID_ARGUMENT, cache.get(null).status());
        assertEquals(Status.NOT_FOUND, cache.get("k").status());
    }

    /** A field write on a simple key is a type mismatch and leaves data untouched. */
    @Test
    void hputOverSimpleIsTypeMismatch() {
        cache.put("k", "v", 0);
        assertEquals(Status.TYPE_MISMATCH, cache.hput("k", "f", "fv", 0).status());
        assertEquals("v", cache.get("k").data().orElseThrow());
        assertTrue(cache.hget("k", "f").status() == Status.TYPE_MISMATCH);
    }
}
