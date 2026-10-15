package com.alansep.kairoscache.unit;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.alansep.kairoscache.application.KairosCache;
import com.alansep.kairoscache.domain.model.Status;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.Test;

/**
 * Unit: TTL validation rules on a manual clock (no real sleeps).
 *
 * <p>{@code 0} is persistent, negatives are {@code INVALID_ARGUMENT} with nothing stored,
 * and fractional millis are unrepresentable by the {@code long} signature.
 */
class TtlValidationTest {
    /** Minimal mutable clock over epoch millis. */
    static final class ManualClock extends Clock {
        private final AtomicLong millis;
        private final ZoneId zone = ZoneId.of("UTC");

        ManualClock(long startMillis) {
            this.millis = new AtomicLong(startMillis);
        }

        /** Advances the clock. */
        void advance(long deltaMillis) {
            millis.addAndGet(deltaMillis);
        }

        @Override
        public ZoneId getZone() {
            return zone;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return Instant.ofEpochMilli(millis.get());
        }

        @Override
        public long millis() {
            return millis.get();
        }
    }

    /** Zero TTL never expires, even far in the future. */
    @Test
    void zeroTtlIsPersistent() {
        ManualClock clock = new ManualClock(1_000);
        try (KairosCache<String, String, String> cache = KairosCache.create(clock)) {
            assertEquals(Status.OK, cache.put("k", "v", 0).status());
            clock.advance(60_000);
            assertEquals("v", cache.get("k").data().orElseThrow());
        }
    }

    /** Negative TTL is rejected and stores nothing. */
    @Test
    void negativeTtlIsInvalidAndStoresNothing() {
        ManualClock clock = new ManualClock(1_000);
        try (KairosCache<String, String, String> cache = KairosCache.create(clock)) {
            assertEquals(Status.INVALID_ARGUMENT, cache.put("k", "v", -1).status());
            assertEquals(Status.INVALID_ARGUMENT, cache.hput("h", "f", "v", -5).status());
            assertEquals(Status.NOT_FOUND, cache.get("k").status());
            assertEquals(Status.NOT_FOUND, cache.hget("h", "f").status());
        }
    }

    /** Positive TTL expires on the manual clock via the lazy path. */
    @Test
    void positiveTtlExpiresLazily() {
        ManualClock clock = new ManualClock(1_000);
        try (KairosCache<String, String, String> cache = KairosCache.create(clock)) {
            cache.put("k", "v", 500);
            assertEquals("v", cache.get("k").data().orElseThrow());
            clock.advance(500);
            assertEquals(Status.NOT_FOUND, cache.get("k").status());
        }
    }

    /** Whole-millis surface: 1 ms is the smallest representable step. */
    @Test
    void wholeMillisOnly() {
        ManualClock clock = new ManualClock(1_000);
        try (KairosCache<String, String, String> cache = KairosCache.create(clock)) {
            cache.put("k", "v", 1);
            clock.advance(1);
            assertEquals(Status.NOT_FOUND, cache.get("k").status());
            // A fractional-millis API would need doubles/Strings; the long signature
            // makes fractions unrepresentable — Duration nanos must be truncated first.
            assertEquals(500, Duration.ofMillis(500).toMillis());
        }
    }
}
