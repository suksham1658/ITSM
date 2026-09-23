package com.nbfc.itsm.reporting;

import com.nbfc.itsm.domain.Employee;
import com.nbfc.itsm.domain.EmployeeRepository;
import com.nbfc.itsm.domain.Ticket;
import com.nbfc.itsm.domain.TicketSla;
import com.nbfc.itsm.security.ItsmUserPrincipal;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import javax.persistence.EntityManager;
import javax.persistence.TypedQuery;
import javax.persistence.criteria.CriteriaBuilder;
import javax.persistence.criteria.CriteriaQuery;
import javax.persistence.criteria.From;
import javax.persistence.criteria.Join;
import javax.persistence.criteria.JoinType;
import javax.persistence.criteria.Predicate;
import javax.persistence.criteria.Root;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@Service
public class ReportingService {

    public static final ZoneId IST = ZoneId.of("Asia/Kolkata");
    public static final int EXPORT_CAP = 5000;
    public static final int PAGE_SIZE = 25;

    static final String COLOR_NAVY = "#0B1F33";
    static final String COLOR_TEAL = "#0E7C86";
    static final String COLOR_INFO = "#2E6FB0";
    static final String COLOR_WARN = "#B8730F";
    static final String COLOR_DANGER = "#C0392B";
    static final String COLOR_SUCCESS = "#1E8E5A";
    static final String COLOR_GOLD = "#B08D57";
    static final String COLOR_SLATE = "#64748B";
    static final String COLOR_PURPLE = "#6A4FB5";

    private static final List<String> CLOSED = Arrays.asList("Closed", "Rejected");
    private static final List<String> OPEN_STATUSES_EXCLUDE = CLOSED;
    private static final DateTimeFormatter MONTH = DateTimeFormatter.ofPattern("MMM yyyy", Locale.ENGLISH);
    private static final DateTimeFormatter DAY = DateTimeFormatter.ofPattern("dd-MMM-yyyy HH:mm", Locale.ENGLISH);

    private static final List<ReportDefinition> DEFS = Collections.unmodifiableList(Arrays.asList(
            new ReportDefinition("volume", "Ticket Volume", "fa-chart-column",
                    "Ticket creation volume in the selected window."),
            new ReportDefinition("sla", "SLA Performance", "fa-stopwatch",
                    "WITHIN / NEAR / BREACHED clocks on tickets in scope."),
            new ReportDefinition("category", "Category Analysis", "fa-tags",
                    "Volume by category."),
            new ReportDefinition("dept", "Department Analysis", "fa-building",
                    "Volume by requester department on the ticket."),
            new ReportDefinition("implementor", "Implementor Performance", "fa-user-gear",
                    "Assigned, open, resolved and average resolution by implementor."),
            new ReportDefinition("restime", "Resolution Time", "fa-clock",
                    "Average clock time from SLA start to resolved."),
            new ReportDefinition("reopen", "Reopen Rate", "fa-rotate-left",
                    "Reopen is not modelled on tickets."),
            new ReportDefinition("aging", "Aging Report", "fa-hourglass-half",
                    "Open tickets bucketed by age."),
            new ReportDefinition("pendingapproval", "Pending Approval Report", "fa-check-double",
                    "Tickets in Pending Approval."),
            new ReportDefinition("security", "Security Incident Report", "fa-shield-halved",
                    "Security Incident type, Cyber Security category, or Highly Confidential."),
            new ReportDefinition("asset", "Asset-related Incidents", "fa-laptop",
                    "Hardware category until the asset register is wired."),
            new ReportDefinition("monthly", "Monthly Trend", "fa-chart-line",
                    "Opened vs resolved vs closed by month.")
    ));

    private final EntityManager entityManager;
    private final EmployeeRepository employeeRepository;

    public ReportingService(EntityManager entityManager, EmployeeRepository employeeRepository) {
        this.entityManager = entityManager;
        this.employeeRepository = employeeRepository;
    }

    public List<ReportDefinition> definitions() {
        return DEFS;
    }

    public ReportDefinition requireDefinition(String code) {
        for (ReportDefinition d : DEFS) {
            if (d.getCode().equals(code)) {
                return d;
            }
        }
        throw new com.nbfc.itsm.exception.ItsmException("REPORT_UNKNOWN", "Unknown report: " + code);
    }

    @Transactional(readOnly = true)
    public DashboardSnapshot dashboard(ItsmUserPrincipal user) {
        DashboardSnapshot snap = new DashboardSnapshot();
        if (user == null) {
            return snap;
        }
        Employee me = employeeRepository.findById(user.getEmployeeId()).orElse(null);
        snap.setTitle(dashboardTitle(user));
        snap.setScopeLabel(scopeLabel(user, me));

        ReportFilter none = new ReportFilter();
        long open = countTickets(user, me, none, statusNotIn(OPEN_STATUSES_EXCLUDE));
        long neu = countTickets(user, me, none, statusEq("Draft"));
        long pending = countTickets(user, me, none, statusEq("Pending Approval"));
        long inProgress = countTickets(user, me, none, statusEq("In Progress"));
        long resolved = countTickets(user, me, none, statusEq("Resolved"));
        long slaBreach = countSla(user, me, none, "BREACHED");
        long slaNear = countSla(user, me, none, "NEAR");
        long critical = countTickets(user, me, none, criticalOpen());
        long incidents = countTickets(user, me, none, typeName("Incident"));
        long sr = countTickets(user, me, none, typeName("Service Request"));
        long mine = countRequester(user, me);
        long assigned = countAssigned(user, me);
        String avg = formatHours(avgResolutionHours(user, me, none));

        Instant now = Instant.now();
        Instant d30 = now.minus(Duration.ofDays(30));
        Instant d60 = now.minus(Duration.ofDays(60));
        ReportFilter cur = range(d30, now);
        ReportFilter prior = range(d60, d30);

        snap.getKpis().add(kpi("Total Open Tickets", String.valueOf(open),
                trendCounts(countTickets(user, me, cur, statusNotIn(OPEN_STATUSES_EXCLUDE)),
                        countTickets(user, me, prior, statusNotIn(OPEN_STATUSES_EXCLUDE)), false),
                "fa-ticket", "info"));
        snap.getKpis().add(kpi("New Requests", String.valueOf(neu),
                trendCounts(countTickets(user, me, cur, statusEq("Draft")),
                        countTickets(user, me, prior, statusEq("Draft")), false),
                "fa-circle-plus", "teal"));
        snap.getKpis().add(kpi("Pending Approvals", String.valueOf(pending),
                trendCounts(countTickets(user, me, cur, statusEq("Pending Approval")),
                        countTickets(user, me, prior, statusEq("Pending Approval")), true),
                "fa-hourglass-half", "warning"));
        snap.getKpis().add(kpi("In Progress", String.valueOf(inProgress),
                trendCounts(countTickets(user, me, cur, statusEq("In Progress")),
                        countTickets(user, me, prior, statusEq("In Progress")), false),
                "fa-spinner", "navy"));
        snap.getKpis().add(kpi("Resolved", String.valueOf(resolved),
                trendCounts(countTickets(user, me, cur, statusEq("Resolved")),
                        countTickets(user, me, prior, statusEq("Resolved")), false),
                "fa-circle-check", "success"));
        snap.getKpis().add(kpi("SLA Breached", String.valueOf(slaBreach),
                trendCounts(countSla(user, me, cur, "BREACHED"), countSla(user, me, prior, "BREACHED"), true),
                "fa-triangle-exclamation", "danger"));
        snap.getKpis().add(kpi("SLA at risk", String.valueOf(slaNear),
                trendCounts(countSla(user, me, cur, "NEAR"), countSla(user, me, prior, "NEAR"), true),
                "fa-clock", "gold"));
        snap.getKpis().add(kpi("Critical Incidents", String.valueOf(critical),
                trendCounts(countTickets(user, me, cur, criticalOpen()),
                        countTickets(user, me, prior, criticalOpen()), true),
                "fa-shield-halved", "danger"));
        snap.getKpis().add(kpi("Avg. Resolution Time", avg,
                trendHours(avgResolutionHours(user, me, cur), avgResolutionHours(user, me, prior)),
                "fa-stopwatch", "gold"));
        snap.getKpis().add(kpi("Incidents", String.valueOf(incidents),
                trendCounts(countTickets(user, me, cur, typeName("Incident")),
                        countTickets(user, me, prior, typeName("Incident")), true),
                "fa-bolt", "warning"));
        snap.getKpis().add(kpi("Service Requests", String.valueOf(sr),
                trendCounts(countTickets(user, me, cur, typeName("Service Request")),
                        countTickets(user, me, prior, typeName("Service Request")), false),
                "fa-clipboard-list", "info"));
        snap.getKpis().add(kpi("My tickets", String.valueOf(mine),
                new Trend("live", "n/a", "your requests"), "fa-user", "teal"));
        snap.getKpis().add(kpi("Assigned to me", String.valueOf(assigned),
                new Trend("live", "n/a", "implementor queue"), "fa-user-gear", "navy"));

        snap.setStatusChart(statusChart(user, me));
        snap.setCategoryChart(groupBar(user, me, none, "category", "name", COLOR_NAVY, "bar", extraNone()));
        snap.setPriorityChart(priorityChart(user, me));
        snap.setTrendChart(monthlyTrendChart(user, me, none, 6, YearMonth.now(IST)));
        return snap;
    }

    @Transactional(readOnly = true)
    public NamedReport run(ItsmUserPrincipal user, String code, ReportFilter filter, int page, int size) {
        if (user == null) {
            throw new AccessDeniedException("Sign in required.");
        }
        assertReportAllowed(user, code);
        Employee me = employeeRepository.findById(user.getEmployeeId()).orElse(null);
        ReportDefinition def = requireDefinition(code);
        NamedReport report = new NamedReport();
        report.setCode(def.getCode());
        report.setTitle(def.getTitle());
        report.setDescription(def.getDescription());
        report.setPage(Math.max(0, page));
        report.setSize(size <= 0 ? PAGE_SIZE : size);
        if ("reopen".equals(code)) {
            report.setNote("Reopen is not modelled. Rate is 0 — this is not an invented first-time-fix percentage.");
            report.setColumns(Arrays.asList("Metric", "Value"));
            report.getRows().add(new ReportRow("Reopened tickets", "0"));
            report.getRows().add(new ReportRow("Reopen rate", "n/a — no reopen events"));
            report.setTotalRows(2);
            ChartPayload empty = new ChartPayload();
            empty.setType("doughnut");
            empty.setEmpty(true);
            report.setChart(empty);
            return report;
        }
        if ("asset".equals(code)) {
            report.setNote("Asset register is not wired. This report uses the Hardware category as a stand-in.");
        }
        if ("volume".equals(code) || "monthly".equals(code)) {
            fillMonthly(report, user, me, filter, "monthly".equals(code));
        } else if ("sla".equals(code)) {
            fillSla(report, user, me, filter);
        } else if ("category".equals(code)) {
            fillGroup(report, user, me, filter, "category", "name", "Category");
        } else if ("dept".equals(code)) {
            fillGroup(report, user, me, filter, "department", "name", "Department");
        } else if ("implementor".equals(code)) {
            fillImplementor(report, user, me, filter);
        } else if ("restime".equals(code)) {
            fillResTime(report, user, me, filter);
        } else if ("aging".equals(code)) {
            fillAging(report, user, me, filter);
        } else if ("pendingapproval".equals(code)) {
            ReportFilter copy = copy(filter);
            copy.setStatus("Pending Approval");
            fillTicketList(report, user, me, copy, extraNone(), page, size);
        } else if ("security".equals(code)) {
            fillTicketList(report, user, me, filter, extraSecurity(), page, size);
        } else if ("asset".equals(code)) {
            fillTicketList(report, user, me, filter, extraHardware(), page, size);
        } else {
            fillTicketList(report, user, me, filter, extraNone(), page, size);
        }
        return report;
    }

    public void assertReportAllowed(ItsmUserPrincipal user, String code) {
        if (user == null || !user.has("REPORT_VIEW")) {
            throw new AccessDeniedException("REPORT_VIEW required.");
        }
        if ("security".equals(code)
                && !user.has("TICKET_VIEW_SECURITY")
                && !user.has("TICKET_VIEW_QUEUE_ALL")
                && !user.has("ADMIN_SYSTEM")) {
            throw new AccessDeniedException("Security report needs TICKET_VIEW_SECURITY or queue-wide access.");
        }
    }

    long countTickets(ItsmUserPrincipal user, Employee me, ReportFilter filter, ExtraPredicate extra) {
        CriteriaBuilder cb = entityManager.getCriteriaBuilder();
        CriteriaQuery<Long> cq = cb.createQuery(Long.class);
        Root<Ticket> root = cq.from(Ticket.class);
        cq.select(cb.count(root.get("ticketId")));
        cq.where(andAll(cb, scopeAndFilters(cb, root, user, me, filter), extra.apply(cb, root)));
        Long n = entityManager.createQuery(cq).getSingleResult();
        return n == null ? 0L : n.longValue();
    }

    private long countSla(ItsmUserPrincipal user, Employee me, ReportFilter filter, String state) {
        CriteriaBuilder cb = entityManager.getCriteriaBuilder();
        CriteriaQuery<Long> cq = cb.createQuery(Long.class);
        Root<TicketSla> sla = cq.from(TicketSla.class);
        Join<TicketSla, Ticket> ticket = sla.join("ticket");
        cq.select(cb.count(sla.get("ticketSlaId")));
        List<Predicate> p = new ArrayList<Predicate>();
        p.add(cb.equal(sla.get("stateCode"), state));
        p.addAll(scopeAndFilters(cb, ticket, user, me, filter));
        cq.where(cb.and(p.toArray(new Predicate[0])));
        Long n = entityManager.createQuery(cq).getSingleResult();
        return n == null ? 0L : n.longValue();
    }

    private long countRequester(ItsmUserPrincipal user, Employee me) {
        if (me == null) {
            return 0L;
        }
        CriteriaBuilder cb = entityManager.getCriteriaBuilder();
        CriteriaQuery<Long> cq = cb.createQuery(Long.class);
        Root<Ticket> root = cq.from(Ticket.class);
        cq.select(cb.count(root.get("ticketId")));
        cq.where(cb.equal(root.get("requester"), me));
        Long n = entityManager.createQuery(cq).getSingleResult();
        return n == null ? 0L : n.longValue();
    }

    private long countAssigned(ItsmUserPrincipal user, Employee me) {
        if (me == null) {
            return 0L;
        }
        CriteriaBuilder cb = entityManager.getCriteriaBuilder();
        CriteriaQuery<Long> cq = cb.createQuery(Long.class);
        Root<Ticket> root = cq.from(Ticket.class);
        cq.select(cb.count(root.get("ticketId")));
        cq.where(cb.equal(root.get("assignedImplementor"), me));
        Long n = entityManager.createQuery(cq).getSingleResult();
        return n == null ? 0L : n.longValue();
    }

    private Double avgResolutionHours(ItsmUserPrincipal user, Employee me, ReportFilter filter) {
        CriteriaBuilder cb = entityManager.getCriteriaBuilder();
        CriteriaQuery<Object[]> cq = cb.createQuery(Object[].class);
        Root<TicketSla> sla = cq.from(TicketSla.class);
        Join<TicketSla, Ticket> ticket = sla.join("ticket");
        cq.multiselect(sla.get("slaStartUtc"), sla.get("resolvedUtc"));
        List<Predicate> p = new ArrayList<Predicate>();
        p.add(cb.isNotNull(sla.get("resolvedUtc")));
        p.addAll(scopeAndFilters(cb, ticket, user, me, filter));
        cq.where(cb.and(p.toArray(new Predicate[0])));
        List<Object[]> rows = entityManager.createQuery(cq).getResultList();
        if (rows.isEmpty()) {
            return null;
        }
        double sum = 0;
        int n = 0;
        for (Object[] row : rows) {
            Instant start = (Instant) row[0];
            Instant end = (Instant) row[1];
            if (start != null && end != null && !end.isBefore(start)) {
                sum += Duration.between(start, end).toMillis() / 3600000.0;
                n++;
            }
        }
        if (n == 0) {
            return null;
        }
        return sum / n;
    }

    private ChartPayload statusChart(ItsmUserPrincipal user, Employee me) {
        Map<String, Long> counts = groupCount(user, me, new ReportFilter(), "statusCode", null, extraNone());
        List<String> order = Arrays.asList("Draft", "Pending Approval", "Approved", "Assigned",
                "In Progress", "On Hold", "Resolved", "Closed", "Rejected");
        List<String> labels = new ArrayList<String>();
        List<Number> data = new ArrayList<Number>();
        List<String> colors = Arrays.asList(COLOR_INFO, COLOR_WARN, COLOR_TEAL, COLOR_PURPLE,
                COLOR_NAVY, COLOR_SLATE, COLOR_SUCCESS, COLOR_SLATE, COLOR_DANGER);
        long total = 0;
        for (int i = 0; i < order.size(); i++) {
            String st = order.get(i);
            Long n = counts.get(st);
            if (n != null && n.longValue() > 0) {
                labels.add("Draft".equals(st) ? "New" : st);
                data.add(n);
                total += n.longValue();
            }
        }
        ChartPayload chart = new ChartPayload();
        chart.setType("doughnut");
        chart.setLabels(labels);
        chart.getSeries().add(new ChartSeries("Tickets", null, data));
        chart.setEmpty(total == 0);
        chart.getSeries().get(0).setColor(joinColors(colors, labels, order));
        return chart;
    }

    private String joinColors(List<String> colors, List<String> labels, List<String> order) {
        List<String> used = new ArrayList<String>();
        for (String label : labels) {
            String key = "New".equals(label) ? "Draft" : label;
            int idx = order.indexOf(key);
            used.add(idx >= 0 && idx < colors.size() ? colors.get(idx) : COLOR_SLATE);
        }
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < used.size(); i++) {
            if (i > 0) {
                sb.append(',');
            }
            sb.append(used.get(i));
        }
        return sb.toString();
    }

    private ChartPayload priorityChart(ItsmUserPrincipal user, Employee me) {
        Map<String, Long> counts = groupCount(user, me, new ReportFilter(), "priorityCode", null, extraNone());
        List<String> order = Arrays.asList("Critical", "High", "Medium", "Low");
        List<String> colors = Arrays.asList(COLOR_DANGER, COLOR_WARN, COLOR_INFO, COLOR_SLATE);
        return orderedBar(counts, order, colors);
    }

    private ChartPayload orderedBar(Map<String, Long> counts, List<String> order, List<String> colors) {
        ChartPayload chart = new ChartPayload();
        chart.setType("bar");
        List<Number> data = new ArrayList<Number>();
        List<String> labels = new ArrayList<String>();
        long total = 0;
        for (String k : order) {
            Long n = counts.containsKey(k) ? counts.get(k) : Long.valueOf(0);
            labels.add(k);
            data.add(n);
            total += n.longValue();
        }
        chart.setLabels(labels);
        ChartSeries s = new ChartSeries("Tickets", COLOR_NAVY, data);
        s.setColor(joinColors(colors, labels, order));
        chart.getSeries().add(s);
        chart.setEmpty(total == 0);
        return chart;
    }

    private ChartPayload groupBar(ItsmUserPrincipal user, Employee me, ReportFilter filter,
                                  String assoc, String field, String color, String type) {
        return groupBar(user, me, filter, assoc, field, color, type, extraNone());
    }

    private ChartPayload groupBar(ItsmUserPrincipal user, Employee me, ReportFilter filter,
                                  String assoc, String field, String color, String type, ExtraPredicate extra) {
        Map<String, Long> counts = groupCount(user, me, filter, assoc, field, extra);
        ChartPayload chart = new ChartPayload();
        chart.setType(type);
        long total = 0;
        List<Number> data = new ArrayList<Number>();
        for (Map.Entry<String, Long> e : counts.entrySet()) {
            chart.getLabels().add(e.getKey());
            data.add(e.getValue());
            total += e.getValue().longValue();
        }
        chart.getSeries().add(new ChartSeries("Tickets", color, data));
        chart.setEmpty(total == 0);
        return chart;
    }

    private ChartPayload monthlyTrendChart(ItsmUserPrincipal user, Employee me, ReportFilter filter,
                                           int months, YearMonth end) {
        List<String> labels = new ArrayList<String>();
        List<Number> opened = new ArrayList<Number>();
        List<Number> resolved = new ArrayList<Number>();
        List<Number> closed = new ArrayList<Number>();
        long total = 0;
        for (int i = months - 1; i >= 0; i--) {
            YearMonth ym = end.minusMonths(i);
            Instant from = ym.atDay(1).atStartOfDay(IST).toInstant();
            Instant to = ym.plusMonths(1).atDay(1).atStartOfDay(IST).toInstant();
            long o = countCreatedBetween(user, me, filter, from, to);
            long r = countResolvedBetween(user, me, filter, from, to);
            long c = countClosedBetween(user, me, filter, from, to);
            labels.add(ym.atDay(1).format(MONTH));
            opened.add(Long.valueOf(o));
            resolved.add(Long.valueOf(r));
            closed.add(Long.valueOf(c));
            total += o + r + c;
        }
        ChartPayload chart = new ChartPayload();
        chart.setType("line");
        chart.setLabels(labels);
        chart.getSeries().add(new ChartSeries("Opened", COLOR_NAVY, opened));
        chart.getSeries().add(new ChartSeries("Resolved", COLOR_TEAL, resolved));
        chart.getSeries().add(new ChartSeries("Closed", COLOR_SLATE, closed));
        chart.setEmpty(total == 0);
        return chart;
    }

    private Map<String, Long> groupCount(ItsmUserPrincipal user, Employee me, ReportFilter filter,
                                         String path, String nested, ExtraPredicate extra) {
        CriteriaBuilder cb = entityManager.getCriteriaBuilder();
        CriteriaQuery<Object[]> cq = cb.createQuery(Object[].class);
        Root<Ticket> root = cq.from(Ticket.class);
        javax.persistence.criteria.Expression<String> key;
        if (nested == null) {
            key = root.get(path);
        } else {
            Join<Object, Object> j = root.join(path, JoinType.LEFT);
            key = j.get(nested);
        }
        cq.multiselect(key, cb.count(root.get("ticketId")));
        cq.where(andAll(cb, scopeAndFilters(cb, root, user, me, filter), extra.apply(cb, root)));
        cq.groupBy(key);
        List<Object[]> rows = entityManager.createQuery(cq).getResultList();
        Map<String, Long> map = new LinkedHashMap<String, Long>();
        for (Object[] row : rows) {
            String label = row[0] == null ? "(None)" : String.valueOf(row[0]);
            Number n = (Number) row[1];
            map.put(label, Long.valueOf(n.longValue()));
        }
        return map;
    }

    private void fillMonthly(NamedReport report, ItsmUserPrincipal user, Employee me,
                             ReportFilter filter, boolean includeClosed) {
        report.setColumns(includeClosed
                ? Arrays.asList("Month", "Opened", "Resolved", "Closed")
                : Arrays.asList("Month", "Opened"));
        YearMonth end = YearMonth.from(filter.getTo() != null ? filter.getTo() : LocalDate.now(IST));
        YearMonth start = YearMonth.from(filter.getFrom() != null ? filter.getFrom() : end.minusMonths(11));
        if (start.isAfter(end)) {
            YearMonth tmp = start;
            start = end;
            end = tmp;
        }
        int months = (int) (end.getYear() * 12 + end.getMonthValue() - (start.getYear() * 12 + start.getMonthValue()) + 1);
        if (months > 24) {
            start = end.minusMonths(23);
            months = 24;
        }
        ChartPayload chart = monthlyTrendChart(user, me, filter, months, end);
        report.setChart(includeClosed ? chart : volumeOnly(chart));
        List<ReportRow> rows = new ArrayList<ReportRow>();
        for (int i = 0; i < months; i++) {
            YearMonth ym = start.plusMonths(i);
            Instant from = ym.atDay(1).atStartOfDay(IST).toInstant();
            Instant to = ym.plusMonths(1).atDay(1).atStartOfDay(IST).toInstant();
            long o = countCreatedBetween(user, me, filter, from, to);
            if (includeClosed) {
                rows.add(new ReportRow(ym.atDay(1).format(MONTH),
                        String.valueOf(o),
                        String.valueOf(countResolvedBetween(user, me, filter, from, to)),
                        String.valueOf(countClosedBetween(user, me, filter, from, to))));
            } else {
                rows.add(new ReportRow(ym.atDay(1).format(MONTH), String.valueOf(o)));
            }
        }
        report.setRows(rows);
        report.setTotalRows(rows.size());
        report.setPage(0);
        report.setSize(Math.max(rows.size(), 1));
    }

    private ChartPayload volumeOnly(ChartPayload full) {
        ChartPayload p = new ChartPayload();
        p.setType("bar");
        p.setLabels(full.getLabels());
        if (!full.getSeries().isEmpty()) {
            p.getSeries().add(full.getSeries().get(0));
        }
        p.setEmpty(full.isEmpty());
        return p;
    }

    private void fillSla(NamedReport report, ItsmUserPrincipal user, Employee me, ReportFilter filter) {
        long within = countSla(user, me, filter, "WITHIN");
        long near = countSla(user, me, filter, "NEAR");
        long breach = countSla(user, me, filter, "BREACHED");
        report.setColumns(Arrays.asList("SLA state", "Tickets"));
        report.getRows().add(new ReportRow("WITHIN", String.valueOf(within)));
        report.getRows().add(new ReportRow("NEAR", String.valueOf(near)));
        report.getRows().add(new ReportRow("BREACHED", String.valueOf(breach)));
        report.setTotalRows(3);
        ChartPayload chart = new ChartPayload();
        chart.setType("doughnut");
        chart.setLabels(Arrays.asList("WITHIN", "NEAR", "BREACHED"));
        List<Number> data = Arrays.<Number>asList(Long.valueOf(within), Long.valueOf(near), Long.valueOf(breach));
        ChartSeries s = new ChartSeries("SLA", COLOR_SUCCESS + "," + COLOR_WARN + "," + COLOR_DANGER, data);
        chart.getSeries().add(s);
        chart.setEmpty(within + near + breach == 0);
        report.setChart(chart);
    }

    private void fillGroup(NamedReport report, ItsmUserPrincipal user, Employee me, ReportFilter filter,
                           String assoc, String field, String heading) {
        Map<String, Long> counts = groupCount(user, me, filter, assoc, field, extraNone());
        report.setColumns(Arrays.asList(heading, "Tickets"));
        long total = 0;
        for (Map.Entry<String, Long> e : counts.entrySet()) {
            report.getRows().add(new ReportRow(e.getKey(), String.valueOf(e.getValue())));
            total += e.getValue().longValue();
        }
        report.setTotalRows(report.getRows().size());
        report.setChart(groupBar(user, me, filter, assoc, field, COLOR_NAVY, "bar", extraNone()));
        if (total == 0) {
            report.setNote("No tickets in this window.");
        }
    }

    private void fillImplementor(NamedReport report, ItsmUserPrincipal user, Employee me, ReportFilter filter) {
        CriteriaBuilder cb = entityManager.getCriteriaBuilder();
        CriteriaQuery<Object[]> cq = cb.createQuery(Object[].class);
        Root<Ticket> root = cq.from(Ticket.class);
        Join<Object, Object> impl = root.join("assignedImplementor", JoinType.LEFT);
        cq.multiselect(impl.get("displayName"), cb.count(root.get("ticketId")));
        cq.where(andAll(cb, scopeAndFilters(cb, root, user, me, filter)));
        cq.groupBy(impl.get("displayName"));
        List<Object[]> grouped = entityManager.createQuery(cq).getResultList();
        report.setColumns(Arrays.asList("Implementor", "Assigned", "Open", "Resolved", "Avg resolution (h)"));
        ChartPayload chart = new ChartPayload();
        chart.setType("bar");
        List<Number> assigned = new ArrayList<Number>();
        List<Number> resolved = new ArrayList<Number>();
        long chartTotal = 0;
        for (Object[] row : grouped) {
            String name = row[0] == null ? "(Unassigned)" : String.valueOf(row[0]);
            long all = ((Number) row[1]).longValue();
            ExtraPredicate match = implementorName(name);
            long open = countTickets(user, me, filter, andExtra(match, statusNotIn(OPEN_STATUSES_EXCLUDE)));
            long res = countTickets(user, me, filter, andExtra(match, statusIn(Arrays.asList("Resolved", "Closed"))));
            Double hrs = avgResolutionHoursImplementor(user, me, filter, name);
            report.getRows().add(new ReportRow(name, String.valueOf(all), String.valueOf(open),
                    String.valueOf(res), hrs == null ? "n/a" : formatHoursNumber(hrs)));
            chart.getLabels().add(name);
            assigned.add(Long.valueOf(all));
            resolved.add(Long.valueOf(res));
            chartTotal += all;
        }
        chart.getSeries().add(new ChartSeries("Assigned", COLOR_NAVY, assigned));
        chart.getSeries().add(new ChartSeries("Resolved", COLOR_TEAL, resolved));
        chart.setEmpty(chartTotal == 0);
        report.setChart(chart);
        report.setTotalRows(report.getRows().size());
    }

    private Double avgResolutionHoursImplementor(ItsmUserPrincipal user, Employee me, ReportFilter filter, String name) {
        CriteriaBuilder cb = entityManager.getCriteriaBuilder();
        CriteriaQuery<Object[]> cq = cb.createQuery(Object[].class);
        Root<TicketSla> sla = cq.from(TicketSla.class);
        Join<TicketSla, Ticket> ticket = sla.join("ticket");
        cq.multiselect(sla.get("slaStartUtc"), sla.get("resolvedUtc"));
        List<Predicate> p = new ArrayList<Predicate>();
        p.add(cb.isNotNull(sla.get("resolvedUtc")));
        p.addAll(scopeAndFilters(cb, ticket, user, me, filter));
        p.add(implementorName(name).apply(cb, ticket));
        cq.where(cb.and(p.toArray(new Predicate[0])));
        List<Object[]> rows = entityManager.createQuery(cq).getResultList();
        if (rows.isEmpty()) {
            return null;
        }
        double sum = 0;
        int n = 0;
        for (Object[] row : rows) {
            Instant start = (Instant) row[0];
            Instant end = (Instant) row[1];
            if (start != null && end != null) {
                sum += Duration.between(start, end).toMillis() / 3600000.0;
                n++;
            }
        }
        return n == 0 ? null : sum / n;
    }

    private void fillResTime(NamedReport report, ItsmUserPrincipal user, Employee me, ReportFilter filter) {
        CriteriaBuilder cb = entityManager.getCriteriaBuilder();
        CriteriaQuery<Object[]> cq = cb.createQuery(Object[].class);
        Root<TicketSla> sla = cq.from(TicketSla.class);
        Join<TicketSla, Ticket> ticket = sla.join("ticket");
        cq.multiselect(ticket.get("priorityCode"), sla.get("slaStartUtc"), sla.get("resolvedUtc"));
        List<Predicate> p = new ArrayList<Predicate>();
        p.add(cb.isNotNull(sla.get("resolvedUtc")));
        p.addAll(scopeAndFilters(cb, ticket, user, me, filter));
        cq.where(cb.and(p.toArray(new Predicate[0])));
        List<Object[]> rows = entityManager.createQuery(cq).getResultList();
        Map<String, double[]> acc = new LinkedHashMap<String, double[]>();
        for (String pr : Arrays.asList("Critical", "High", "Medium", "Low")) {
            acc.put(pr, new double[]{0, 0});
        }
        for (Object[] row : rows) {
            String pr = row[0] == null ? "Medium" : String.valueOf(row[0]);
            Instant start = (Instant) row[1];
            Instant end = (Instant) row[2];
            if (start == null || end == null) {
                continue;
            }
            double[] a = acc.get(pr);
            if (a == null) {
                a = new double[]{0, 0};
                acc.put(pr, a);
            }
            a[0] += Duration.between(start, end).toMillis() / 3600000.0;
            a[1] += 1;
        }
        report.setColumns(Arrays.asList("Priority", "Resolved tickets", "Avg hours"));
        ChartPayload chart = new ChartPayload();
        chart.setType("bar");
        List<Number> data = new ArrayList<Number>();
        long n = 0;
        for (Map.Entry<String, double[]> e : acc.entrySet()) {
            long count = (long) e.getValue()[1];
            String avg = count == 0 ? "n/a" : formatHoursNumber(e.getValue()[0] / e.getValue()[1]);
            report.getRows().add(new ReportRow(e.getKey(), String.valueOf(count), avg));
            data.add(count == 0 ? Double.valueOf(0) : Double.valueOf(e.getValue()[0] / e.getValue()[1]));
            chart.getLabels().add(e.getKey());
            n += count;
        }
        chart.getSeries().add(new ChartSeries("Hours", COLOR_NAVY, data));
        chart.setEmpty(n == 0);
        report.setChart(chart);
        report.setTotalRows(report.getRows().size());
    }

    private void fillAging(NamedReport report, ItsmUserPrincipal user, Employee me, ReportFilter filter) {
        CriteriaBuilder cb = entityManager.getCriteriaBuilder();
        CriteriaQuery<Instant> cq = cb.createQuery(Instant.class);
        Root<Ticket> root = cq.from(Ticket.class);
        cq.select(root.get("createdAtUtc"));
        cq.where(andAll(cb, scopeAndFilters(cb, root, user, me, filter),
                statusNotIn(OPEN_STATUSES_EXCLUDE).apply(cb, root)));
        List<Instant> created = entityManager.createQuery(cq).getResultList();
        long b1 = 0, b2 = 0, b3 = 0, b4 = 0, b5 = 0;
        Instant now = Instant.now();
        for (Instant c : created) {
            if (c == null) {
                continue;
            }
            long days = Duration.between(c, now).toDays();
            if (days <= 1) {
                b1++;
            } else if (days <= 3) {
                b2++;
            } else if (days <= 7) {
                b3++;
            } else if (days <= 14) {
                b4++;
            } else {
                b5++;
            }
        }
        report.setColumns(Arrays.asList("Age", "Open tickets"));
        report.getRows().add(new ReportRow("0–1 days", String.valueOf(b1)));
        report.getRows().add(new ReportRow("2–3 days", String.valueOf(b2)));
        report.getRows().add(new ReportRow("4–7 days", String.valueOf(b3)));
        report.getRows().add(new ReportRow("8–14 days", String.valueOf(b4)));
        report.getRows().add(new ReportRow("15+ days", String.valueOf(b5)));
        report.setTotalRows(5);
        ChartPayload chart = new ChartPayload();
        chart.setType("bar");
        chart.setLabels(Arrays.asList("0–1", "2–3", "4–7", "8–14", "15+"));
        List<Number> data = Arrays.<Number>asList(Long.valueOf(b1), Long.valueOf(b2), Long.valueOf(b3),
                Long.valueOf(b4), Long.valueOf(b5));
        chart.getSeries().add(new ChartSeries("Open", COLOR_GOLD, data));
        chart.setEmpty(created.isEmpty());
        report.setChart(chart);
    }

    private void fillTicketList(NamedReport report, ItsmUserPrincipal user, Employee me, ReportFilter filter,
                                ExtraPredicate extra, int page, int size) {
        CriteriaBuilder cb = entityManager.getCriteriaBuilder();
        CriteriaQuery<Long> countQ = cb.createQuery(Long.class);
        Root<Ticket> countRoot = countQ.from(Ticket.class);
        countQ.select(cb.count(countRoot.get("ticketId")));
        countQ.where(andAll(cb, scopeAndFilters(cb, countRoot, user, me, filter), extra.apply(cb, countRoot)));
        Long total = entityManager.createQuery(countQ).getSingleResult();
        long totalN = total == null ? 0L : total.longValue();

        CriteriaQuery<Object[]> cq = cb.createQuery(Object[].class);
        Root<Ticket> root = cq.from(Ticket.class);
        Join<Object, Object> type = root.join("ticketType", JoinType.LEFT);
        Join<Object, Object> dept = root.join("department", JoinType.LEFT);
        Join<Object, Object> req = root.join("requester", JoinType.LEFT);
        cq.multiselect(
                root.get("publicNumber"),
                root.get("subject"),
                type.get("name"),
                root.get("statusCode"),
                root.get("priorityCode"),
                dept.get("name"),
                req.get("displayName"),
                root.get("createdAtUtc"));
        cq.where(andAll(cb, scopeAndFilters(cb, root, user, me, filter), extra.apply(cb, root)));
        cq.orderBy(cb.desc(root.get("createdAtUtc")));
        int safeSize = Math.min(Math.max(size, 1), EXPORT_CAP);
        TypedQuery<Object[]> q = entityManager.createQuery(cq);
        q.setFirstResult(page * safeSize);
        q.setMaxResults(safeSize);
        List<Object[]> rows = q.getResultList();
        report.setColumns(Arrays.asList("Number", "Subject", "Type", "Status", "Priority", "Department",
                "Requester", "Created (IST)"));
        for (Object[] row : rows) {
            Instant created = (Instant) row[7];
            String when = created == null ? "" : DAY.format(created.atZone(IST));
            report.getRows().add(new ReportRow(
                    str(row[0]), str(row[1]), str(row[2]), str(row[3]),
                    str(row[4]), str(row[5]), str(row[6]), when));
        }
        report.setTotalRows(totalN);
        report.setPage(page);
        report.setSize(safeSize);
        report.setChart(groupBar(user, me, filter, "statusCode", null, COLOR_NAVY, "bar", extra));
    }

    private long countCreatedBetween(ItsmUserPrincipal user, Employee me, ReportFilter base,
                                     Instant from, Instant to) {
        CriteriaBuilder cb = entityManager.getCriteriaBuilder();
        CriteriaQuery<Long> cq = cb.createQuery(Long.class);
        Root<Ticket> root = cq.from(Ticket.class);
        cq.select(cb.count(root.get("ticketId")));
        List<Predicate> p = scopeAndFiltersIgnoringDates(cb, root, user, me, base);
        p.add(cb.greaterThanOrEqualTo(root.<Instant>get("createdAtUtc"), from));
        p.add(cb.lessThan(root.<Instant>get("createdAtUtc"), to));
        cq.where(cb.and(p.toArray(new Predicate[0])));
        Long n = entityManager.createQuery(cq).getSingleResult();
        return n == null ? 0L : n.longValue();
    }

    private long countResolvedBetween(ItsmUserPrincipal user, Employee me, ReportFilter base,
                                      Instant from, Instant to) {
        CriteriaBuilder cb = entityManager.getCriteriaBuilder();
        CriteriaQuery<Long> cq = cb.createQuery(Long.class);
        Root<TicketSla> sla = cq.from(TicketSla.class);
        Join<TicketSla, Ticket> ticket = sla.join("ticket");
        cq.select(cb.count(sla.get("ticketSlaId")));
        List<Predicate> p = scopeAndFiltersIgnoringDates(cb, ticket, user, me, base);
        p.add(cb.isNotNull(sla.get("resolvedUtc")));
        p.add(cb.greaterThanOrEqualTo(sla.<Instant>get("resolvedUtc"), from));
        p.add(cb.lessThan(sla.<Instant>get("resolvedUtc"), to));
        cq.where(cb.and(p.toArray(new Predicate[0])));
        Long n = entityManager.createQuery(cq).getSingleResult();
        return n == null ? 0L : n.longValue();
    }

    private long countClosedBetween(ItsmUserPrincipal user, Employee me, ReportFilter base,
                                    Instant from, Instant to) {
        CriteriaBuilder cb = entityManager.getCriteriaBuilder();
        CriteriaQuery<Long> cq = cb.createQuery(Long.class);
        Root<Ticket> root = cq.from(Ticket.class);
        cq.select(cb.count(root.get("ticketId")));
        List<Predicate> p = scopeAndFiltersIgnoringDates(cb, root, user, me, base);
        p.add(cb.equal(root.get("statusCode"), "Closed"));
        p.add(cb.greaterThanOrEqualTo(root.<Instant>get("updatedAtUtc"), from));
        p.add(cb.lessThan(root.<Instant>get("updatedAtUtc"), to));
        cq.where(cb.and(p.toArray(new Predicate[0])));
        Long n = entityManager.createQuery(cq).getSingleResult();
        return n == null ? 0L : n.longValue();
    }

    private List<Predicate> scopeAndFilters(CriteriaBuilder cb, From<?, Ticket> ticket,
                                            ItsmUserPrincipal user, Employee me, ReportFilter filter) {
        List<Predicate> p = new ArrayList<Predicate>();
        p.add(scope(cb, ticket, user, me));
        if (filter == null) {
            return p;
        }
        Instant[] window = window(filter);
        if (window != null) {
            p.add(cb.greaterThanOrEqualTo(ticket.<Instant>get("createdAtUtc"), window[0]));
            p.add(cb.lessThan(ticket.<Instant>get("createdAtUtc"), window[1]));
        }
        addCommonFilters(cb, ticket, filter, p);
        return p;
    }

    private List<Predicate> scopeAndFiltersIgnoringDates(CriteriaBuilder cb, From<?, Ticket> ticket,
                                                         ItsmUserPrincipal user, Employee me, ReportFilter filter) {
        List<Predicate> p = new ArrayList<Predicate>();
        p.add(scope(cb, ticket, user, me));
        addCommonFilters(cb, ticket, filter, p);
        return p;
    }

    private void addCommonFilters(CriteriaBuilder cb, From<?, Ticket> ticket, ReportFilter filter, List<Predicate> p) {
        if (filter == null) {
            return;
        }
        if (StringUtils.hasText(filter.getStatus())) {
            p.add(cb.equal(ticket.get("statusCode"), filter.getStatus()));
        }
        if (StringUtils.hasText(filter.getPriority())) {
            p.add(cb.equal(ticket.get("priorityCode"), filter.getPriority()));
        }
        if (filter.getTypeId() != null) {
            p.add(cb.equal(ticket.get("ticketType").get("ticketTypeId"), filter.getTypeId()));
        }
        if (filter.getDepartmentId() != null) {
            p.add(cb.equal(ticket.get("department").get("departmentId"), filter.getDepartmentId()));
        }
        if (filter.getCategoryId() != null) {
            p.add(cb.equal(ticket.get("category").get("categoryId"), filter.getCategoryId()));
        }
    }

    Predicate scope(CriteriaBuilder cb, From<?, Ticket> ticket, ItsmUserPrincipal user, Employee me) {
        if (user == null) {
            return cb.disjunction();
        }
        if (user.has("TICKET_VIEW_QUEUE_ALL") || user.has("ADMIN_SYSTEM")) {
            return cb.conjunction();
        }
        if (user.has("TICKET_VIEW_DEPARTMENT")) {
            if (me != null && me.getDepartment() != null) {
                return cb.equal(ticket.get("department"), me.getDepartment());
            }
            return cb.disjunction();
        }
        if (user.has("TICKET_VIEW_TEAM")) {
            if (me == null) {
                return cb.disjunction();
            }
            return cb.or(
                    cb.equal(ticket.get("requester").get("employeeId"), me.getEmployeeId()),
                    cb.equal(ticket.get("requester").get("manager"), me));
        }
        if (user.has("TICKET_VIEW_SECURITY")) {
            Predicate sec = cb.or(
                    cb.equal(ticket.get("confidentialityCode"), "Highly Confidential"),
                    cb.equal(ticket.get("ticketType").get("name"), "Security Incident"),
                    cb.equal(ticket.get("category").get("name"), "Cyber Security"));
            Predicate own = ownOrAssigned(cb, ticket, me);
            return cb.or(sec, own);
        }
        return ownOrAssigned(cb, ticket, me);
    }

    private Predicate ownOrAssigned(CriteriaBuilder cb, From<?, Ticket> ticket, Employee me) {
        if (me == null) {
            return cb.disjunction();
        }
        return cb.or(cb.equal(ticket.get("requester"), me), cb.equal(ticket.get("assignedImplementor"), me));
    }

    private Instant[] window(ReportFilter filter) {
        if (filter == null || (filter.getFrom() == null && filter.getTo() == null)) {
            return null;
        }
        LocalDate from = filter.getFrom() != null ? filter.getFrom() : LocalDate.of(2000, 1, 1);
        LocalDate to = filter.getTo() != null ? filter.getTo() : LocalDate.now(IST);
        Instant start = from.atStartOfDay(IST).toInstant();
        Instant end = to.plusDays(1).atStartOfDay(IST).toInstant();
        return new Instant[]{start, end};
    }

    private String dashboardTitle(ItsmUserPrincipal user) {
        List<String> roles = user.getRoleCodes();
        if (roles.contains("CISO") && !roles.contains("SYSTEM_ADMINISTRATOR")) {
            return "Security & Risk Dashboard";
        }
        if (roles.contains("IT_SERVICE_DESK")) {
            return "Service Desk Dashboard";
        }
        if (roles.contains("IT_IMPLEMENTOR") && !roles.contains("IT_SERVICE_DESK")
                && !roles.contains("SYSTEM_ADMINISTRATOR")) {
            return "Implementor Dashboard";
        }
        return "Executive Dashboard";
    }

    private String scopeLabel(ItsmUserPrincipal user, Employee me) {
        if (user.has("TICKET_VIEW_QUEUE_ALL") || user.has("ADMIN_SYSTEM")) {
            return "Queue-wide (all tickets)";
        }
        if (user.has("TICKET_VIEW_DEPARTMENT")) {
            String dept = me != null && me.getDepartment() != null ? me.getDepartment().getName() : "your department";
            return "Department: " + dept;
        }
        if (user.has("TICKET_VIEW_TEAM")) {
            return "Team (direct reports and your tickets)";
        }
        if (user.has("TICKET_VIEW_SECURITY")) {
            return "Security tickets plus your own";
        }
        return "Your tickets and assignments";
    }

    private KpiCard kpi(String label, String value, Trend trend, String icon, String color) {
        return new KpiCard(label, value, trend.text, trend.css, trend.ctx, icon, color);
    }

    private Trend trendCounts(long current, long prior, boolean higherIsBad) {
        if (prior == 0L) {
            return new Trend("n/a", "na", "vs prior 30 days");
        }
        double pct = ((current - prior) * 100.0) / prior;
        String arrow = current >= prior ? "up" : "down";
        boolean bad = (current > prior && higherIsBad) || (current < prior && !higherIsBad);
        String css = arrow + (bad ? "-bad" : "-good");
        String text = String.format(Locale.ENGLISH, "%.1f%%", Math.abs(pct));
        return new Trend(text, css, "vs prior 30 days");
    }

    private Trend trendHours(Double current, Double prior) {
        if (current == null || prior == null || prior.doubleValue() == 0) {
            return new Trend("n/a", "na", "vs prior 30 days");
        }
        boolean improved = current.doubleValue() < prior.doubleValue();
        double delta = Math.abs(current.doubleValue() - prior.doubleValue());
        return new Trend(formatHoursNumber(delta) + "h", improved ? "down-good" : "up-bad", "vs prior 30 days");
    }

    private static String formatHours(Double hours) {
        if (hours == null) {
            return "n/a";
        }
        return formatHoursNumber(hours) + "h";
    }

    private static String formatHoursNumber(double hours) {
        if (hours < 1) {
            long mins = Math.round(hours * 60);
            return mins + "m";
        }
        return BigDecimal.valueOf(hours).setScale(1, RoundingMode.HALF_UP).toPlainString();
    }

    private static String str(Object o) {
        return o == null ? "" : String.valueOf(o);
    }

    private static ReportFilter range(Instant from, Instant to) {
        ReportFilter f = new ReportFilter();
        f.setFrom(from.atZone(IST).toLocalDate());
        f.setTo(to.atZone(IST).toLocalDate().minusDays(1));
        return f;
    }

    private static ReportFilter copy(ReportFilter src) {
        ReportFilter f = new ReportFilter();
        if (src == null) {
            return f;
        }
        f.setFrom(src.getFrom());
        f.setTo(src.getTo());
        f.setTypeId(src.getTypeId());
        f.setStatus(src.getStatus());
        f.setDepartmentId(src.getDepartmentId());
        f.setCategoryId(src.getCategoryId());
        f.setPriority(src.getPriority());
        return f;
    }

    private static Predicate andAll(CriteriaBuilder cb, List<Predicate> list, Predicate... extra) {
        List<Predicate> all = new ArrayList<Predicate>(list);
        if (extra != null) {
            for (Predicate p : extra) {
                if (p != null) {
                    all.add(p);
                }
            }
        }
        if (all.isEmpty()) {
            return cb.conjunction();
        }
        return cb.and(all.toArray(new Predicate[0]));
    }

    private ExtraPredicate extraNone() {
        return new ExtraPredicate() {
            @Override
            public Predicate apply(CriteriaBuilder cb, From<?, Ticket> root) {
                return cb.conjunction();
            }
        };
    }

    private ExtraPredicate extraSecurity() {
        return new ExtraPredicate() {
            @Override
            public Predicate apply(CriteriaBuilder cb, From<?, Ticket> root) {
                return cb.or(
                        cb.equal(root.get("confidentialityCode"), "Highly Confidential"),
                        cb.equal(root.get("ticketType").get("name"), "Security Incident"),
                        cb.equal(root.get("category").get("name"), "Cyber Security"));
            }
        };
    }

    private ExtraPredicate extraHardware() {
        return new ExtraPredicate() {
            @Override
            public Predicate apply(CriteriaBuilder cb, From<?, Ticket> root) {
                return cb.or(cb.equal(root.get("category").get("name"), "Hardware"),
                        cb.equal(root.get("ticketType").get("name"), "Hardware Request"));
            }
        };
    }

    private ExtraPredicate statusEq(final String status) {
        return new ExtraPredicate() {
            @Override
            public Predicate apply(CriteriaBuilder cb, From<?, Ticket> root) {
                return cb.equal(root.get("statusCode"), status);
            }
        };
    }

    ExtraPredicate statusNotIn(final List<String> statuses) {
        return new ExtraPredicate() {
            @Override
            public Predicate apply(CriteriaBuilder cb, From<?, Ticket> root) {
                return cb.not(root.get("statusCode").in(statuses));
            }
        };
    }

    private ExtraPredicate statusIn(final List<String> statuses) {
        return new ExtraPredicate() {
            @Override
            public Predicate apply(CriteriaBuilder cb, From<?, Ticket> root) {
                return root.get("statusCode").in(statuses);
            }
        };
    }

    private ExtraPredicate typeName(final String name) {
        return new ExtraPredicate() {
            @Override
            public Predicate apply(CriteriaBuilder cb, From<?, Ticket> root) {
                return cb.equal(root.get("ticketType").get("name"), name);
            }
        };
    }

    private ExtraPredicate criticalOpen() {
        return new ExtraPredicate() {
            @Override
            public Predicate apply(CriteriaBuilder cb, From<?, Ticket> root) {
                return cb.and(
                        cb.equal(root.get("priorityCode"), "Critical"),
                        cb.equal(root.get("ticketType").get("name"), "Incident"),
                        cb.not(root.get("statusCode").in(OPEN_STATUSES_EXCLUDE)));
            }
        };
    }

    private ExtraPredicate implementorName(final String name) {
        return new ExtraPredicate() {
            @Override
            public Predicate apply(CriteriaBuilder cb, From<?, Ticket> root) {
                if ("(Unassigned)".equals(name)) {
                    return cb.isNull(root.get("assignedImplementor"));
                }
                return cb.equal(root.get("assignedImplementor").get("displayName"), name);
            }
        };
    }

    private ExtraPredicate andExtra(final ExtraPredicate a, final ExtraPredicate b) {
        return new ExtraPredicate() {
            @Override
            public Predicate apply(CriteriaBuilder cb, From<?, Ticket> root) {
                return cb.and(a.apply(cb, root), b.apply(cb, root));
            }
        };
    }

    interface ExtraPredicate {
        Predicate apply(CriteriaBuilder cb, From<?, Ticket> root);
    }

    private static final class Trend {
        final String text;
        final String css;
        final String ctx;

        Trend(String text, String css, String ctx) {
            this.text = text;
            this.css = css;
            this.ctx = ctx;
        }
    }
}
