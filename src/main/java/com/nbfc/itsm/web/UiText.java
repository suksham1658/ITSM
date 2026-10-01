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
                return "badge-flow-strong";
            case "Completed":
                return "badge-closed";
            case "Rejected":
                return "badge-rejected";
            case "Skipped":
                return "badge-skipped";
            default:
                return "badge-draft";
        }
    }

    /** Badge class for configuration change requests and workflow definitions/rules. */
    /** Top menu: is the current page ({@code nav}) one of the comma-separated keys of a menu group? */
    public boolean navIn(String nav, String keys) {
        if (nav == null || keys == null) {
            return false;
        }
        for (String k : keys.split(",")) {
            if (nav.equals(k.trim())) {
                return true;
            }
        }
        return false;
    }

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
