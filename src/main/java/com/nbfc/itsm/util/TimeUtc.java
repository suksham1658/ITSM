package com.nbfc.itsm.util;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

public final class TimeUtc {

    private TimeUtc() {
    }

    public static Instant now(Clock clock) {
        return Instant.now(clock);
    }

    public static Instant now() {
        return Instant.now(Clock.systemUTC());
    }

    public static ZoneOffset zone() {
        return ZoneOffset.UTC;
    }
}
