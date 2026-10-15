package com.alansep.kairoscache.integration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.alansep.kairoscache.application.KairosCache;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.Test;

/**
 * Stress: 10k mixed read/write/evict/expiry operations across virtual threads complete
 * without exceptions or torn reads, and stats stay consistent.
 */
class ConcurrencyTest {
    /** 10k mixed ops over shared keys; asserts no failures and hits+misses==reads. */
    @Test
    void mixedWorkloadHasNoErrorsOrTornReads() throws Exception {
        try (KairosCache<String, String, String> cache = KairosCache.create();
                var pool = Executors.newVirtualThreadPerTaskExecutor()) {
            int writers = 4;
            int opsPerWriter = 2_500;
            AtomicLong reads = new AtomicLong();
            List<Callable<Void>> jobs = new ArrayList<>();
            for (int w = 0; w < writers; w++) {
                int id = w;
                jobs.add(() -> {
                    for (int i = 0; i < opsPerWriter; i++) {
                        String key = "k-" + (i % 64) + "-w" + id;
                        String field = "f-" + (i % 8);
                        int kind = i % 6;
                        switch (kind) {
                            case 0 -> cache.put(key, "v" + i, 0);
                            case 1 -> {
                                var got = cache.get(key);
                                reads.incrementAndGet();
                                got.data().ifPresent(v -> {
                                    if (!v.startsWith("v")) {
                                        throw new AssertionError("torn read: " + v);
                                    }
                                });
                            }
                            case 2 -> cache.hput(key, field, "hv" + i, 0);
                            case 3 -> {
                                var got = cache.hget(key, field);
                                reads.incrementAndGet();
                                got.data().ifPresent(v -> {
                                    if (!v.startsWith("hv")) {
                                        throw new AssertionError("torn hash read: " + v);
                                    }
                                });
                            }
                            case 4 -> cache.evict(key);
                            default -> cache.hevict(key, field);
                        }
                    }
                    return null;
                });
            }
            List<Future<Void>> futures = pool.invokeAll(jobs);
            for (Future<Void> f : futures) {
                f.get(60, TimeUnit.SECONDS);
            }
            var stats = cache.stats();
            assertEquals(reads.get(), stats.hits() + stats.misses(), "hits+misses must equal reads");
            assertTrue(stats.size() >= 0);
        }
    }
}
