package com.nbfc.itsm.sla;

import com.nbfc.itsm.domain.BusinessCalendar;
import com.nbfc.itsm.domain.BusinessCalendarRepository;
import com.nbfc.itsm.domain.Holiday;
import com.nbfc.itsm.domain.HolidayRepository;
import com.nbfc.itsm.domain.SlaPolicy;
import com.nbfc.itsm.domain.SlaPolicyRepository;
import com.nbfc.itsm.domain.SystemSettingRepository;
import com.nbfc.itsm.domain.Ticket;
import com.nbfc.itsm.domain.TicketSla;
import com.nbfc.itsm.domain.TicketSlaRepository;
import com.nbfc.itsm.exception.ItsmException;
import com.nbfc.itsm.util.TimeUtc;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * SLA clocks. All targets are in <b>business minutes</b> (business calendar + holidays, IST) unless the policy is
 * 24x7.
 * <ul>
 *   <li>Start at submit: response and resolve due = start + policy minutes (+ minutes spent on hold).</li>
 *   <li>Hold pauses the clock; when work resumes the business minutes on hold are added to the due times.</li>
 *   <li>First response after its due time sets {@code responseBreached}.</li>
 *   <li>Resolve: MET when on time, BREACHED when late (the resolve time is kept either way).</li>
 *   <li>Re-open ("Not resolved"): a fresh resolution window from now.</li>
 *   <li>Priority change: due times recalculated from the start with the new policy.</li>
 *   <li>NEAR when at most {@code sla.near-percent} (System Configuration, default 20) of the business-time window
 *       is left.</li>
 * </ul>
 */
@Service
public class SlaService {

    private static final ZoneId IST = ZoneId.of("Asia/Kolkata");
    public static final int DEFAULT_NEAR_PERCENT = 20;

    private final SlaPolicyRepository slaPolicyRepository;
    private final TicketSlaRepository ticketSlaRepository;
    private final BusinessCalendarRepository calendarRepository;
    private final HolidayRepository holidayRepository;
    private final SystemSettingRepository settingRepository;

    public SlaService(SlaPolicyRepository slaPolicyRepository,
                      TicketSlaRepository ticketSlaRepository,
                      BusinessCalendarRepository calendarRepository,
                      HolidayRepository holidayRepository,
                      SystemSettingRepository settingRepository) {
        this.slaPolicyRepository = slaPolicyRepository;
        this.ticketSlaRepository = ticketSlaRepository;
        this.calendarRepository = calendarRepository;
        this.holidayRepository = holidayRepository;
        this.settingRepository = settingRepository;
    }

    // ------------------------------------------------------------------ lifecycle

    @Transactional
    public TicketSla startClocks(Ticket ticket) {
        SlaPolicy policy = policyFor(ticket.getPriorityCode());
        Instant start = TimeUtc.now();
        TicketSla sla = ticketSlaRepository.findByTicket(ticket).orElse(new TicketSla());
        sla.setTicket(ticket);
        sla.setSlaPolicy(policy);
        sla.setSlaStartUtc(start);
        sla.setPausedMinutes(0);
        sla.setPausedAtUtc(null);
        sla.setPaused(false);
        sla.setResponseBreached(false);
        sla.setNearAlertedUtc(null);
        sla.setBreachAlertedUtc(null);
        computeDue(sla);
        sla.setStateCode("WITHIN");
        refreshState(sla, start);
        return ticketSlaRepository.save(sla);
    }

    /** First reply from the desk / implementor; late when after the response due time (hold time excluded). */
    @Transactional
    public void markFirstResponse(Ticket ticket) {
        TicketSla sla = ticketSlaRepository.findByTicket(ticket).orElse(null);
        if (sla == null || sla.getFirstResponseUtc() != null) {
            return;
        }
        Instant now = TimeUtc.now();
        sla.setFirstResponseUtc(now);
        if (now.isAfter(effectiveDue(sla, sla.getResponseDueUtc(), now))) {
            sla.setResponseBreached(true);
        }
        refreshState(sla, now);
        ticketSlaRepository.save(sla);
    }

    /** Resolve: MET when on time, BREACHED when late. */
    @Transactional
    public void markResolved(Ticket ticket) {
        TicketSla sla = ticketSlaRepository.findByTicket(ticket).orElse(null);
        if (sla == null) {
            return;
        }
        Instant now = TimeUtc.now();
        resumeClock(sla, now);
        sla.setResolvedUtc(now);
        sla.setStateCode(now.isAfter(sla.getResolveDueUtc()) ? "BREACHED" : "MET");
        ticketSlaRepository.save(sla);
    }

    /** Hold (true) pauses the clock; resuming (false) adds the business minutes on hold to the due times. */
    @Transactional
    public void pause(Ticket ticket, boolean paused) {
        TicketSla sla = ticketSlaRepository.findByTicket(ticket).orElse(null);
        if (sla == null || sla.getResolvedUtc() != null) {
            return;
        }
        Instant now = TimeUtc.now();
        if (paused) {
            if (sla.getPausedAtUtc() == null) {
                sla.setPausedAtUtc(now);
            }
            sla.setPaused(true);
        } else {
            resumeClock(sla, now);
            refreshState(sla, now);
        }
        ticketSlaRepository.save(sla);
    }

    /** "Not resolved" / re-opened: a fresh resolution window starts now (the first response stays recorded). */
    @Transactional
    public void reopen(Ticket ticket) {
        TicketSla sla = ticketSlaRepository.findByTicket(ticket).orElse(null);
        if (sla == null) {
            return;
        }
        Instant now = TimeUtc.now();
        sla.setResolvedUtc(null);
        sla.setSlaStartUtc(now);
        sla.setPausedMinutes(0);
        sla.setPausedAtUtc(null);
        sla.setPaused(false);
        sla.setNearAlertedUtc(null);
        sla.setBreachAlertedUtc(null);
        computeDue(sla);
        sla.setStateCode("WITHIN");
        refreshState(sla, now);
        ticketSlaRepository.save(sla);
    }

    /** Priority changed: use the new policy and recalculate the due times from the start (hold time kept). */
    @Transactional
    public void recalculate(Ticket ticket) {
        TicketSla sla = ticketSlaRepository.findByTicket(ticket).orElse(null);
        if (sla == null) {
            return;
        }
        sla.setSlaPolicy(policyFor(ticket.getPriorityCode()));
        computeDue(sla);
        if (sla.getFirstResponseUtc() != null) {
            sla.setResponseBreached(sla.getFirstResponseUtc().isAfter(sla.getResponseDueUtc()));
        }
        sla.setNearAlertedUtc(null);
        sla.setBreachAlertedUtc(null);
        if (sla.getResolvedUtc() != null) {
            sla.setStateCode(sla.getResolvedUtc().isAfter(sla.getResolveDueUtc()) ? "BREACHED" : "MET");
        } else {
            sla.setStateCode("WITHIN");
            refreshState(sla, TimeUtc.now());
        }
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

    /**
     * Brings a running clock up to date: late response flag, and WITHIN / NEAR / BREACHED from the business time
     * left. Resolved and paused clocks are left alone.
     */
    public void refreshState(TicketSla sla, Instant now) {
        if (sla == null || sla.getResolvedUtc() != null || sla.isPaused() || sla.getResolveDueUtc() == null) {
            return;
        }
        boolean allHours = sla.getSlaPolicy() != null && sla.getSlaPolicy().isAllHours();
        if (sla.getFirstResponseUtc() == null && sla.getResponseDueUtc() != null && now.isAfter(sla.getResponseDueUtc())) {
            sla.setResponseBreached(true);
        }
        if (!now.isBefore(sla.getResolveDueUtc())) {
            sla.setStateCode("BREACHED");
            return;
        }
        long total = Math.max(1L, businessMinutesBetween(sla.getSlaStartUtc(), sla.getResolveDueUtc(), allHours));
        long remaining = businessMinutesBetween(now, sla.getResolveDueUtc(), allHours);
        sla.setStateCode(remaining * 100L <= (long) nearPercent() * total ? "NEAR" : "WITHIN");
    }

    public int nearPercent() {
        try {
            return settingRepository.findById("sla.near-percent")
                    .map(s -> Integer.parseInt(s.getSettingValue().trim())).orElse(DEFAULT_NEAR_PERCENT);
        } catch (RuntimeException ex) {
            return DEFAULT_NEAR_PERCENT;
        }
    }

    // ------------------------------------------------------------------ helpers

    private SlaPolicy policyFor(String priority) {
        return slaPolicyRepository.findByPriorityCodeAndActiveTrue(priority)
                .orElseThrow(() -> new ItsmException("SLA_POLICY_MISSING", "No SLA policy for priority " + priority));
    }

    private void computeDue(TicketSla sla) {
        SlaPolicy p = sla.getSlaPolicy();
        sla.setResponseDueUtc(addMinutes(sla.getSlaStartUtc(), p.getResponseMinutes() + sla.getPausedMinutes(), p.isAllHours()));
        sla.setResolveDueUtc(addMinutes(sla.getSlaStartUtc(), p.getResolutionMinutes() + sla.getPausedMinutes(), p.isAllHours()));
    }

    /** Ends a pause: the business minutes on hold are added and the due times recalculated. */
    private void resumeClock(TicketSla sla, Instant now) {
        if (sla.getPausedAtUtc() != null) {
            boolean allHours = sla.getSlaPolicy() != null && sla.getSlaPolicy().isAllHours();
            long held = businessMinutesBetween(sla.getPausedAtUtc(), now, allHours);
            sla.setPausedMinutes((int) Math.min(Integer.MAX_VALUE, sla.getPausedMinutes() + held));
            sla.setPausedAtUtc(null);
            computeDue(sla);
        }
        sla.setPaused(false);
    }

    /** A due time as it would be if the current pause ended now. */
    private Instant effectiveDue(TicketSla sla, Instant due, Instant now) {
        if (sla.getPausedAtUtc() == null || due == null) {
            return due;
        }
        boolean allHours = sla.getSlaPolicy() != null && sla.getSlaPolicy().isAllHours();
        long held = businessMinutesBetween(sla.getPausedAtUtc(), now, allHours);
        return addMinutes(due, (int) Math.min(Integer.MAX_VALUE, held), allHours);
    }

    /** Working minutes between two instants (calendar + holidays), or plain minutes for 24x7. */
    public long businessMinutesBetween(Instant from, Instant to, boolean allHours) {
        if (from == null || to == null || !to.isAfter(from)) {
            return 0L;
        }
        if (allHours || calendarRepository.count() == 0) {
            return Duration.between(from, to).toMinutes();
        }
        Map<Integer, BusinessCalendar> byDay = calendarByDay();
        LocalDate first = from.atZone(IST).toLocalDate();
        LocalDate last = to.atZone(IST).toLocalDate();
        Set<LocalDate> holidays = new HashSet<LocalDate>();
        for (Holiday h : holidayRepository.findByHolidayDateBetween(first, last)) {
            holidays.add(h.getHolidayDate());
        }
        LocalDateTime a = from.atZone(IST).toLocalDateTime();
        LocalDateTime b = to.atZone(IST).toLocalDateTime();
        long minutes = 0L;
        for (LocalDate d = first; !d.isAfter(last); d = d.plusDays(1)) {
            BusinessCalendar cal = byDay.get(Integer.valueOf(d.getDayOfWeek().getValue()));
            if (cal == null || !cal.isWorkingDay() || holidays.contains(d)) {
                continue;
            }
            LocalDateTime open = LocalDateTime.of(d, cal.getStartTime());
            LocalDateTime close = LocalDateTime.of(d, cal.getEndTime());
            LocalDateTime s = a.isAfter(open) ? a : open;
            LocalDateTime e = b.isBefore(close) ? b : close;
            if (e.isAfter(s)) {
                minutes += Duration.between(s, e).toMinutes();
            }
        }
        return minutes;
    }

    private Map<Integer, BusinessCalendar> calendarByDay() {
        Map<Integer, BusinessCalendar> byDay = new HashMap<Integer, BusinessCalendar>();
        List<BusinessCalendar> rows = calendarRepository.findAllByOrderByWeekdayIsoAsc();
        for (BusinessCalendar row : rows) {
            byDay.put(Integer.valueOf(row.getWeekdayIso()), row);
        }
        return byDay;
    }

    Instant addMinutes(Instant start, int minutes, boolean allHours) {
        if (allHours || calendarRepository.count() == 0) {
            return start.plusSeconds(minutes * 60L);
        }
        Map<Integer, BusinessCalendar> byDay = calendarByDay();
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
            long available = Duration.between(nowLocal, windowEnd).toMinutes();
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
