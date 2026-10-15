package com.alansep.kairoscache.infrastructure.time;

import com.alansep.kairoscache.domain.port.ExpiryPorts.ClockPort;
import java.time.Clock;
import java.util.Objects;

/**
 * System clock adapter (production seam for {@link ClockPort}).
 */
public final class SystemClockAdapter implements ClockPort {
    private final Clock clock;

    /**
     * Creates an adapter on the system UTC clock.
     */
    public SystemClockAdapter() {
        this(Clock.systemUTC());
    }

    /**
     * Creates an adapter on the given clock (tests inject a fixed/manual clock).
     *
     * @param clock delegate clock
     */
    public SystemClockAdapter(Clock clock) {
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    /**
     * Returns the current epoch millis.
     *
     * @return now in millis
     */
    @Override
    public long millis() {
        return clock.millis();
    }
}
