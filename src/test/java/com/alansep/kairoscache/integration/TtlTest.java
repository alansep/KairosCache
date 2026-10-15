package com.alansep.kairoscache.integration;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.alansep.kairoscache.application.KairosCache;
import com.alansep.kairoscache.domain.model.Status;
import java.time.Duration;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Integration: real-clock expiry visibility + stale-fire immunity (the JCache regression).
 */
class TtlTest {
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

    /** Polls until the key disappears or the budget runs out. */
    private void awaitGone(String key, Duration budget) throws InterruptedException {
        long deadline = System.currentTimeMillis() + budget.toMillis();
        while (System.currentTimeMillis() < deadline) {
            if (cache.get(key).status() == Status.NOT_FOUND) {
                return;
            }
            Thread.sleep(25);
        }
        assertEquals(Status.NOT_FOUND, cache.get(key).status(), "entry should have expired: " + key);
    }

    /** A 500 ms entry is gone within the budget while a persistent sibling survives. */
    @Test
    void expiringEntryDisappearsWhilePersistentSurvives() throws InterruptedException {
        cache.put("session:1", "v", 500);
        cache.put("config:theme", "dark", 0);
        assertEquals("v", cache.get("session:1").data().orElseThrow());
        awaitGone("session:1", Duration.ofSeconds(3));
        assertEquals("dark", cache.get("config:theme").data().orElseThrow());
    }

    /** Overwriting with 0 cancels expiry: the entry survives 2x the original lifetime. */
    @Test
    void rewriteWithZeroCancelsExpiry() throws InterruptedException {
        cache.put("k", "v1", 400);
        cache.put("k", "v2", 0);
        Thread.sleep(900);
        assertEquals("v2", cache.get("k").data().orElseThrow());
    }

    /** Overwriting with a new TTL replaces the old deadline (old fire is a no-op). */
    @Test
    void rewriteWithNewTtlReplacesOldDeadline() throws InterruptedException {
        cache.put("k", "v1", 300);
        cache.put("k", "v2", 2_000);
        Thread.sleep(600);
        assertEquals("v2", cache.get("k").data().orElseThrow());
    }
}
