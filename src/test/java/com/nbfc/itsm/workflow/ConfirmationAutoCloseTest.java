package com.nbfc.itsm.workflow;

import com.nbfc.itsm.config.ItsmProperties;
import com.nbfc.itsm.domain.AssignmentGroupMember;
import com.nbfc.itsm.domain.AssignmentGroupMemberRepository;
import com.nbfc.itsm.domain.AssignmentGroupRepository;
import com.nbfc.itsm.domain.Category;
import com.nbfc.itsm.domain.CategoryRepository;
import com.nbfc.itsm.domain.Employee;
import com.nbfc.itsm.domain.EmployeeRepository;
import com.nbfc.itsm.domain.EmployeeRoleAssignment;
import com.nbfc.itsm.domain.EmployeeRoleAssignmentRepository;
import com.nbfc.itsm.domain.RoleRepository;
import com.nbfc.itsm.domain.SubCategoryRepository;
import com.nbfc.itsm.domain.Ticket;
import com.nbfc.itsm.domain.TicketRepository;
import com.nbfc.itsm.domain.TicketSlaRepository;
import com.nbfc.itsm.domain.TicketTypeRepository;
import com.nbfc.itsm.domain.WorkflowInstanceRepository;
import com.nbfc.itsm.domain.WorkflowInstanceStage;
import com.nbfc.itsm.domain.WorkflowInstanceStageRepository;
import com.nbfc.itsm.exception.ItsmException;
import com.nbfc.itsm.identity.PortalUserService;
import com.nbfc.itsm.notification.TicketEmailEvent;
import com.nbfc.itsm.notification.TicketEmailService;
import com.nbfc.itsm.security.ItsmUserPrincipal;
import com.nbfc.itsm.seed.CatalogSeedService;
import com.nbfc.itsm.ticket.TicketForm;
import com.nbfc.itsm.ticket.TicketService;
import com.nbfc.itsm.util.TimeUtc;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.event.ApplicationEvents;
import org.springframework.test.context.event.RecordApplicationEvents;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;

import static org.hamcrest.Matchers.allOf;
import static org.hamcrest.Matchers.containsString;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.flash;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;

/**
 * Requester confirmation: Resolved / Not resolved; no answer within 48 hours closes the ticket and mails a
 * re-open link; the requester can re-open (back to the implementor) or confirm within 48 hours of closure.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
@RecordApplicationEvents
class ConfirmationAutoCloseTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private ApplicationEvents events;
    @Autowired private CatalogSeedService catalogSeedService;
    @Autowired private TicketService ticketService;
    @Autowired private ConfirmationAutoCloseJob job;
    @Autowired private TicketEmailService emailService;
    @Autowired private ItsmProperties properties;
    @Autowired private PortalUserService portalUserService;
    @Autowired private EmployeeRepository employeeRepository;
    @Autowired private RoleRepository roleRepository;
    @Autowired private EmployeeRoleAssignmentRepository assignmentRepository;
    @Autowired private AssignmentGroupRepository groupRepository;
    @Autowired private AssignmentGroupMemberRepository memberRepository;
    @Autowired private TicketTypeRepository ticketTypeRepository;
    @Autowired private CategoryRepository categoryRepository;
    @Autowired private SubCategoryRepository subCategoryRepository;
    @Autowired private TicketRepository ticketRepository;
    @Autowired private TicketSlaRepository slaRepository;
    @Autowired private WorkflowInstanceRepository instanceRepository;
    @Autowired private WorkflowInstanceStageRepository stageRepository;

    private Employee requester;
    private Employee desk;
    private Employee impl;

    @BeforeEach
    void setUp() {
        catalogSeedService.ensureSeeded();
        requester = employee("E-AC-REQ", "Rohan Requester", null, "EMPLOYEE");
        desk = employee("E-AC-SD", "Divya Desk", "IT_SERVICE_DESK", "EMPLOYEE", "IT_SERVICE_DESK");
        impl = employee("E-AC-IM", "Imran Implementor", "IT_IMPLEMENTORS", "EMPLOYEE", "IT_IMPLEMENTOR");
    }

    @AfterEach
    void clear() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void requesterSeesResolvedAndNotResolvedAndNotResolvedGoesBackToTheImplementor() throws Exception {
        Ticket t = resolved();
        mockMvc.perform(get("/tickets/{id}", t.getTicketId()).with(authentication(token(requester))))
                .andExpect(content().string(allOf(containsString("Is your issue resolved?"),
                        containsString("resolved — close ticket"),
                        containsString("No, not resolved — send back to implementor"),
                        containsString("closes automatically on"))));

        assertThrows(ItsmException.class,
                () -> ticketService.applyAction(as(requester), t.getTicketId(), "SEND_BACK", "", null));
        ticketService.applyAction(as(requester), t.getTicketId(), "SEND_BACK", "Still crashes after lunch", null);
        assertEquals("FULFILMENT", current(t).getStageType(), "back with the implementor");
        assertEquals(impl.getEmployeeId(), current(t).getResolvedEmployee().getEmployeeId());
        assertNull(slaRepository.findByTicket(ticketRepository.findById(t.getTicketId()).get()).get().getResolvedUtc(),
                "SLA clock runs again");
    }

    @Test
    void noAnswerWithinTheWindowClosesItAndMailsAReopenLink() {
        Ticket t = resolved();
        Instant now = TimeUtc.now();
        assertEquals(0, job.run(now.plus(Duration.ofHours(47))), "not yet");
        assertEquals("Resolved", status(t));

        assertEquals(1, job.run(now.plus(Duration.ofHours(49))));
        assertEquals("Closed", status(t));
        assertEquals(1, events.stream(TicketEmailEvent.class)
                .filter(e -> e.getKind() == TicketEmailEvent.Kind.AUTO_CLOSED && e.getTicketId().equals(t.getTicketId())).count());
        assertEquals(0, job.run(now.plus(Duration.ofHours(50))), "only once");

        String old = properties.getMail().getPortalUrl();
        try {
            properties.getMail().setPortalUrl("http://itnexa.local:8090/itsm-portal");
            String body = emailService.body(ticketRepository.findById(t.getTicketId()).get(), TicketEmailEvent.Kind.AUTO_CLOSED);
            assertTrue(body.contains("If you are not satisfied, please re-open this ticket"), body);
            assertTrue(body.contains("http://itnexa.local:8090/itsm-portal/tickets/" + t.getTicketId() + "/reopen"), body);
            assertTrue(body.contains("2 days"), body);
        } finally {
            properties.getMail().setPortalUrl(old);
        }
    }

    @Test
    void reopenWithinTwoDaysSendsItBackAndTheLinkExpiresAfterwards() throws Exception {
        Ticket t = autoClosed();
        mockMvc.perform(get("/tickets/{id}/reopen", t.getTicketId()).with(authentication(token(requester))))
                .andExpect(redirectedUrl("/tickets/" + t.getTicketId() + "#reopen"));
        mockMvc.perform(get("/tickets/{id}", t.getTicketId()).with(authentication(token(requester))))
                .andExpect(content().string(allOf(containsString("Closed automatically — is your issue resolved?"),
                        containsString("re-open and send back"))));

        ticketService.reopen(as(requester), t.getTicketId(), "The printer still jams on page two");
        assertEquals("FULFILMENT", current(t).getStageType());
        assertEquals("Assigned", status(t));

        // Resolve, no answer, closed again; then let the re-open period run out.
        ticketService.applyAction(as(impl), t.getTicketId(), "RESOLVE", "Cleaned the rollers", null);
        assertEquals(1, job.run(TimeUtc.now().plus(Duration.ofHours(49))));
        confirmationStep(t).setActedAtUtc(TimeUtc.now().minus(Duration.ofHours(49)));
        ItsmException expired = assertThrows(ItsmException.class,
                () -> ticketService.reopen(as(requester), t.getTicketId(), "It is broken again today"));
        assertEquals("REOPEN_EXPIRED", expired.getCode());
        mockMvc.perform(get("/tickets/{id}/reopen", t.getTicketId()).with(authentication(token(requester))))
                .andExpect(flash().attribute("errorMessage", containsString("expired")));
    }

    @Test
    void confirmingAfterAutoCloseKeepsItClosedForGood() {
        Ticket t = autoClosed();
        assertThrows(org.springframework.security.access.AccessDeniedException.class,
                () -> ticketService.reopen(as(impl), t.getTicketId(), "Only the requester can do this"));
        ticketService.confirmClosed(as(requester), t.getTicketId());
        assertEquals("Closed", status(t));
        assertThrows(ItsmException.class,
                () -> ticketService.reopen(as(requester), t.getTicketId(), "Changed my mind about this"));
    }

    // ------------------------------------------------------------------ helpers

    private Ticket resolved() {
        Category net = categoryRepository.findByCode("NETWORK").orElseThrow(IllegalStateException::new);
        TicketForm f = new TicketForm();
        f.setTicketTypeId(ticketTypeRepository.findByCode("INCIDENT").orElseThrow(IllegalStateException::new).getTicketTypeId());
        f.setCategoryId(net.getCategoryId());
        f.setSubCategoryId(subCategoryRepository.findByCategoryAndActiveTrueOrderBySortOrderAsc(net).get(0).getSubCategoryId());
        f.setSubject("Network printer offline");
        f.setDescription("The floor printer shows offline since the morning.");
        f.setIntent("submit");
        Ticket t = ticketService.save(as(requester), f);
        ticketService.applyAction(as(desk), t.getTicketId(), "ASSIGN", null, impl.getEmployeeId());
        ticketService.applyAction(as(impl), t.getTicketId(), "RESOLVE", "Restarted the print server", null);
        assertEquals("CONFIRMATION", current(t).getStageType());
        assertNotNull(slaRepository.findByTicket(ticketRepository.findById(t.getTicketId()).get()).get().getResolvedUtc());
        return t;
    }

    private Ticket autoClosed() {
        Ticket t = resolved();
        assertEquals(1, job.run(TimeUtc.now().plus(Duration.ofHours(49))));
        assertEquals("Closed", status(t));
        return t;
    }

    private WorkflowInstanceStage confirmationStep(Ticket t) {
        for (WorkflowInstanceStage s : stageRepository.findByWorkflowInstanceOrderByStageOrderAsc(
                instanceRepository.findByTicketId(t.getTicketId()).get())) {
            if ("CONFIRMATION".equals(s.getStageType())) {
                return s;
            }
        }
        throw new IllegalStateException("no confirmation step");
    }

    private WorkflowInstanceStage current(Ticket t) {
        for (WorkflowInstanceStage s : stageRepository.findByWorkflowInstanceOrderByStageOrderAsc(
                instanceRepository.findByTicketId(t.getTicketId()).get())) {
            if ("Current".equals(s.getStatusCode())) {
                return s;
            }
        }
        throw new IllegalStateException("no current stage");
    }

    private String status(Ticket t) {
        return ticketRepository.findById(t.getTicketId()).get().getStatusCode();
    }

    private ItsmUserPrincipal as(Employee e) {
        ItsmUserPrincipal p = portalUserService.toPrincipal(employeeRepository.findById(e.getEmployeeId()).get());
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(p, null, p.getAuthorities()));
        return p;
    }

    private UsernamePasswordAuthenticationToken token(Employee e) {
        ItsmUserPrincipal p = portalUserService.toPrincipal(employeeRepository.findById(e.getEmployeeId()).get());
        return new UsernamePasswordAuthenticationToken(p, null, p.getAuthorities());
    }

    private Employee employee(String no, String name, String groupCode, String... roles) {
        Employee e = new Employee();
        e.setEmployeeNo(no);
        e.setSamAccountName(no.toLowerCase());
        e.setDisplayName(name);
        e.setPortalActive(true);
        e = employeeRepository.save(e);
        for (String code : roles) {
            EmployeeRoleAssignment row = new EmployeeRoleAssignment();
            row.setEmployee(e);
            row.setRole(roleRepository.findByCode(code).orElseThrow(() -> new IllegalStateException(code)));
            assignmentRepository.save(row);
        }
        if (groupCode != null) {
            AssignmentGroupMember m = new AssignmentGroupMember();
            m.setAssignmentGroup(groupRepository.findByCode(groupCode).orElseThrow(() -> new IllegalStateException(groupCode)));
            m.setEmployee(e);
            memberRepository.save(m);
        }
        return e;
    }
}
