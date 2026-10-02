package com.nbfc.itsm.admin;

import com.nbfc.itsm.audit.AuditRecorder;
import com.nbfc.itsm.domain.BusinessCalendar;
import com.nbfc.itsm.domain.BusinessCalendarRepository;
import com.nbfc.itsm.domain.Holiday;
import com.nbfc.itsm.domain.HolidayRepository;
import com.nbfc.itsm.domain.SlaPolicy;
import com.nbfc.itsm.domain.SlaPolicyRepository;
import com.nbfc.itsm.exception.ItsmException;
import com.nbfc.itsm.security.ItsmUserPrincipal;
import org.springframework.data.domain.Sort;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Admin &gt; SLA Config: response / resolution targets per priority, working hours per weekday and holidays.
 * System Administrator only; changes apply immediately (final authority) and are audited. New targets apply to
 * tickets submitted from now on (and to a ticket whose priority is changed); running clocks keep their due times.
 * Working hours and holidays are used by every later calculation (new tickets, hold time, re-open).
 */
@Service
public class SlaAdminService {

    private static final List<String> PRIORITY_ORDER = Arrays.asList("Critical", "High", "Medium", "Low");
    static final int MAX_MINUTES = 60 * 24 * 60; // 60 days

    private final SlaPolicyRepository policyRepository;
    private final BusinessCalendarRepository calendarRepository;
    private final HolidayRepository holidayRepository;
    private final AuditRecorder auditRecorder;

    public SlaAdminService(SlaPolicyRepository policyRepository, BusinessCalendarRepository calendarRepository,
                           HolidayRepository holidayRepository, AuditRecorder auditRecorder) {
        this.policyRepository = policyRepository;
        this.calendarRepository = calendarRepository;
        this.holidayRepository = holidayRepository;
        this.auditRecorder = auditRecorder;
    }

    @Transactional(readOnly = true)
    public List<SlaPolicy> policies() {
        List<SlaPolicy> all = new ArrayList<SlaPolicy>(policyRepository.findAll());
        all.sort((a, b) -> Integer.compare(rank(a.getPriorityCode()), rank(b.getPriorityCode())));
        return all;
    }

    /** Monday..Sunday, creating a missing weekday as a non-working day. */
    @Transactional
    public List<BusinessCalendar> calendar() {
        Map<Integer, BusinessCalendar> byDay = new HashMap<Integer, BusinessCalendar>();
        for (BusinessCalendar c : calendarRepository.findAllByOrderByWeekdayIsoAsc()) {
            byDay.put(c.getWeekdayIso(), c);
        }
        List<BusinessCalendar> out = new ArrayList<BusinessCalendar>();
        for (int d = 1; d <= 7; d++) {
            BusinessCalendar c = byDay.get(d);
            if (c == null) {
                c = new BusinessCalendar();
                c.setWeekdayIso(d);
                c.setWorkingDay(false);
                c.setStartTime(LocalTime.of(9, 0));
                c.setEndTime(LocalTime.of(18, 0));
                c = calendarRepository.save(c);
            }
            out.add(c);
        }
        return out;
    }

    @Transactional(readOnly = true)
    public List<Holiday> holidays() {
        return holidayRepository.findAll(Sort.by(Sort.Direction.ASC, "holidayDate"));
    }

    @PreAuthorize("hasAuthority('ROLE_SYSTEM_ADMINISTRATOR')")
    @Transactional
    public SlaPolicy updatePolicy(Long id, Integer responseMinutes, Integer resolutionMinutes, boolean allHours,
                                  ItsmUserPrincipal actor) {
        SlaPolicy p = policyRepository.findById(id).orElseThrow(() -> new ItsmException("NOT_FOUND", "SLA policy not found."));
        int response = minutes(responseMinutes, "Response time");
        int resolution = minutes(resolutionMinutes, "Resolution time");
        if (resolution < response) {
            throw new ItsmException("VALIDATION", "Resolution time cannot be shorter than response time.");
        }
        String before = p.getResponseMinutes() + "/" + p.getResolutionMinutes() + (p.isAllHours() ? " 24x7" : "");
        p.setResponseMinutes(response);
        p.setResolutionMinutes(resolution);
        p.setAllHours(allHours);
        policyRepository.save(p);
        audit("SLA_POLICY_UPDATE", p.getPriorityCode() + ": " + before + " -> " + response + "/" + resolution
                + (allHours ? " 24x7" : ""), actor);
        return p;
    }

    /** {@code working[i]}, {@code start[i]}, {@code end[i]} for Monday (index 0) .. Sunday (index 6). */
    @PreAuthorize("hasAuthority('ROLE_SYSTEM_ADMINISTRATOR')")
    @Transactional
    public void updateCalendar(List<Integer> workingDays, List<String> starts, List<String> ends, ItsmUserPrincipal actor) {
        if (starts == null || ends == null || starts.size() != 7 || ends.size() != 7) {
            throw new ItsmException("VALIDATION", "Send start and end times for all seven days.");
        }
        List<BusinessCalendar> days = calendar();
        boolean anyWorking = false;
        StringBuilder summary = new StringBuilder();
        for (int i = 0; i < 7; i++) {
            BusinessCalendar c = days.get(i);
            boolean working = workingDays != null && workingDays.contains(i + 1);
            LocalTime s = time(starts.get(i), c.getStartTime());
            LocalTime e = time(ends.get(i), c.getEndTime());
            if (working && !e.isAfter(s)) {
                throw new ItsmException("VALIDATION", dayName(i + 1) + ": end time must be after start time.");
            }
            anyWorking |= working;
            c.setWorkingDay(working);
            c.setStartTime(s);
            c.setEndTime(e);
            summary.append(dayName(i + 1).substring(0, 3)).append(working ? " " + s + "-" + e : " off").append("; ");
        }
        if (!anyWorking) {
            throw new ItsmException("VALIDATION", "At least one day must be a working day.");
        }
        calendarRepository.saveAll(days);
        audit("SLA_CALENDAR_UPDATE", summary.toString().trim(), actor);
    }

    @PreAuthorize("hasAuthority('ROLE_SYSTEM_ADMINISTRATOR')")
    @Transactional
    public Holiday addHoliday(LocalDate date, String name, ItsmUserPrincipal actor) {
        if (date == null) {
            throw new ItsmException("VALIDATION", "Choose the holiday date.");
        }
        String clean = name == null ? "" : name.trim();
        if (clean.length() < 2 || clean.length() > 100) {
            throw new ItsmException("VALIDATION", "Holiday name must be 2 to 100 characters.");
        }
        if (holidayRepository.existsByHolidayDate(date)) {
            throw new ItsmException("DUPLICATE", date + " is already a holiday.");
        }
        Holiday h = new Holiday();
        h.setHolidayDate(date);
        h.setName(clean);
        h.setNational(false);
        h = holidayRepository.save(h);
        audit("SLA_HOLIDAY_ADD", date + " " + clean, actor);
        return h;
    }

    @PreAuthorize("hasAuthority('ROLE_SYSTEM_ADMINISTRATOR')")
    @Transactional
    public void deleteHoliday(Long id, ItsmUserPrincipal actor) {
        Holiday h = holidayRepository.findById(id).orElseThrow(() -> new ItsmException("NOT_FOUND", "Holiday not found."));
        holidayRepository.delete(h);
        audit("SLA_HOLIDAY_DELETE", h.getHolidayDate() + " " + h.getName(), actor);
    }

    // ------------------------------------------------------------------ helpers

    private static int minutes(Integer v, String label) {
        if (v == null || v < 1 || v > MAX_MINUTES) {
            throw new ItsmException("VALIDATION", label + " must be between 1 and " + MAX_MINUTES + " minutes.");
        }
        return v;
    }

    private static LocalTime time(String v, LocalTime fallback) {
        if (v == null || v.trim().isEmpty()) {
            return fallback;
        }
        try {
            return LocalTime.parse(v.trim());
        } catch (RuntimeException ex) {
            throw new ItsmException("VALIDATION", "Times must look like 09:00.");
        }
    }

    public static String dayName(int iso) {
        return java.time.DayOfWeek.of(iso).getDisplayName(java.time.format.TextStyle.FULL, java.util.Locale.ENGLISH);
    }

    private static int rank(String priority) {
        int i = PRIORITY_ORDER.indexOf(priority);
        return i < 0 ? 99 : i;
    }

    private void audit(String action, String detail, ItsmUserPrincipal actor) {
        auditRecorder.record("ADMIN", action, detail + " (by " + (actor == null ? "?" : actor.getEmployeeNo()) + ")", "SUCCESS");
    }
}
