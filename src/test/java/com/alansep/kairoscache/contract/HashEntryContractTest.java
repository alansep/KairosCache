package com.alansep.kairoscache.contract;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.alansep.kairoscache.application.KairosCache;
import com.alansep.kairoscache.domain.model.Status;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Contract: hash entry store/read/update + sibling preservation (FR-002, FR-004, FR-005, FR-017).
 */
class HashEntryContractTest {
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

    /** Three fields stored; one read plus full read; one updated; siblings intact. */
    @Test
    void hputHgetHgetAllWithSiblingPreservation() {
        cache.hput("order:99", "status", "OPEN", 0);
        cache.hput("order:99", "total", "100", 0);
        cache.hput("order:99", "owner", "Alan", 0);

        assertEquals("OPEN", cache.hget("order:99", "status").data().orElseThrow());
        var all = cache.hgetAll("order:99");
        assertEquals(Status.OK, all.status());
        assertEquals(Map.of("status", "OPEN", "total", "100", "owner", "Alan"), all.data().orElseThrow());

        cache.hput("order:99", "status", "PAID", 0);
        assertEquals("PAID", cache.hget("order:99", "status").data().orElseThrow());
        assertEquals("100", cache.hget("order:99", "total").data().orElseThrow());
        assertEquals("Alan", cache.hget("order:99", "owner").data().orElseThrow());
    }

    /** Cross-shape operations are type mismatches and leave data untouched. */
    @Test
    void crossShapeOpsAreTypeMismatch() {
        cache.put("simple", "v", 0);
        assertEquals(Status.TYPE_MISMATCH, cache.hget("simple", "f").status());
        assertEquals(Status.TYPE_MISMATCH, cache.hgetAll("simple").status());
        assertEquals("v", cache.get("simple").data().orElseThrow());

        cache.hput("hash", "f", "fv", 0);
        assertEquals(Status.TYPE_MISMATCH, cache.get("hash").status());
        assertEquals(Status.TYPE_MISMATCH, cache.put("hash", "v", 0).status());
        assertEquals("fv", cache.hget("hash", "f").data().orElseThrow());
    }

    /** Missing hash key/field reads report NOT_FOUND. */
    @Test
    void missingHashReadsAreNotFound() {
        assertEquals(Status.NOT_FOUND, cache.hget("absent", "f").status());
        assertEquals(Status.NOT_FOUND, cache.hgetAll("absent").status());
        cache.hput("h", "a", "1", 0);
        assertEquals(Status.NOT_FOUND, cache.hget("h", "nope").status());
        assertTrue(cache.hget("h", "nope").data().isEmpty());
    }
}
