package com.nbfc.itsm.web;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class UiTextTest {

    private final UiText ui = new UiText();

    @Test
    void legacyManagerPrefixIsHidden() {
        assertEquals("Pankaj Kanhaiya/Authum/IT", ui.stageLabel("Manager approval — Pankaj Kanhaiya/Authum/IT"));
        assertEquals("CISO approval", ui.stageLabel("CISO approval"));
        assertEquals("", ui.stageLabel(null));
    }

    @Test
    void statusColours() {
        assertEquals("badge-flow-strong", ui.ticketStatusClass("In Progress"));
        assertEquals("badge-flow", ui.ticketStatusClass("Pending Approval"));
        assertEquals("badge-flow", ui.ticketStatusClass("Assigned"));
        assertEquals("badge-closed", ui.ticketStatusClass("Closed"));
        assertEquals("badge-rejected", ui.ticketStatusClass("Rejected"));
        assertEquals("badge-pending", ui.ticketStatusClass("On Hold"));
        assertEquals("badge-flow-strong", ui.stageStatusClass("Current"));
        assertEquals("badge-closed", ui.stageStatusClass("Completed"));
        assertEquals("badge-rejected", ui.stageStatusClass("Rejected"));
        assertEquals("Pending approval", ui.configStatusText("PendingApproval"));
    }
}
