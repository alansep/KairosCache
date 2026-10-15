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
 * Contract: intentional eviction — idempotent, whole-key vs single-field scope
 * (FR-014, FR-015, FR-016).
 */
class EvictContractTest {
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

    /** Evicting a simple key makes it missing; missing evicts still succeed. */
    @Test
    void evictSimpleKeyIsIdempotent() {
        cache.put("user:1", "Alan", 0);
        assertEquals(Status.OK, cache.evict("user:1").status());
        assertEquals(Status.NOT_FOUND, cache.get("user:1").status());
        assertEquals(Status.OK, cache.evict("user:1").status());
        assertEquals(Status.OK, cache.evict("never-existed").status());
    }

    /** Evicting one hash field keeps siblings; whole-key evict removes everything. */
    @Test
    void fieldVsWholeKeyScope() {
        cache.hput("order:99", "status", "OPEN", 0);
        cache.hput("order:99", "total", "100", 0);

        assertEquals(Status.OK, cache.hevict("order:99", "total").status());
        assertEquals(Status.NOT_FOUND, cache.hget("order:99", "total").status());
        assertEquals("OPEN", cache.hget("order:99", "status").data().orElseThrow());
        assertEquals(Map.of("status", "OPEN"), cache.hgetAll("order:99").data().orElseThrow());

        assertEquals(Status.OK, cache.evict("order:99").status());
        assertEquals(Status.NOT_FOUND, cache.hget("order:99", "status").status());
        assertEquals(Status.NOT_FOUND, cache.hgetAll("order:99").status());
    }

    /** Missing field evict succeeds; last-field removal leaves an empty hash present. */
    @Test
    void missingFieldEvictSucceedsAndEmptyHashStaysPresent() {
        assertEquals(Status.OK, cache.hevict("absent", "f").status());
        cache.hput("h", "only", "1", 0);
        assertEquals(Status.OK, cache.hevict("h", "missing").status());
        assertEquals(Status.OK, cache.hevict("h", "only").status());
        var all = cache.hgetAll("h");
        assertEquals(Status.OK, all.status());
        assertTrue(all.data().orElseThrow().isEmpty());
    }
}
