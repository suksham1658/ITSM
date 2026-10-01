package com.nbfc.itsm.notification;

import com.nbfc.itsm.domain.Category;
import com.nbfc.itsm.domain.CategoryRepository;
import com.nbfc.itsm.domain.Employee;
import com.nbfc.itsm.domain.EmployeeRepository;
import com.nbfc.itsm.domain.EmployeeRoleAssignment;
import com.nbfc.itsm.domain.EmployeeRoleAssignmentRepository;
import com.nbfc.itsm.domain.Notification;
import com.nbfc.itsm.domain.NotificationRepository;
import com.nbfc.itsm.domain.RoleRepository;
import com.nbfc.itsm.domain.SubCategoryRepository;
import com.nbfc.itsm.domain.Ticket;
import com.nbfc.itsm.domain.TicketTypeRepository;
import com.nbfc.itsm.identity.PortalUserService;
import com.nbfc.itsm.security.ItsmUserPrincipal;
import com.nbfc.itsm.seed.CatalogSeedService;
import com.nbfc.itsm.ticket.TicketForm;
import com.nbfc.itsm.ticket.TicketService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

import static org.hamcrest.Matchers.containsString;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;

/** Who is told what, at each step of a Service Request and an Incident. */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class NotificationFlowTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private CatalogSeedService catalogSeedService;
    @Autowired private TicketService ticketService;
    @Autowired private PortalUserService portalUserService;
    @Autowired private EmployeeRepository employeeRepository;
    @Autowired private RoleRepository roleRepository;
    @Autowired private EmployeeRoleAssignmentRepository assignmentRepository;
    @Autowired private TicketTypeRepository ticketTypeRepository;
    @Autowired private CategoryRepository categoryRepository;
    @Autowired private SubCategoryRepository subCategoryRepository;
    @Autowired private NotificationRepository notificationRepository;

    private Employee hod;
    private Employee lead;
    private Employee requester;
    private Employee desk;
    private Employee impl;

    @BeforeEach
    void setUp() {
        catalogSeedService.ensureSeeded();
        hod = employee("E-NF-HOD", "Notify HOD", null, "EMPLOYEE", "HOD");
        lead = employee("E-NF-LEAD", "Notify Lead", hod, "EMPLOYEE");
        requester = employee("E-NF-REQ", "Notify Requester", lead, "EMPLOYEE");
        desk = employee("E-NF-SD", "Notify Desk", null, "EMPLOYEE", "IT_SERVICE_DESK");
        impl = employee("E-NF-IMP", "Notify Implementor", null, "EMPLOYEE", "IT_IMPLEMENTOR");
    }

    @AfterEach
    void clear() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void serviceRequestApprovalChainAndDecline() {
        Ticket sr = ticketService.save(as(requester), form("SERVICE_REQUEST", "NETWORK"));
        assertTrue(titles(requester, sr).stream().anyMatch(t -> t.startsWith("Submitted")), "requester: submitted");
        assertTrue(titles(lead, sr).stream().anyMatch(t -> t.startsWith("Approval needed")), "manager: approval needed");
        assertTrue(titles(hod, sr).isEmpty(), "HOD not yet");

        ticketService.applyAction(as(lead), sr.getTicketId(), "APPROVE", "Looks fine from my side", null);
        assertTrue(titles(requester, sr).stream().anyMatch(t -> t.startsWith("Approved by Notify Lead")));
        assertTrue(titles(hod, sr).stream().anyMatch(t -> t.startsWith("Approval needed")), "next in chain notified");
        assertTrue(titles(lead, sr).stream().noneMatch(t -> t.startsWith("Approved")), "actor not told about own action");

        ticketService.applyAction(as(hod), sr.getTicketId(), "REJECT", "Not budgeted this quarter", null);
        List<Notification> mine = notificationRepository.findByRecipientIdAndTicketId(requester.getEmployeeId(), sr.getTicketId());
        assertTrue(mine.stream().anyMatch(n -> n.getTitle().startsWith("Declined by Notify HOD")
                && n.getBody().contains("Not budgeted this quarter")));
    }

    @Test
    void incidentDeskImplementorAndConfirmation() {
        Ticket inc = ticketService.save(as(requester), form("INCIDENT", "HARDWARE"));
        assertTrue(titles(desk, inc).stream().anyMatch(t -> t.startsWith("New in IT Service Desk")), "desk queue arrival");

        ticketService.applyAction(as(desk), inc.getTicketId(), "ASSIGN", null, impl.getEmployeeId());
        assertTrue(titles(impl, inc).stream().anyMatch(t -> t.startsWith("Assigned to you")));
        assertTrue(titles(requester, inc).stream().anyMatch(t -> t.startsWith("Assigned")));

        ticketService.applyAction(as(impl), inc.getTicketId(), "START", null, null);
        assertTrue(titles(requester, inc).stream().anyMatch(t -> t.startsWith("In progress")));
        ticketService.applyAction(as(impl), inc.getTicketId(), "RESOLVE", "Replaced keyboard", null);
        assertTrue(titles(requester, inc).stream().anyMatch(t -> t.startsWith("Resolved, please confirm")),
                "requester asked to confirm");

        ticketService.applyAction(as(requester), inc.getTicketId(), "APPROVE", null, null);
        assertTrue(titles(impl, inc).stream().anyMatch(t -> t.startsWith("Closed")), "implementor told it closed");
    }

    @Test
    void bellShowsCountAndOpeningMarksRead() throws Exception {
        Ticket inc = ticketService.save(as(requester), form("INCIDENT", "HARDWARE"));
        ItsmUserPrincipal deskUser = portalUserService.toPrincipal(desk);
        Notification n = notificationRepository.findByRecipientIdAndTicketId(desk.getEmployeeId(), inc.getTicketId()).get(0);
        assertFalse(n.isRead());

        mockMvc.perform(get("/notifications").with(authentication(token(deskUser))))
                .andExpect(content().string(containsString("class=\"notif-count\"")))
                .andExpect(content().string(containsString(inc.getPublicNumber())));

        mockMvc.perform(get("/notifications/{id}/open", n.getNotificationId()).with(authentication(token(deskUser))))
                .andExpect(redirectedUrl("/tickets/" + inc.getTicketId()));
        assertTrue(notificationRepository.findById(n.getNotificationId()).get().isRead());

        // Someone else's notification cannot be opened or marked.
        ItsmUserPrincipal other = portalUserService.toPrincipal(impl);
        Notification forRequester = notificationRepository.findByRecipientIdAndTicketId(requester.getEmployeeId(), inc.getTicketId()).get(0);
        mockMvc.perform(get("/notifications/{id}/open", forRequester.getNotificationId()).with(authentication(token(other))))
                .andExpect(redirectedUrl("/notifications"));
        assertFalse(notificationRepository.findById(forRequester.getNotificationId()).get().isRead());
    }

    // ------------------------------------------------------------------ helpers

    private List<String> titles(Employee who, Ticket t) {
        return notificationRepository.findByRecipientIdAndTicketId(who.getEmployeeId(), t.getTicketId()).stream()
                .map(Notification::getTitle).collect(Collectors.toList());
    }

    private TicketForm form(String typeCode, String categoryCode) {
        Category c = categoryRepository.findByCode(categoryCode).orElseThrow(IllegalStateException::new);
        TicketForm f = new TicketForm();
        f.setSerialMode("NA");
        f.setTicketTypeId(ticketTypeRepository.findByCode(typeCode).orElseThrow(IllegalStateException::new).getTicketTypeId());
        f.setCategoryId(c.getCategoryId());
        f.setSubCategoryId(subCategoryRepository.findByCategoryAndActiveTrueOrderBySortOrderAsc(c).get(0).getSubCategoryId());
        f.setSubject("Notification test " + typeCode);
        f.setDescription("Checking who gets notified at each step.");
        f.setIntent("submit");
        return f;
    }

    private ItsmUserPrincipal as(Employee e) {
        ItsmUserPrincipal p = portalUserService.toPrincipal(employeeRepository.findById(e.getEmployeeId()).get());
        SecurityContextHolder.getContext().setAuthentication(token(p));
        return p;
    }

    private static UsernamePasswordAuthenticationToken token(ItsmUserPrincipal p) {
        return new UsernamePasswordAuthenticationToken(p, null, p.getAuthorities());
    }

    private Employee employee(String no, String name, Employee manager, String... roles) {
        Employee e = new Employee();
        e.setEmployeeNo(no);
        e.setSamAccountName(no.toLowerCase());
        e.setDisplayName(name);
        e.setPortalActive(true);
        e.setManager(manager);
        e = employeeRepository.save(e);
        for (String code : roles) {
            EmployeeRoleAssignment row = new EmployeeRoleAssignment();
            row.setEmployee(e);
            row.setRole(roleRepository.findByCode(code).orElseThrow(() -> new IllegalStateException(code)));
            assignmentRepository.save(row);
        }
        return e;
    }
}
