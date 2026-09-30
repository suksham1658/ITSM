package com.nbfc.itsm.workflow;

import com.nbfc.itsm.domain.Category;
import com.nbfc.itsm.domain.CategoryRepository;
import com.nbfc.itsm.domain.Employee;
import com.nbfc.itsm.domain.EmployeeRepository;
import com.nbfc.itsm.domain.EmployeeRoleAssignment;
import com.nbfc.itsm.domain.EmployeeRoleAssignmentRepository;
import com.nbfc.itsm.domain.RoleRepository;
import com.nbfc.itsm.domain.SubCategory;
import com.nbfc.itsm.domain.SubCategoryRepository;
import com.nbfc.itsm.domain.Ticket;
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
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A Service Request climbs the reporting line: immediate manager, then that manager's manager,
 * up to the HOD, then CISO. Each person sees it in their Approvals list only while it is theirs.
 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
class RequestRoutingTest {

    @Autowired
    private CatalogSeedService catalogSeedService;
    @Autowired
    private TicketService ticketService;
    @Autowired
    private PortalUserService portalUserService;
    @Autowired
    private EmployeeRepository employeeRepository;
    @Autowired
    private RoleRepository roleRepository;
    @Autowired
    private EmployeeRoleAssignmentRepository assignmentRepository;
    @Autowired
    private TicketTypeRepository ticketTypeRepository;
    @Autowired
    private CategoryRepository categoryRepository;
    @Autowired
    private SubCategoryRepository subCategoryRepository;
    @Autowired
    private WorkflowInstanceRepository instanceRepository;
    @Autowired
    private WorkflowInstanceStageRepository instanceStageRepository;

    private Employee requester;
    private Employee teamLead;
    private Employee hod;

    @BeforeEach
    void setUp() {
        catalogSeedService.ensureSeeded();
        hod = employee("E-RT-HOD", "rt.hod", "Routing HOD", null, "EMPLOYEE", "HOD");
        teamLead = employee("E-RT-TL", "rt.lead", "Routing Team Lead", hod, "EMPLOYEE");
        requester = employee("E-RT-REQ", "rt.req", "Routing Requester", teamLead, "EMPLOYEE");
    }

    @AfterEach
    void clear() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void serviceRequestMovesUpTheReportingLine() {
        Ticket sr = ticketService.save(as(requester), serviceRequest());
        assertEquals("Pending Approval", sr.getStatusCode());

        // 1st stop: immediate manager; the step is named after the person, with no "Manager approval" prefix.
        assertEquals(teamLead.getEmployeeId(), current(sr).getResolvedEmployee().getEmployeeId());
        assertEquals("Routing Team Lead", current(sr).getLabel());
        assertTrue(inApprovals(teamLead, sr), "team lead sees it");
        assertFalse(inApprovals(hod, sr), "HOD does not see it yet");

        ticketService.applyAction(as(teamLead), sr.getTicketId(), "APPROVE", "Approved by team lead for access", null);

        // 2nd stop: the manager's manager, who holds the HOD role, so the hierarchy ends here.
        assertEquals(hod.getEmployeeId(), current(sr).getResolvedEmployee().getEmployeeId());
        assertTrue(inApprovals(hod, sr));
        assertFalse(inApprovals(teamLead, sr), "gone from the team lead's list after approving");

        ticketService.applyAction(as(hod), sr.getTicketId(), "APPROVE", "Approved by HOD, business need confirmed", null);

        // Then the named CISO stage from the template.
        WorkflowInstanceStage next = current(sr);
        assertEquals("NAMED_ROLE", next.getActorStrategy());
        assertEquals("CISO", next.getResolvedRole().getCode());
    }

    @Test
    void requesterWithoutManagerGetsAClearMessage() {
        Employee orphan = employee("E-RT-ORP", "rt.orphan", "Routing Orphan", null, "EMPLOYEE");
        ItsmException ex = assertThrows(ItsmException.class, () -> ticketService.save(as(orphan), serviceRequest()));
        assertEquals("NO_MANAGER_CHAIN", ex.getCode());
        assertTrue(ex.getMessage().contains("no manager is set for you"), ex.getMessage());
    }

    @Test
    void hodSetOutsideTheChainGoesManagerThenHod() {
        // Like 50058036: manager Pankaj, whose AD chain continues to Bittu and Rohit; HOD set to Anil,
        // who is not in that chain. Expected: Pankaj -> Anil, then the CISO stage.
        Employee rohit = employee("E-RT-ROH", "rt.rohit", "Rohit", null, "EMPLOYEE");
        Employee bittu = employee("E-RT-BIT", "rt.bittu", "Bittu", rohit, "EMPLOYEE");
        Employee pankaj = employee("E-RT-PAN", "rt.pankaj", "Pankaj", bittu, "EMPLOYEE");
        Employee anil = employee("E-RT-ANI", "rt.anil", "Anil", null, "EMPLOYEE", "HOD", "CISO");
        Employee me = employee("E-RT-ME", "rt.me", "Suksham", pankaj, "EMPLOYEE");
        me.setHod(anil);
        employeeRepository.save(me);

        Ticket sr = ticketService.save(as(me), serviceRequest());
        assertEquals(java.util.Arrays.asList("Pankaj", "Anil"), hierarchyApprovers(sr));
        assertEquals(pankaj.getEmployeeId(), current(sr).getResolvedEmployee().getEmployeeId());

        ticketService.applyAction(as(pankaj), sr.getTicketId(), "APPROVE", "Approved by the manager for access", null);
        assertEquals(anil.getEmployeeId(), current(sr).getResolvedEmployee().getEmployeeId(), "straight to the HOD");

        ticketService.applyAction(as(anil), sr.getTicketId(), "APPROVE", "Approved by the HOD, business need ok", null);
        assertEquals("CISO", current(sr).getResolvedRole().getCode(), "then the CISO stage");
    }

    @Test
    void hodSetHigherInTheChainStopsThere() {
        Employee top = employee("E-RT-TOP", "rt.top", "Top", null, "EMPLOYEE");
        Employee rohit = employee("E-RT-RO2", "rt.rohit2", "Rohit", top, "EMPLOYEE");
        Employee bittu = employee("E-RT-BI2", "rt.bittu2", "Bittu", rohit, "EMPLOYEE");
        Employee pankaj = employee("E-RT-PA2", "rt.pankaj2", "Pankaj", bittu, "EMPLOYEE");
        Employee me = employee("E-RT-ME2", "rt.me2", "Requester", pankaj, "EMPLOYEE");
        me.setHod(rohit);
        employeeRepository.save(me);

        Ticket sr = ticketService.save(as(me), serviceRequest());
        assertEquals(java.util.Arrays.asList("Pankaj", "Bittu", "Rohit"), hierarchyApprovers(sr), "stops at the HOD, not Top");
    }

    @Test
    void hodSameAsManagerIsOneStep() {
        Employee boss = employee("E-RT-BOS", "rt.boss", "Boss", null, "EMPLOYEE");
        Employee me = employee("E-RT-ME3", "rt.me3", "Requester", boss, "EMPLOYEE");
        me.setHod(boss);
        employeeRepository.save(me);

        Ticket sr = ticketService.save(as(me), serviceRequest());
        assertEquals(java.util.Collections.singletonList("Boss"), hierarchyApprovers(sr));
    }

    // ------------------------------------------------------------------ helpers

    /** Names on the manager-hierarchy steps, in order. */
    private java.util.List<String> hierarchyApprovers(Ticket t) {
        WorkflowInstance inst = instanceRepository.findByTicketId(t.getTicketId()).orElseThrow(IllegalStateException::new);
        java.util.List<String> names = new java.util.ArrayList<String>();
        for (WorkflowInstanceStage s : instanceStageRepository.findByWorkflowInstanceOrderByStageOrderAsc(inst)) {
            if ("LDAP_MANAGER".equals(s.getActorStrategy()) && s.getResolvedEmployee() != null) {
                names.add(s.getResolvedEmployee().getDisplayName());
            }
        }
        return names;
    }

    private boolean inApprovals(Employee who, Ticket t) {
        for (Ticket x : ticketService.approvalsFor(as(who))) {
            if (x.getTicketId().equals(t.getTicketId())) {
                return true;
            }
        }
        return false;
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

    private TicketForm serviceRequest() {
        Category network = categoryRepository.findByCode("NETWORK").orElseThrow(IllegalStateException::new);
        SubCategory sub = subCategoryRepository.findByCategoryAndActiveTrueOrderBySortOrderAsc(network).get(0);
        TicketForm f = new TicketForm();
        f.setTicketTypeId(ticketTypeRepository.findByCode("SERVICE_REQUEST").orElseThrow(IllegalStateException::new)
                .getTicketTypeId());
        f.setCategoryId(network.getCategoryId());
        f.setSubCategoryId(sub.getSubCategoryId());
        f.setSubject("VPN access for remote work");
        f.setDescription("Need VPN access to work from the branch office.");
        f.setIntent("submit");
        return f;
    }

    private ItsmUserPrincipal as(Employee e) {
        ItsmUserPrincipal p = portalUserService.toPrincipal(employeeRepository.findById(e.getEmployeeId()).get());
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(p, null, p.getAuthorities()));
        return p;
    }

    private Employee employee(String no, String sam, String name, Employee manager, String... roles) {
        Employee e = new Employee();
        e.setEmployeeNo(no);
        e.setSamAccountName(sam);
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
