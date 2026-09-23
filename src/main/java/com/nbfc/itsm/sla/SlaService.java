package com.nbfc.itsm.sla;

import com.nbfc.itsm.domain.BusinessCalendar;
import com.nbfc.itsm.domain.BusinessCalendarRepository;
import com.nbfc.itsm.domain.HolidayRepository;
import com.nbfc.itsm.domain.SlaPolicy;
import com.nbfc.itsm.domain.SlaPolicyRepository;
import com.nbfc.itsm.domain.Ticket;
import com.nbfc.itsm.domain.TicketSla;
import com.nbfc.itsm.domain.TicketSlaRepository;
import com.nbfc.itsm.exception.ItsmException;
import com.nbfc.itsm.util.TimeUtc;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
public class SlaService {

    private static final ZoneId IST = ZoneId.of("Asia/Kolkata");

    private final SlaPolicyRepository slaPolicyRepository;
    private final TicketSlaRepository ticketSlaRepository;
    private final BusinessCalendarRepository calendarRepository;
    private final HolidayRepository holidayRepository;

    public SlaService(SlaPolicyRepository slaPolicyRepository,
                      TicketSlaRepository ticketSlaRepository,
                      BusinessCalendarRepository calendarRepository,
                      HolidayRepository holidayRepository) {
        this.slaPolicyRepository = slaPolicyRepository;
        this.ticketSlaRepository = ticketSlaRepository;
        this.calendarRepository = calendarRepository;
        this.holidayRepository = holidayRepository;
    }

    @Transactional
    public TicketSla startClocks(Ticket ticket) {
        SlaPolicy policy = slaPolicyRepository.findByPriorityCodeAndActiveTrue(ticket.getPriorityCode())
                .orElseThrow(() -> new ItsmException("SLA_POLICY_MISSING",
                        "No SLA policy for priority " + ticket.getPriorityCode()));
        Instant start = TimeUtc.now();
        TicketSla sla = ticketSlaRepository.findByTicket(ticket).orElse(new TicketSla());
        sla.setTicket(ticket);
        sla.setSlaPolicy(policy);
        sla.setSlaStartUtc(start);
        sla.setResponseDueUtc(addMinutes(start, policy.getResponseMinutes(), policy.isAllHours()));
        sla.setResolveDueUtc(addMinutes(start, policy.getResolutionMinutes(), policy.isAllHours()));
        sla.setPaused(false);
        sla.setStateCode("WITHIN");
        refreshState(sla, start);
        return ticketSlaRepository.save(sla);
    }

    @Transactional
    public void markFirstResponse(Ticket ticket) {
        TicketSla sla = ticketSlaRepository.findByTicket(ticket).orElse(null);
        if (sla == null || sla.getFirstResponseUtc() != null) {
            return;
        }
        sla.setFirstResponseUtc(TimeUtc.now());
        refreshState(sla, TimeUtc.now());
        ticketSlaRepository.save(sla);
    }

    @Transactional
    public void markResolved(Ticket ticket) {
        TicketSla sla = ticketSlaRepository.findByTicket(ticket).orElse(null);
        if (sla == null) {
            return;
        }
        sla.setResolvedUtc(TimeUtc.now());
        sla.setStateCode("MET");
        sla.setPaused(false);
        ticketSlaRepository.save(sla);
    }

    @Transactional
    public void pause(Ticket ticket, boolean paused) {
        TicketSla sla = ticketSlaRepository.findByTicket(ticket).orElse(null);
        if (sla == null || "MET".equals(sla.getStateCode())) {
            return;
        }
        sla.setPaused(paused);
        refreshState(sla, TimeUtc.now());
        ticketSlaRepository.save(sla);
    }

    @Transactional
    public void refresh(Ticket ticket) {
        TicketSla sla = ticketSlaRepository.findByTicket(ticket).orElse(null);
        if (sla == null) {
            return;
        }
        refreshState(sla, TimeUtc.now());
        ticketSlaRepository.save(sla);
    }

    public void refreshState(TicketSla sla, Instant now) {
        if (sla == null || "MET".equals(sla.getStateCode()) || sla.isPaused()) {
            return;
        }
        if (!now.isBefore(sla.getResolveDueUtc())) {
            sla.setStateCode("BREACHED");
            return;
        }
        long total = Math.max(1L, sla.getResolveDueUtc().getEpochSecond() - sla.getSlaStartUtc().getEpochSecond());
        long remaining = sla.getResolveDueUtc().getEpochSecond() - now.getEpochSecond();
        if (remaining * 100L / total <= 20L) {
            sla.setStateCode("NEAR");
        } else {
            sla.setStateCode("WITHIN");
        }
    }

    Instant addMinutes(Instant start, int minutes, boolean allHours) {
        if (allHours || calendarRepository.count() == 0) {
            return start.plusSeconds(minutes * 60L);
        }
        Map<Integer, BusinessCalendar> byDay = new HashMap<Integer, BusinessCalendar>();
        List<BusinessCalendar> rows = calendarRepository.findAllByOrderByWeekdayIsoAsc();
        for (BusinessCalendar row : rows) {
            byDay.put(Integer.valueOf(row.getWeekdayIso()), row);
        }
        ZonedDateTime cursor = start.atZone(IST);
        int left = minutes;
        int guard = 0;
        while (left > 0 && guard < 200000) {
            guard++;
            LocalDate date = cursor.toLocalDate();
            int iso = date.getDayOfWeek().getValue();
            BusinessCalendar cal = byDay.get(Integer.valueOf(iso));
            boolean working = cal != null && cal.isWorkingDay() && !holidayRepository.existsByHolidayDate(date);
            if (!working) {
                cursor = date.plusDays(1).atStartOfDay(IST);
                continue;
            }
            LocalTime startT = cal.getStartTime();
            LocalTime endT = cal.getEndTime();
            LocalDateTime windowStart = LocalDateTime.of(date, startT);
            LocalDateTime windowEnd = LocalDateTime.of(date, endT);
            LocalDateTime nowLocal = cursor.toLocalDateTime();
            if (nowLocal.isBefore(windowStart)) {
                cursor = windowStart.atZone(IST);
                continue;
            }
            if (!nowLocal.isBefore(windowEnd)) {
                cursor = date.plusDays(1).atStartOfDay(IST);
                continue;
            }
            long available = java.time.Duration.between(nowLocal, windowEnd).toMinutes();
            if (available <= 0) {
                cursor = date.plusDays(1).atStartOfDay(IST);
                continue;
            }
            if (left <= available) {
                return nowLocal.plusMinutes(left).atZone(IST).toInstant();
            }
            left -= (int) available;
            cursor = date.plusDays(1).atStartOfDay(IST);
        }
        return start.plusSeconds(minutes * 60L);
    }
}
