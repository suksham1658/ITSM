package com.nbfc.itsm.workflow;

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
import com.nbfc.itsm.domain.TicketAssignmentLog;
import com.nbfc.itsm.domain.TicketAssignmentLogRepository;
import com.nbfc.itsm.domain.TicketRepository;
import com.nbfc.itsm.domain.TicketTypeRepository;
import com.nbfc.itsm.exception.ItsmException;
import com.nbfc.itsm.identity.PortalUserService;
import com.nbfc.itsm.security.ItsmUserPrincipal;
import com.nbfc.itsm.seed.CatalogSeedService;
import com.nbfc.itsm.ticket.TicketForm;
import com.nbfc.itsm.ticket.TicketService;
import com.nbfc.itsm.ticket.WaitingItem;
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

import static org.hamcrest.Matchers.allOf;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Implementor's view of a ticket: the requester's directory details (read only), only Reassign /
 * Resolve, and a workflow history of who assigned or reassigned the ticket to whom, with the comment.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class ImplementorViewTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private CatalogSeedService catalogSeedService;
    @Autowired private TicketService ticketService;
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
    @Autowired private TicketAssignmentLogRepository logRepository;

    private Employee requester;
    private Employee desk;
    private Employee impl1;
    private Employee impl2;

    @BeforeEach
    void setUp() {
        catalogSeedService.ensureSeeded();
        requester = employee("E-IV-REQ", "Riya Requester", null, "EMPLOYEE");
        requester.setEmail("riya@corp.in");
        requester.setDesignation("Credit Analyst");
        requester.setPhoneNumber("+91 22 4000 1234");
        requester.setOfficeLocation("Mumbai HO, 5th floor");
        requester = employeeRepository.save(requester);
        desk = employee("E-IV-SD", "Dev Desk", "IT_SERVICE_DESK", "EMPLOYEE", "IT_SERVICE_DESK");
        impl1 = employee("E-IV-IM1", "Isha One", "IT_IMPLEMENTORS", "EMPLOYEE", "IT_IMPLEMENTOR");
        impl2 = employee("E-IV-IM2", "Ishaan Two", "IT_IMPLEMENTORS", "EMPLOYEE", "IT_IMPLEMENTOR");
    }

    @AfterEach
    void clear() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void implementorSeesRequesterDetailsAndOnlyReassignResolve() throws Exception {
        Ticket t = assignedToImpl1();
        mockMvc.perform(get("/tickets/{id}", t.getTicketId()).with(authentication(token(impl1))))
                .andExpect(status().isOk())
                .andExpect(content().string(allOf(
                        containsString("Requester details"), containsString("riya@corp.in"),
                        containsString("+91 22 4000 1234"), containsString("Mumbai HO, 5th floor"),
                        containsString("Credit Analyst"),
                        containsString("<option value=\"REASSIGN\">Reassign</option>"),
                        containsString("<option value=\"RESOLVE\">Resolve</option>"),
                        not(containsString("value=\"HOLD\"")),
                        not(containsString("value=\"ACCEPT\"")), not(containsString("value=\"START\"")))));
        // The requester does not see a card about themselves.
        mockMvc.perform(get("/tickets/{id}", t.getTicketId()).with(authentication(token(requester))))
                .andExpect(content().string(not(containsString("Requester details"))));
    }

    @Test
    void reassignNeedsACommentAndTheWorkflowShowsWhoHandedItToWhom() throws Exception {
        Ticket t = assignedToImpl1();
        assertThrows(ItsmException.class,
                () -> ticketService.applyAction(as(impl1), t.getTicketId(), "REASSIGN", " ", impl2.getEmployeeId()));

        ticketService.applyAction(as(impl1), t.getTicketId(), "REASSIGN", "Needs the network team, handing over", impl2.getEmployeeId());
        List<TicketAssignmentLog> log = logRepository.findByTicketOrderByCreatedAtUtcAscTicketAssignmentLogIdAsc(
                ticketRepository.findById(t.getTicketId()).get());
        assertEquals(2, log.size(), "desk assignment + reassignment");
        assertEquals("ASSIGN", log.get(0).getActionCode());
        assertNull(log.get(0).getFromEmployee());
        assertEquals(impl1.getEmployeeId(), log.get(0).getToEmployee().getEmployeeId());
        assertEquals(desk.getEmployeeId(), log.get(0).getByEmployee().getEmployeeId());
        TicketAssignmentLog re = log.get(1);
        assertEquals("REASSIGN", re.getActionCode());
        assertEquals(impl1.getEmployeeId(), re.getByEmployee().getEmployeeId());
        assertEquals(impl2.getEmployeeId(), re.getToEmployee().getEmployeeId());
        assertEquals("Needs the network team, handing over", re.getRemarks());

        mockMvc.perform(get("/tickets/{id}", t.getTicketId()).with(authentication(token(requester))))
                .andExpect(content().string(allOf(
                        containsString("<b>Reassigned</b> by <b>Isha One</b>"), containsString("to <b>Ishaan Two</b>"),
                        containsString("Needs the network team, handing over"),
                        containsString("<b>Assigned</b> by <b>Dev Desk</b> to <b>Isha One</b>"))));
        // The new implementor can work it and resolve straight away.
        assertEquals(1, ticketService.waitingFor(as(impl2)).stream()
                .filter((WaitingItem i) -> i.getTicket().getTicketId().equals(t.getTicketId())).count());
        ticketService.applyAction(as(impl2), t.getTicketId(), "RESOLVE", "Replaced the switch port", null);
        assertEquals("Resolved", ticketRepository.findById(t.getTicketId()).get().getStatusCode(), "now with the requester");
        ticketService.applyAction(as(requester), t.getTicketId(), "APPROVE", null, null);
        assertEquals("Closed", ticketRepository.findById(t.getTicketId()).get().getStatusCode());
    }

    @Test
    void onHoldOffersReassignAndResolveOnly() throws Exception {
        Ticket t = assignedToImpl1();
        ticketService.applyAction(as(impl1), t.getTicketId(), "HOLD", "Waiting for the spare part", null);
        mockMvc.perform(get("/tickets/{id}", t.getTicketId()).with(authentication(token(impl1))))
                .andExpect(content().string(allOf(containsString("value=\"REASSIGN\""), containsString("value=\"RESOLVE\""),
                        not(containsString("value=\"HOLD\"")))));
        ticketService.applyAction(as(impl1), t.getTicketId(), "RESOLVE", "Part arrived and fitted", null);
        assertEquals("Resolved", ticketRepository.findById(t.getTicketId()).get().getStatusCode());
    }

    // ------------------------------------------------------------------ helpers

    private Ticket assignedToImpl1() {
        Category hw = categoryRepository.findByCode("HARDWARE").orElseThrow(IllegalStateException::new);
        TicketForm f = new TicketForm();
        f.setSerialMode("NA");
        f.setTicketTypeId(ticketTypeRepository.findByCode("INCIDENT").orElseThrow(IllegalStateException::new).getTicketTypeId());
        f.setCategoryId(hw.getCategoryId());
        f.setSubCategoryId(subCategoryRepository.findByCategoryAndActiveTrueOrderBySortOrderAsc(hw).get(0).getSubCategoryId());
        f.setSubject("Desk phone and network port dead");
        f.setDescription("No network on my desk port since this morning.");
        f.setIntent("submit");
        Ticket t = ticketService.save(as(requester), f);
        ticketService.applyAction(as(desk), t.getTicketId(), "ASSIGN", "Please check the port", impl1.getEmployeeId());
        return t;
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
