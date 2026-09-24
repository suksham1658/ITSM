package com.nbfc.itsm.web;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.ui.ExtendedModelMap;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;

class BackLinksTest {

    private static final String[] FALLBACK = {"/tickets", "Back to My Tickets"};

    @Test
    void returnsToTheListPageTheUserCameFrom() {
        assertArrayEquals(new String[] {"/approvals", "Back to Approvals"}, resolve("http://localhost/approvals"));
        assertArrayEquals(new String[] {"/queue/desk?page=2&status=Open", "Back to Queue"},
                resolve("http://localhost/queue/desk?page=2&status=Open"));
        assertArrayEquals(new String[] {"/", "Back to Dashboard"}, resolve("http://localhost/"));
        assertArrayEquals(new String[] {"/tickets/team", "Back to Team Requests"}, resolve("http://localhost/tickets/team"));
        assertArrayEquals(new String[] {"/admin/roles/7", "Back to Role"}, resolve("http://localhost/admin/roles/7"));
    }

    @Test
    void neverPointsOutsideThePortal() {
        assertArrayEquals(FALLBACK, resolve(null));
        assertArrayEquals(FALLBACK, resolve("https://evil.example.com/approvals"));
        assertArrayEquals(FALLBACK, resolve("http://localhost//evil.example.com/x"));
        assertArrayEquals(FALLBACK, resolve("not a uri at all %%%"));
        assertArrayEquals(FALLBACK, resolve("http://localhost/tickets/42"), "another detail page is not a list");
    }

    @Test
    void honoursTheContextPath() {
        MockHttpServletRequest req = new MockHttpServletRequest("GET", "/itsm/tickets/5");
        req.setContextPath("/itsm");
        req.addHeader("Referer", "http://localhost/itsm/sla");
        assertArrayEquals(new String[] {"/sla", "Back to SLA Monitoring"}, BackLinks.resolve(req, FALLBACK[0], FALLBACK[1]));
    }

    @Test
    void remembersTheListAcrossSelfReloads() {
        MockHttpSession session = new MockHttpSession();
        ExtendedModelMap model = new ExtendedModelMap();

        MockHttpServletRequest fromApprovals = request("http://localhost/approvals");
        fromApprovals.setSession(session);
        BackLinks.addTo(model, fromApprovals, FALLBACK[0], FALLBACK[1], "k");
        assertEquals("/approvals", model.get("backHref"));

        // After posting an action the page redirects to itself, so the Referer is the detail page.
        MockHttpServletRequest reload = request("http://localhost/tickets/5");
        reload.setSession(session);
        BackLinks.addTo(model, reload, FALLBACK[0], FALLBACK[1], "k");
        assertEquals("/approvals", model.get("backHref"));
        assertEquals("Back to Approvals", model.get("backLabel"));
    }

    private static String[] resolve(String referer) {
        return BackLinks.resolve(request(referer), FALLBACK[0], FALLBACK[1]);
    }

    private static MockHttpServletRequest request(String referer) {
        MockHttpServletRequest req = new MockHttpServletRequest("GET", "/tickets/5");
        req.setServerName("localhost");
        if (referer != null) {
            req.addHeader("Referer", referer);
        }
        return req;
    }
}
