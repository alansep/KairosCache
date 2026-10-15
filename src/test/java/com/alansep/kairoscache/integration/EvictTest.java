package com.alansep.kairoscache.integration;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.alansep.kairoscache.application.KairosCache;
import org.junit.jupiter.api.Test;

/**
 * Integration: evict invalidates pending TTL fires (no resurrection, stale fire no-op).
 */
class EvictTest {
    /** Evict-then-recreate as persistent survives the original deadline. */
    @Test
    void evictCancelsPendingTtlFire() throws InterruptedException {
        try (KairosCache<String, String, String> cache = KairosCache.create()) {
            cache.put("k", "expiring", 400);
            cache.evict("k");
            cache.put("k", "persistent", 0);
            Thread.sleep(800);
            assertEquals("persistent", cache.get("k").data().orElseThrow());
        }
    }

    /** Field evict then whole-key TTL expiry removes the (now smaller) entry. */
    @Test
    void fieldEvictKeepsTtlOnWholeKey() throws InterruptedException {
        try (KairosCache<String, String, String> cache = KairosCache.create()) {
            cache.hput("h", "a", "1", 400);
            cache.hput("h", "b", "2", 400);
            cache.hevict("h", "a");
            assertEquals("2", cache.hget("h", "b").data().orElseThrow());
            Thread.sleep(800);
            assertEquals(
                    com.alansep.kairoscache.domain.model.Status.NOT_FOUND,
                    cache.hget("h", "b").status());
        }
    }
}
