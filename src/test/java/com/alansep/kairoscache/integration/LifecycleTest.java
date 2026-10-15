package com.alansep.kairoscache.integration;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.alansep.kairoscache.application.KairosCache;
import org.junit.jupiter.api.Test;

/**
 * Lifecycle: close shuts the scheduler down; post-close operations fail fast.
 */
class LifecycleTest {
    /** Every operation throws after close; double close is harmless. */
    @Test
    void postCloseOpsThrowAndDoubleCloseIsHarmless() {
        var cache = KairosCache.<String, String, String>create();
        cache.put("k", "v", 0);
        cache.close();
        assertDoesNotThrow(cache::close);
        assertThrows(IllegalStateException.class, () -> cache.put("k", "v", 0));
        assertThrows(IllegalStateException.class, () -> cache.get("k"));
        assertThrows(IllegalStateException.class, () -> cache.evict("k"));
        assertThrows(IllegalStateException.class, () -> cache.hput("k", "f", "v", 0));
        assertThrows(IllegalStateException.class, () -> cache.hget("k", "f"));
        assertThrows(IllegalStateException.class, () -> cache.hgetAll("k"));
        assertThrows(IllegalStateException.class, () -> cache.hevict("k", "f"));
        assertThrows(IllegalStateException.class, cache::stats);
    }
}
