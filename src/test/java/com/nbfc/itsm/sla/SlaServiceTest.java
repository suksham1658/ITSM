package com.nbfc.itsm.sla;

import com.nbfc.itsm.domain.Ticket;
import com.nbfc.itsm.domain.TicketSla;
import com.nbfc.itsm.seed.CatalogSeedService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class SlaServiceTest {

    private static final ZoneId IST = ZoneId.of("Asia/Kolkata");

    @Autowired
    private CatalogSeedService catalogSeedService;
    @Autowired
    private SlaService slaService;

    @BeforeEach
    void seed() {
        catalogSeedService.ensureSeeded();
    }

    @Test
    void allHoursAddsWallClockMinutes() {
        Instant start = Instant.parse("2026-01-23T12:00:00Z");
        Instant due = slaService.addMinutes(start, 120, true);
        assertEquals(start.plusSeconds(120 * 60L), due);
    }

    @Test
    void businessHoursSkipWeekendAndRepublicDay() {
        // Friday 17:00 IST 23 Jan 2026; 120 working minutes → skip Sat/Sun and
        // Republic Day Monday 26 Jan → Tuesday 10:00 IST.
        Instant start = ZonedDateTime.of(LocalDateTime.of(2026, 1, 23, 17, 0), IST).toInstant();
        Instant due = slaService.addMinutes(start, 120, false);
        ZonedDateTime dueIst = due.atZone(IST);
        assertEquals(2026, dueIst.getYear());
        assertEquals(1, dueIst.getMonthValue());
        assertEquals(27, dueIst.getDayOfMonth());
        assertEquals(10, dueIst.getHour());
        assertEquals(0, dueIst.getMinute());
    }

    @Test
    void refreshStateMarksNearAndBreached() {
        TicketSla sla = new TicketSla();
        Instant start = Instant.parse("2026-09-11T10:00:00Z");
        sla.setSlaStartUtc(start);
        sla.setResolveDueUtc(start.plusSeconds(100));
        sla.setStateCode("WITHIN");
        sla.setPaused(false);
        slaService.refreshState(sla, start.plusSeconds(85));
        assertEquals("NEAR", sla.getStateCode());
        slaService.refreshState(sla, start.plusSeconds(100));
        assertEquals("BREACHED", sla.getStateCode());
    }

    @Test
    void startClocksRequirePolicy() {
        Ticket ticket = new Ticket();
        ticket.setPriorityCode("NotAPriority");
        try {
            slaService.startClocks(ticket);
            throw new AssertionError("expected missing policy");
        } catch (com.nbfc.itsm.exception.ItsmException ex) {
            assertEquals("SLA_POLICY_MISSING", ex.getCode());
        }
    }

    @Test
    void pausedClockDoesNotBreach() {
        TicketSla sla = new TicketSla();
        Instant start = Instant.parse("2026-09-11T10:00:00Z");
        sla.setSlaStartUtc(start);
        sla.setResolveDueUtc(start.plusSeconds(10));
        sla.setPaused(true);
        sla.setStateCode("WITHIN");
        slaService.refreshState(sla, start.plusSeconds(50));
        assertEquals("WITHIN", sla.getStateCode());
        assertTrue(sla.isPaused());
    }
}
