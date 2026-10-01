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
import com.nbfc.itsm.domain.SystemSetting;
import com.nbfc.itsm.domain.SystemSettingRepository;
import com.nbfc.itsm.domain.Ticket;
import com.nbfc.itsm.domain.TicketRepository;
import com.nbfc.itsm.domain.TicketTypeRepository;
import com.nbfc.itsm.domain.WorkflowInstance;
import com.nbfc.itsm.domain.WorkflowInstanceRepository;
import com.nbfc.itsm.domain.WorkflowInstanceStage;
import com.nbfc.itsm.domain.WorkflowInstanceStageRepository;
import com.nbfc.itsm.exception.ItsmException;
import com.nbfc.itsm.identity.PortalUserService;
import com.nbfc.itsm.security.ItsmUserPrincipal;
import com.nbfc.itsm.seed.CatalogSeedService;
import com.nbfc.itsm.ticket.TicketForm;
import com.nbfc.itsm.ticket.TicketService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Incident: requester raises it, it waits in the Service Desk queue, a desk agent assigns an
 * implementor from the IT Implementors group, the implementor starts / reassigns / resolves,
 * which closes it (requester confirmation is off by default; it can be switched on in System Configuration).
 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
class IncidentFlowTest {

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
    @Autowired private WorkflowInstanceRepository instanceRepository;
    @Autowired private WorkflowInstanceStageRepository instanceStageRepository;
    @Autowired private SystemSettingRepository settingRepository;

    private Employee requester;
    private Employee desk;
    private Employee impl1;
    private Employee impl2;

    @BeforeEach
    void setUp() {
        catalogSeedService.ensureSeeded();
        requester = employee("E-IF-REQ", "Incident Requester", null, "EMPLOYEE");
        desk = employee("E-IF-SD", "Desk Agent", "IT_SERVICE_DESK", "EMPLOYEE", "IT_SERVICE_DESK");
        impl1 = employee("E-IF-IM1", "Implementor One", "IT_IMPLEMENTORS", "EMPLOYEE", "IT_IMPLEMENTOR");
        impl2 = employee("E-IF-IM2", "Implementor Two", "IT_IMPLEMENTORS", "EMPLOYEE", "IT_IMPLEMENTOR");
    }

    @AfterEach
    void clear() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void queueListsNewestTicketFirst() {
        Ticket a = ticketService.save(as(requester), incident());
        Ticket b = ticketService.save(as(requester), incident());
        Ticket c = ticketService.save(as(requester), incident());
        // Creation order deliberately differs from id order: b newest, a oldest.
        java.time.Instant base = java.time.Instant.parse("2026-01-01T00:00:00Z");
        a.setCreatedAtUtc(base);
        b.setCreatedAtUtc(base.plusSeconds(120));
        c.setCreatedAtUtc(base.plusSeconds(60));
        ticketRepository.saveAll(java.util.Arrays.asList(a, b, c));
        ticketRepository.flush();

        java.util.List<Long> ids = new java.util.ArrayList<Long>();
        for (Ticket t : ticketService.queueByStageType("ASSIGNMENT")) {
            if (t.getTicketId().equals(a.getTicketId()) || t.getTicketId().equals(b.getTicketId())
                    || t.getTicketId().equals(c.getTicketId())) {
                ids.add(t.getTicketId());
            }
        }
        assertEquals(java.util.Arrays.asList(b.getTicketId(), c.getTicketId(), a.getTicketId()), ids);
    }

    @Test
    void incidentGoesDeskThenImplementorThenRequesterThenClosed() {
        Ticket t = ticketService.save(as(requester), incident());
        assertEquals("ASSIGNMENT", current(t).getStageType());
        assertTrue(ticketService.queueByStageType("ASSIGNMENT").stream().anyMatch(x -> x.getTicketId().equals(t.getTicketId())),
                "visible in the service desk Ticket Queue");

        // Desk can only assign people from IT Implementors, not a fellow desk agent.
        ItsmException wrong = assertThrows(ItsmException.class,
                () -> ticketService.applyAction(as(desk), t.getTicketId(), "ASSIGN", null, desk.getEmployeeId()));
        assertEquals("ASSIGNEE_NOT_IN_GROUP", wrong.getCode());

        ticketService.applyAction(as(desk), t.getTicketId(), "ASSIGN", "Laptop issue, please check", impl1.getEmployeeId());
        WorkflowInstanceStage work = current(t);
        assertEquals("FULFILMENT", work.getStageType());
        assertEquals(impl1.getEmployeeId(), work.getResolvedEmployee().getEmployeeId());
        assertEquals("Assigned", status(t));
        assertTrue(ticketService.assignedTo(as(impl1)).stream().anyMatch(x -> x.getTicketId().equals(t.getTicketId())),
                "in implementor's My Assigned Tickets");

        ticketService.applyAction(as(impl1), t.getTicketId(), "START", null, null);
        assertEquals("In Progress", status(t));

        // Reassign keeps the ticket on the implementation step, now owned by Implementor Two.
        ticketService.applyAction(as(impl1), t.getTicketId(), "REASSIGN", "Handing over to network specialist", impl2.getEmployeeId());
        assertEquals("FULFILMENT", current(t).getStageType());
        assertEquals(impl2.getEmployeeId(), current(t).getResolvedEmployee().getEmployeeId());
        assertEquals("Assigned", status(t));

        ticketService.applyAction(as(impl2), t.getTicketId(), "START", null, null);
        ticketService.applyAction(as(impl2), t.getTicketId(), "RESOLVE", "Replaced the faulty RAM module", null);
        assertEquals("Closed", status(t), "resolving closes the ticket, no requester confirmation");
        assertTrue(instanceStageRepository.findByWorkflowInstanceOrderByStageOrderAsc(
                instanceRepository.findByTicketId(t.getTicketId()).get()).stream()
                .anyMatch(s -> "CONFIRMATION".equals(s.getStageType()) && "Skipped".equals(s.getStatusCode())),
                "confirmation step shown as skipped");
    }

    @Test
    void approvalsPageListsWhateverIsWaitingForTheUser() {
        Ticket t = ticketService.save(as(requester), incident());
        assertTrue(waiting(desk, t).contains("ASSIGNMENT"), "desk: assign");
        assertTrue(waiting(impl1, t).isEmpty(), "implementor: not yet");
        assertTrue(waiting(requester, t).isEmpty(), "requester: nothing to do");

        ticketService.applyAction(as(desk), t.getTicketId(), "ASSIGN", null, impl1.getEmployeeId());
        assertTrue(waiting(desk, t).isEmpty(), "desk: done");
        assertTrue(waiting(impl1, t).contains("FULFILMENT"), "implementor: work on it");
        assertTrue(waiting(impl2, t).isEmpty(), "other implementor: not theirs");

        ticketService.applyAction(as(impl1), t.getTicketId(), "START", null, null);
        ticketService.applyAction(as(impl1), t.getTicketId(), "RESOLVE", "Replaced the faulty RAM module", null);
        assertTrue(waiting(impl1, t).isEmpty(), "closed tickets drop off");
    }

    private java.util.List<String> waiting(Employee who, Ticket t) {
        java.util.List<String> types = new java.util.ArrayList<String>();
        for (com.nbfc.itsm.ticket.WaitingItem i : ticketService.waitingFor(as(who))) {
            if (i.getTicket().getTicketId().equals(t.getTicketId())) {
                types.add(i.getStageType());
            }
        }
        return types;
    }

    @Test
    void requesterConfirmsWhenSwitchedOnInSystemConfiguration() {
        SystemSetting on = new SystemSetting();
        on.setSettingKey("workflow.requester-confirmation");
        on.setSettingValue("true");
        on.setCategory("workflow");
        settingRepository.save(on);
        Ticket t = ticketService.save(as(requester), incident());
        ticketService.applyAction(as(desk), t.getTicketId(), "ASSIGN", null, impl1.getEmployeeId());
        ticketService.applyAction(as(impl1), t.getTicketId(), "START", null, null);
        ticketService.applyAction(as(impl1), t.getTicketId(), "RESOLVE", "Replaced the faulty RAM module", null);
        assertEquals("CONFIRMATION", current(t).getStageType());
        assertEquals("Resolved", status(t));

        ticketService.applyAction(as(requester), t.getTicketId(), "APPROVE", "Working fine now, thank you", null);
        assertEquals("Closed", status(t));
    }

    @Test
    void roleAloneIsEnoughToWorkTheDeskAndBeAssigned() {
        // Neither is added to any group on Admin > Users: the roles are enough.
        Employee roleOnlyDesk = employee("E-IF-SD2", "Role-only Desk", null, "EMPLOYEE", "IT_SERVICE_DESK");
        Employee roleOnlyImpl = employee("E-IF-IM3", "Role-only Implementor", null, "EMPLOYEE", "IT_IMPLEMENTOR");
        Ticket t = ticketService.save(as(requester), incident());

        assertTrue(ticketService.detail(as(roleOnlyDesk), t.getTicketId()).isCanAct(), "desk role sees Take action");
        ticketService.applyAction(as(roleOnlyDesk), t.getTicketId(), "ASSIGN", null, roleOnlyImpl.getEmployeeId());

        assertEquals(roleOnlyImpl.getEmployeeId(), current(t).getResolvedEmployee().getEmployeeId());
        assertTrue(ticketService.detail(as(roleOnlyImpl), t.getTicketId()).isCanAct(), "implementor can work it");
    }

    // ------------------------------------------------------------------ helpers

    private String status(Ticket t) {
        return ticketRepository.findById(t.getTicketId()).get().getStatusCode();
    }

    private WorkflowInstanceStage current(Ticket t) {
        WorkflowInstance inst = instanceRepository.findByTicketId(t.getTicketId()).orElseThrow(IllegalStateException::new);
        for (WorkflowInstanceStage s : instanceStageRepository.findByWorkflowInstanceOrderByStageOrderAsc(inst)) {
            if ("Current".equals(s.getStatusCode())) {
                return s;
            }
        }
        throw new IllegalStateException("no current stage");
    }

    private TicketForm incident() {
        Category hw = categoryRepository.findByCode("HARDWARE").orElseThrow(IllegalStateException::new);
        TicketForm f = new TicketForm();
        f.setSerialMode("NA");
        f.setTicketTypeId(ticketTypeRepository.findByCode("INCIDENT").orElseThrow(IllegalStateException::new).getTicketTypeId());
        f.setCategoryId(hw.getCategoryId());
        f.setSubCategoryId(subCategoryRepository.findByCategoryAndActiveTrueOrderBySortOrderAsc(hw).get(0).getSubCategoryId());
        f.setSubject("Laptop keeps restarting");
        f.setDescription("Laptop restarts every few minutes since this morning.");
        f.setIntent("submit");
        return f;
    }

    private ItsmUserPrincipal as(Employee e) {
        ItsmUserPrincipal p = portalUserService.toPrincipal(employeeRepository.findById(e.getEmployeeId()).get());
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(p, null, p.getAuthorities()));
        return p;
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
