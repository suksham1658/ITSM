package com.nbfc.itsm.audit;

import com.nbfc.itsm.domain.AuditLog;
import com.nbfc.itsm.domain.AuditLogRepository;
import com.nbfc.itsm.domain.Employee;
import com.nbfc.itsm.domain.EmployeeRepository;
import com.nbfc.itsm.domain.Ticket;
import com.nbfc.itsm.domain.TicketRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import javax.persistence.criteria.Predicate;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Audit Trail page: read-only search over {@code audit_log}, newest first. Nothing here can change or
 * delete an entry (the table also has an immutability trigger).
 */
@Service
public class AuditTrailService {

    public static final int PAGE_SIZE = 50;
    private static final ZoneId IST = ZoneId.of("Asia/Kolkata");

    private final AuditLogRepository auditLogRepository;
    private final EmployeeRepository employeeRepository;
    private final TicketRepository ticketRepository;

    public AuditTrailService(AuditLogRepository auditLogRepository, EmployeeRepository employeeRepository,
                             TicketRepository ticketRepository) {
        this.auditLogRepository = auditLogRepository;
        this.employeeRepository = employeeRepository;
        this.ticketRepository = ticketRepository;
    }

    @PreAuthorize("hasAuthority('AUDIT_VIEW')")
    @Transactional(readOnly = true)
    public Result search(Filter f, int page) {
        Specification<AuditLog> spec = spec(f);
        Page<AuditLog> rows = auditLogRepository.findAll(spec,
                PageRequest.of(Math.max(page, 0), PAGE_SIZE, Sort.by(Sort.Direction.DESC, "occurredAtUtc", "auditLogId")));
        Set<Long> employeeIds = new HashSet<Long>();
        Set<Long> ticketIds = new HashSet<Long>();
        for (AuditLog a : rows.getContent()) {
            if (a.getEmployeeId() != null) {
                employeeIds.add(a.getEmployeeId());
            }
            if (a.getTicketId() != null) {
                ticketIds.add(a.getTicketId());
            }
        }
        Map<Long, String> names = new HashMap<Long, String>();
        for (Employee e : employeeRepository.findAllById(employeeIds)) {
            names.put(e.getEmployeeId(), e.getDisplayName());
        }
        Map<Long, String> numbers = new HashMap<Long, String>();
        for (Ticket t : ticketRepository.findAllById(ticketIds)) {
            numbers.put(t.getTicketId(), t.getPublicNumber());
        }
        List<Row> out = new ArrayList<Row>();
        for (AuditLog a : rows.getContent()) {
            out.add(new Row(a, a.getEmployeeId() == null ? null : names.get(a.getEmployeeId()),
                    a.getTicketId() == null ? null : numbers.get(a.getTicketId())));
        }
        return new Result(out, rows.getNumber(), rows.getTotalPages(), rows.getTotalElements());
    }

    @PreAuthorize("hasAuthority('AUDIT_VIEW')")
    @Transactional(readOnly = true)
    public List<String> modules() {
        return auditLogRepository.distinctModules();
    }

    @PreAuthorize("hasAuthority('AUDIT_VIEW')")
    @Transactional(readOnly = true)
    public List<String> actions() {
        return auditLogRepository.distinctActions();
    }

    private Specification<AuditLog> spec(Filter f) {
        // Resolved up front: people / tickets matching the text boxes.
        final Set<Long> userIds = new HashSet<Long>();
        final String user = trim(f.getUser());
        if (user != null) {
            for (Employee e : employeeRepository
                    .findByDisplayNameContainingIgnoreCaseOrEmployeeNoContainingIgnoreCaseOrSamAccountNameContainingIgnoreCase(user, user, user)) {
                userIds.add(e.getEmployeeId());
            }
        }
        final Set<Long> ticketIds = new HashSet<Long>();
        final String ticket = trim(f.getTicket());
        if (ticket != null) {
            for (Ticket t : ticketRepository.findAll((root, q, cb) ->
                    cb.like(cb.lower(root.get("publicNumber")), "%" + ticket.toLowerCase() + "%"))) {
                ticketIds.add(t.getTicketId());
            }
        }
        return (root, query, cb) -> {
            List<Predicate> and = new ArrayList<Predicate>();
            if (user != null) {
                Predicate byNo = cb.like(cb.lower(root.get("employeeNo")), "%" + user.toLowerCase() + "%");
                and.add(userIds.isEmpty() ? byNo : cb.or(byNo, root.get("employeeId").in(userIds)));
            }
            if (ticket != null) {
                and.add(ticketIds.isEmpty() ? cb.disjunction() : root.get("ticketId").in(ticketIds));
            }
            if (trim(f.getModule()) != null) {
                and.add(cb.equal(root.get("moduleCode"), f.getModule().trim()));
            }
            if (trim(f.getAction()) != null) {
                and.add(cb.equal(root.get("actionCode"), f.getAction().trim()));
            }
            if (trim(f.getResult()) != null) {
                and.add(cb.equal(root.get("resultCode"), f.getResult().trim()));
            }
            if (trim(f.getText()) != null) {
                String like = "%" + f.getText().trim().toLowerCase() + "%";
                and.add(cb.or(cb.like(cb.lower(root.get("newValue")), like), cb.like(cb.lower(root.get("oldValue")), like)));
            }
            if (f.getFrom() != null) {
                and.add(cb.greaterThanOrEqualTo(root.get("occurredAtUtc"), f.getFrom().atStartOfDay(IST).toInstant()));
            }
            if (f.getTo() != null) {
                and.add(cb.lessThan(root.get("occurredAtUtc"), f.getTo().plusDays(1).atStartOfDay(IST).toInstant()));
            }
            return cb.and(and.toArray(new Predicate[0]));
        };
    }

    private static String trim(String s) {
        return StringUtils.hasText(s) ? s.trim() : null;
    }

    /** Page filters (all optional). Dates are IST calendar days, inclusive. */
    public static class Filter {
        private String user;
        private String ticket;
        private String module;
        private String action;
        private String result;
        private String text;
        private LocalDate from;
        private LocalDate to;

        public String getUser() { return user; }
        public void setUser(String user) { this.user = user; }
        public String getTicket() { return ticket; }
        public void setTicket(String ticket) { this.ticket = ticket; }
        public String getModule() { return module; }
        public void setModule(String module) { this.module = module; }
        public String getAction() { return action; }
        public void setAction(String action) { this.action = action; }
        public String getResult() { return result; }
        public void setResult(String result) { this.result = result; }
        public String getText() { return text; }
        public void setText(String text) { this.text = text; }
        public LocalDate getFrom() { return from; }
        public void setFrom(LocalDate from) { this.from = from; }
        public LocalDate getTo() { return to; }
        public void setTo(LocalDate to) { this.to = to; }
    }

    /** One table row with the person's name and the ticket number resolved. */
    public static class Row {
        private final AuditLog log;
        private final String userName;
        private final String ticketNumber;

        Row(AuditLog log, String userName, String ticketNumber) {
            this.log = log;
            this.userName = userName;
            this.ticketNumber = ticketNumber;
        }

        public AuditLog getLog() { return log; }
        public String getUserName() { return userName; }
        public String getTicketNumber() { return ticketNumber; }
    }

    public static class Result {
        private final List<Row> rows;
        private final int page;
        private final int totalPages;
        private final long total;

        Result(List<Row> rows, int page, int totalPages, long total) {
            this.rows = rows;
            this.page = page;
            this.totalPages = totalPages;
            this.total = total;
        }

        public List<Row> getRows() { return rows; }
        public int getPage() { return page; }
        public int getTotalPages() { return totalPages; }
        public long getTotal() { return total; }
        public boolean isHasNext() { return page + 1 < totalPages; }
        public boolean isHasPrevious() { return page > 0; }
    }
}
