package com.nbfc.itsm.workflow;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nbfc.itsm.domain.WorkflowRuleRepository;
import com.nbfc.itsm.exception.ItsmException;
import com.nbfc.itsm.ticket.TicketMatchContext;
import org.junit.jupiter.api.Test;

import java.util.Collections;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class WorkflowMatcherServiceTest {

    private final WorkflowMatcherService matcher =
            new WorkflowMatcherService(null, new ObjectMapper());

    @Test
    void incidentConditionDoesNotMatchServiceRequest() {
        TicketMatchContext incident = new TicketMatchContext(
                "Incident", "INCIDENT", "Hardware", "Laptop Issue", "Normal", "IT", "Medium");
        TicketMatchContext sr = new TicketMatchContext(
                "Service Request", "SERVICE_REQUEST", "Network", "VPN Access", "Normal", "IT", "Medium");
        assertTrue(matcher.matches("{\"ticket_type\":[\"Incident\"]}", incident));
        assertFalse(matcher.matches("{\"ticket_type\":[\"Incident\"]}", sr));
        assertTrue(matcher.matches("{\"ticket_type\":[\"Service Request\"]}", sr));
        assertFalse(matcher.matches("{\"ticket_type\":[\"Service Request\"]}", incident));
        assertTrue(matcher.matches("{}", incident));
        assertTrue(matcher.matches("{\"category\":[\"Cyber Security\"]}",
                new TicketMatchContext("Incident", "INCIDENT", "Cyber Security", "Phishing Incident",
                        "Normal", "IT", "High")));
    }

    @Test
    void invalidConditionJsonDoesNotMatch() {
        TicketMatchContext ctx = new TicketMatchContext(
                "Incident", "INCIDENT", "Hardware", "Laptop Issue", "Normal", "IT", "Medium");
        assertFalse(matcher.matches("{not-json", ctx));
    }

    @Test
    void matchFailsClosedWhenNoActiveRule() {
        WorkflowRuleRepository repo = mock(WorkflowRuleRepository.class);
        when(repo.findByStatusCodeOrderByPriorityAsc("Active")).thenReturn(Collections.emptyList());
        WorkflowMatcherService svc = new WorkflowMatcherService(repo, new ObjectMapper());
        TicketMatchContext ctx = new TicketMatchContext(
                "Incident", "INCIDENT", "Hardware", "Laptop Issue", "Normal", "IT", "Medium");
        ItsmException ex = assertThrows(ItsmException.class, () -> svc.match(ctx));
        assertEquals("WORKFLOW_NO_MATCH", ex.getCode());
    }
}
