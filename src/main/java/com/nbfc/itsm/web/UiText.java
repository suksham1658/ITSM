package com.nbfc.itsm.web;

import org.springframework.stereotype.Component;

/**
 * Display helpers used by templates as {@code ${@ui.method(...)}} so every page shows statuses
 * and workflow steps with the same wording and colours:
 * green = moving / in the flow, grey = finished, red = rejected, amber = on hold.
 */
@Component("ui")
public class UiText {

    /** Prefix older tickets stored on reporting-line steps; new steps carry only the person's name. */
    private static final String LEGACY_MANAGER_PREFIX = "Manager approval — ";

    /** Workflow step name as shown to users (drops the legacy "Manager approval — " prefix). */
    public String stageLabel(String label) {
        if (label == null) {
            return "";
        }
        return label.startsWith(LEGACY_MANAGER_PREFIX) ? label.substring(LEGACY_MANAGER_PREFIX.length()) : label;
    }

    /** Badge class for a ticket status. */
    public String ticketStatusClass(String status) {
        if (status == null) {
            return "badge-draft";
        }
        switch (status) {
            case "In Progress":
                return "badge-flow-strong";
            case "Pending Approval":
                return "badge-waiting";
            case "Approved":
            case "Assigned":
                return "badge-flow";
            case "Resolved":
                return "badge-resolved";
            case "On Hold":
                return "badge-pending";
            case "Rejected":
                return "badge-rejected";
            case "Closed":
                return "badge-closed";
            default:
                return "badge-draft";
        }
    }

    /** Badge class for a workflow step status. */
    public String stageStatusClass(String status) {
        if (status == null) {
            return "badge-draft";
        }
        switch (status) {
            case "Current":
                return "badge-waiting";
            case "Completed":
                return "badge-done";
            case "Rejected":
                return "badge-rejected";
            case "Skipped":
                return "badge-skipped";
            default:
                return "badge-draft";
        }
    }

    /**
     * Workflow step status as shown to users: the step the ticket is waiting at reads "Pending" (orange),
     * steps still to come read "Upcoming", finished ones "Completed" (green).
     */
    public String stageStatusText(String status) {
        if ("Current".equals(status)) {
            return "Pending";
        }
        if ("Pending".equals(status)) {
            return "Upcoming";
        }
        return status == null ? "" : status;
    }

    /** SLA state in words: On track / At risk / Breached / Paused / Met / Breached (resolved late). */
    public String slaText(com.nbfc.itsm.domain.TicketSla s) {
        if (s == null) {
            return "";
        }
        if (s.getResolvedUtc() != null) {
            return "BREACHED".equals(s.getStateCode()) ? "Breached (resolved late)" : "Met";
        }
        if (s.isPaused()) {
            return "Paused (on hold)";
        }
        switch (s.getStateCode() == null ? "" : s.getStateCode()) {
            case "NEAR":
                return "At risk";
            case "BREACHED":
                return "Breached";
            default:
                return "On track";
        }
    }

    public String slaClass(com.nbfc.itsm.domain.TicketSla s) {
        if (s == null) {
            return "badge-draft";
        }
        if (s.getResolvedUtc() == null && s.isPaused()) {
            return "badge-onhold";
        }
        switch (s.getStateCode() == null ? "" : s.getStateCode()) {
            case "NEAR":
                return "badge-sla-near";
            case "BREACHED":
                return "badge-sla-breach";
            default:
                return "badge-sla-ok";
        }
    }

    /** First response: Pending / Overdue (none yet, late) / On time / Late. */
    public String responseText(com.nbfc.itsm.domain.TicketSla s) {
        if (s == null) {
            return "";
        }
        if (s.getFirstResponseUtc() == null) {
            return s.isResponseBreached() ? "Overdue" : "Pending";
        }
        return s.isResponseBreached() ? "Late" : "On time";
    }

    /** ISO weekday (1 = Monday) as "Monday". */
    public String dayName(int iso) {
        return com.nbfc.itsm.admin.SlaAdminService.dayName(iso);
    }

    /** Minutes as "2 h 30 min" / "45 min". */
    public String minutesText(int minutes) {
        if (minutes < 60) {
            return minutes + " min";
        }
        int h = minutes / 60;
        int m = minutes % 60;
        return h + " h" + (m == 0 ? "" : " " + m + " min");
    }

    /** Workflow action as shown in the Take action list, e.g. SEND_BACK -&gt; "Send back". */
    public String actionLabel(String code) {
        if (code == null) {
            return "";
        }
        String s = code.trim().replace('_', ' ').toLowerCase(java.util.Locale.ROOT);
        return s.isEmpty() ? s : Character.toUpperCase(s.charAt(0)) + s.substring(1);
    }

    /** Badge class for configuration change requests and workflow definitions/rules. */
    public String configStatusClass(String status) {
        if (status == null) {
            return "badge-draft";
        }
        switch (status) {
            case "PendingApproval":
            case "Active":
                return "badge-flow";
            case "Applied":
            case "Inactive":
            case "Retired":
                return "badge-closed";
            case "Rejected":
                return "badge-rejected";
            default:
                return "badge-draft";
        }
    }

    private static final java.time.format.DateTimeFormatter IST_FORMAT =
            java.time.format.DateTimeFormatter.ofPattern("dd MMM yyyy, HH:mm")
                    .withZone(java.time.ZoneId.of("Asia/Kolkata"));

    /** A stored UTC time as India time, e.g. "27 Sep 2026, 00:54"; "—" when empty. */
    public String dateTime(java.time.Instant utc) {
        return utc == null ? "—" : IST_FORMAT.format(utc);
    }

    /** Human wording for config request statuses ("PendingApproval" reads badly). */
    public String configStatusText(String status) {
        return "PendingApproval".equals(status) ? "Pending approval" : status;
    }
}
