package com.nbfc.itsm.web;

import org.springframework.ui.Model;

import javax.servlet.http.HttpServletRequest;
import java.net.URI;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * "Back" target for pages that can be opened from several lists (e.g. ticket detail from
 * Approvals, a queue, SLA Monitoring or the dashboard). Uses the Referer only when it is a
 * same-host portal list page from {@link #LIST_PAGES}; anything else falls back to a fixed page,
 * so the link can never point outside the portal.
 */
final class BackLinks {

    /** Context-relative list pages a detail page may return to, with their button labels. */
    private static final Map<String, String> LIST_PAGES = new LinkedHashMap<String, String>();

    static {
        LIST_PAGES.put("/approvals", "Back to Approvals");
        LIST_PAGES.put("/queue/", "Back to Queue");
        LIST_PAGES.put("/sla", "Back to SLA Monitoring");
        LIST_PAGES.put("/escalations", "Back to Escalations");
        LIST_PAGES.put("/risk", "Back to Risk Dashboard");
        LIST_PAGES.put("/tickets/team", "Back to Team Requests");
        LIST_PAGES.put("/tickets/department", "Back to Department Requests");
        LIST_PAGES.put("/tickets/security", "Back to Security Requests");
        LIST_PAGES.put("/tickets/changes", "Back to Change Requests");
        LIST_PAGES.put("/tickets", "Back to My Tickets");
        LIST_PAGES.put("/reports", "Back to Reports");
        LIST_PAGES.put("/admin/users", "Back to Users");
        LIST_PAGES.put("/admin/roles", "Back to Roles & Permissions");
        LIST_PAGES.put("/admin/roles/", "Back to Role");
        LIST_PAGES.put("/", "Back to Dashboard");
    }

    private BackLinks() {
    }

    /** Adds {@code backHref} (context-relative, may keep list filters) and {@code backLabel} to the model. */
    static void addTo(Model model, HttpServletRequest request, String fallbackHref, String fallbackLabel) {
        String[] back = resolve(request, fallbackHref, fallbackLabel);
        model.addAttribute("backHref", back[0]);
        model.addAttribute("backLabel", back[1]);
    }

    /**
     * Like {@link #addTo(Model, HttpServletRequest, String, String)} but remembers the last list
     * page in the session under {@code sessionKey}, so the link survives the page reloading itself
     * (e.g. after posting an approval or comment, when the Referer is the detail page).
     */
    static void addTo(Model model, HttpServletRequest request, String fallbackHref, String fallbackLabel,
                      String sessionKey) {
        String[] back = resolve(request, null, null);
        javax.servlet.http.HttpSession session = request.getSession(false);
        if (back[0] != null) {
            if (session != null) {
                session.setAttribute(sessionKey, back);
            }
        } else if (session != null && session.getAttribute(sessionKey) instanceof String[]) {
            back = (String[]) session.getAttribute(sessionKey);
        } else {
            back = new String[] {fallbackHref, fallbackLabel};
        }
        model.addAttribute("backHref", back[0]);
        model.addAttribute("backLabel", back[1]);
    }

    static String[] resolve(HttpServletRequest request, String fallbackHref, String fallbackLabel) {
        String referer = request.getHeader("Referer");
        if (referer == null) {
            return new String[] {fallbackHref, fallbackLabel};
        }
        try {
            URI uri = new URI(referer);
            if (uri.getHost() != null && !uri.getHost().equalsIgnoreCase(request.getServerName())) {
                return new String[] {fallbackHref, fallbackLabel};
            }
            String path = uri.getRawPath() == null ? "" : uri.getRawPath();
            String ctx = request.getContextPath() == null ? "" : request.getContextPath();
            if (!ctx.isEmpty()) {
                if (!path.startsWith(ctx)) {
                    return new String[] {fallbackHref, fallbackLabel};
                }
                path = path.substring(ctx.length());
            }
            if (path.isEmpty()) {
                path = "/";
            }
            if (!path.startsWith("/") || path.startsWith("//")) {
                return new String[] {fallbackHref, fallbackLabel};
            }
            for (Map.Entry<String, String> page : LIST_PAGES.entrySet()) {
                String key = page.getKey();
                // Keys ending in "/" match a section (/queue/desk, /queue/mine...); others match exactly.
                boolean match = key.length() > 1 && key.endsWith("/") ? path.startsWith(key) : path.equals(key);
                if (match) {
                    String query = uri.getRawQuery();
                    return new String[] {query == null ? path : path + "?" + query, page.getValue()};
                }
            }
        } catch (Exception ignored) {
            // malformed Referer: use the fallback
        }
        return new String[] {fallbackHref, fallbackLabel};
    }
}
