package com.alansep.kairoscache.unit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.alansep.kairoscache.application.KairosCache;
import com.alansep.kairoscache.domain.model.Status;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Unit: hgetAll snapshot-copy semantics + type mismatch in both directions.
 */
class HashEntryTest {
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

    /** hgetAll returns an unmodifiable snapshot detached from later writes. */
    @Test
    void hgetAllIsSnapshotCopy() {
        cache.hput("h", "a", "1", 0);
        Map<String, String> first = cache.hgetAll("h").data().orElseThrow();
        assertThrows(UnsupportedOperationException.class, () -> first.put("x", "y"));

        cache.hput("h", "b", "2", 0);
        assertEquals(Map.of("a", "1"), first, "previous snapshot must not see later writes");
        assertEquals(Map.of("a", "1", "b", "2"), cache.hgetAll("h").data().orElseThrow());
    }

    /** Type mismatch is reported in both directions without touching data. */
    @Test
    void typeMismatchBothDirections() {
        cache.put("s", "v", 0);
        assertEquals(Status.TYPE_MISMATCH, cache.hput("s", "f", "x", 0).status());
        assertTrue(cache.hgetAll("s").status() == Status.TYPE_MISMATCH);
        assertEquals("v", cache.get("s").data().orElseThrow());

        cache.hput("h", "f", "fv", 0);
        assertEquals(Status.TYPE_MISMATCH, cache.get("h").status());
        assertEquals("fv", cache.hget("h", "f").data().orElseThrow());
    }
}
