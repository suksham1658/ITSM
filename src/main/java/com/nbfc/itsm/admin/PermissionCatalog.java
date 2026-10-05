package com.nbfc.itsm.admin;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Display metadata for permission codes on the role editor: a label, the portal pages the
 * permission unlocks (as enforced by {@code SecurityConfig} / {@code @PreAuthorize}) and the
 * group it is shown under. Mirrors the prototype's page catalogue, but the stored unit is the
 * permission code, not the page. Codes present in the database but not listed here are shown
 * under {@link #OTHER_GROUP} with their database description.
 */
public final class PermissionCatalog {

    public static final String OTHER_GROUP = "Other";

    private static final Map<String, Entry> ENTRIES = new LinkedHashMap<String, Entry>();

    static {
        add("TICKET_CREATE", "General", "Raise requests", "Raise Request");
        add("TICKET_RAISE_IMAC", "General", "Raise IMAC requests (Install/Move/Add/Change)", "Raise Request → IMAC");
        add("TICKET_VIEW_OWN", "General", "View own tickets", "My Tickets");
        add("KB_READ", "General", "Read knowledge base", "Knowledge Base");

        add("TICKET_APPROVE_ASSIGNED_STAGE", "Approvals & Work", "Act on assigned approval stages", "Approvals");
        add("TICKET_VIEW_TEAM", "Approvals & Work", "View team tickets", "Team Requests");
        add("TICKET_VIEW_DEPARTMENT", "Approvals & Work", "View department tickets", "Department Requests");
        add("TICKET_VIEW_SECURITY", "Approvals & Work", "View security tickets", "Security Requests, Risk Dashboard");
        add("TICKET_VIEW_QUEUE_ALL", "Approvals & Work", "View the service desk queue", "Ticket Queue");
        add("TICKET_ASSIGN", "Approvals & Work", "Assign implementors", "Assignment");
        add("SLA_MONITOR", "Approvals & Work", "Monitor SLA", "SLA Monitoring, Escalations");
        add("AD_ACCOUNT_UNLOCK", "Approvals & Work", "Unlock locked Active Directory accounts", "AD Account Unlock");
        add("TICKET_FULFIL", "Approvals & Work", "Fulfil and resolve tickets",
                "My Assigned Tickets, Work Queue, Change Requests, Implementation");

        add("REPORT_VIEW", "Resources", "View reports", "Reports");
        add("ASSET_MANAGE", "Resources", "Manage assets", "Asset Management");

        add("AUDIT_VIEW", "Administration", "View audit log", "Audit Trail");
        add("ADMIN_USER_MANAGE", "Administration", "Manage portal users and roles", "Users, Roles & Permissions");
        add("ADMIN_MASTERDATA_PROPOSE", "Administration", "Propose configuration changes (maker)",
                "Categories, SLA Config, Workflow Config, Configuration");
        add("ADMIN_MASTERDATA_APPROVE", "Administration", "Approve configuration changes (checker)",
                "Config approvals");
        add("ADMIN_SYSTEM", "Administration", "System settings (non-secret)", "System Configuration");
    }

    private PermissionCatalog() {
    }

    private static void add(String code, String group, String label, String pages) {
        ENTRIES.put(code, new Entry(code, group, label, pages));
    }

    /** Group names in display order, {@link #OTHER_GROUP} last. */
    public static List<String> groups() {
        List<String> groups = new ArrayList<String>();
        for (Entry e : ENTRIES.values()) {
            if (!groups.contains(e.getGroup())) {
                groups.add(e.getGroup());
            }
        }
        groups.add(OTHER_GROUP);
        return Collections.unmodifiableList(groups);
    }

    /** Metadata for {@code code}; unknown codes fall back to the database description. */
    public static Entry describe(String code, String dbDescription) {
        Entry e = ENTRIES.get(code);
        if (e != null) {
            return e;
        }
        return new Entry(code, OTHER_GROUP, dbDescription == null ? code : dbDescription, "");
    }

    /** Sort key that keeps catalogue order inside a group. */
    public static int order(String code) {
        int i = 0;
        for (String c : ENTRIES.keySet()) {
            if (c.equals(code)) {
                return i;
            }
            i++;
        }
        return Integer.MAX_VALUE;
    }

    public static final class Entry {
        private final String code;
        private final String group;
        private final String label;
        private final String pages;

        Entry(String code, String group, String label, String pages) {
            this.code = code;
            this.group = group;
            this.label = label;
            this.pages = pages;
        }

        public String getCode() {
            return code;
        }

        public String getGroup() {
            return group;
        }

        public String getLabel() {
            return label;
        }

        public String getPages() {
            return pages;
        }
    }
}
