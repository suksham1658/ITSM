package com.nbfc.itsm.util;

import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

import static org.junit.jupiter.api.Assertions.assertEquals;

class TimeUtcTest {

    @Test
    void nowUsesUtcClock() {
        Clock clock = Clock.fixed(Instant.parse("2026-09-11T11:00:00Z"), ZoneOffset.UTC);
        assertEquals(Instant.parse("2026-09-11T11:00:00Z"), TimeUtc.now(clock));
        assertEquals(ZoneOffset.UTC, TimeUtc.zone());
    }
}
