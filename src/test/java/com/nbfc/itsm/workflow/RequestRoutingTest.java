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
    void imacRoutesToImacFlowApprovalThenServiceDesk() {
        // IMAC may be raised only by someone the admin granted TICKET_RAISE_IMAC (here via System Administrator).
        Employee imacReq = employee("E-RT-IMAC", "rt.imac", "IMAC Requester", teamLead, "EMPLOYEE", "SYSTEM_ADMINISTRATOR");
        Ticket t = ticketService.save(as(imacReq), imacRequest());
        WorkflowInstance inst = instanceRepository.findByTicketId(t.getTicketId()).orElseThrow(IllegalStateException::new);
        assertEquals("IMAC_FLOW", inst.getWorkflowDefinition().getCode(), "IMAC ticket uses the IMAC workflow");
        assertEquals("Pending Approval", t.getStatusCode());
        assertEquals(teamLead.getEmployeeId(), current(t).getResolvedEmployee().getEmployeeId());

        ticketService.applyAction(as(teamLead), t.getTicketId(), "APPROVE", "Approved the IMAC request for the move", null);
        ticketService.applyAction(as(hod), t.getTicketId(), "APPROVE", "HOD approves this IMAC request, ok", null);

        // Next is IT Service Desk (assignment), not a CISO stage.
        WorkflowInstanceStage next = current(t);
        assertEquals("ASSIGNMENT", next.getStageType());
        assertEquals("SERVICE_DESK", next.getActorStrategy());
    }

    @Test
    void handledScopeListsTicketsIAmAnActorOn() {
        Employee imacReq = employee("E-RT-IMAC3", "rt.imac3", "IMAC Requester 3", teamLead, "EMPLOYEE", "SYSTEM_ADMINISTRATOR");
        Ticket t = ticketService.save(as(imacReq), imacRequest());
        // teamLead is the first hierarchy approver (routed to him); approving keeps him the resolved actor.
        ticketService.applyAction(as(teamLead), t.getTicketId(), "APPROVE", "Approved by team lead for the imac", null);
        org.springframework.data.domain.Page<Ticket> handled = ticketService.search(as(teamLead), "handled",
                null, null, null, null, org.springframework.data.domain.PageRequest.of(0, 20));
        boolean found = false;
        for (Ticket x : handled.getContent()) {
            if (x.getTicketId().equals(t.getTicketId())) {
                found = true;
            }
        }
        assertTrue(found, "an approver sees the ticket in My Handled, regardless of status");
    }

    @Autowired
    private com.nbfc.itsm.domain.LocationRepository locationRepository;
    @Autowired
    private com.nbfc.itsm.admin.LocationService locationService;

    @Test
    void imacFormAllocatesIncrementingHostnamesAndKeepsThem() {
        com.nbfc.itsm.domain.Location kol = new com.nbfc.itsm.domain.Location();
        kol.setName("Kolkata"); kol.setAddress("Addr K"); kol.setActive(true);
        kol = locationRepository.save(kol);
        com.nbfc.itsm.domain.Location bhu = new com.nbfc.itsm.domain.Location();
        bhu.setName("Bhubaneswar"); bhu.setAddress("Addr B"); bhu.setActive(true);
        bhu = locationRepository.save(bhu);

        // The form's /locations/{id}/info endpoint allocates on each selection:
        String h1 = locationService.allocateHostname(kol.getName());
        String h2 = locationService.allocateHostname(bhu.getName());
        assertEquals("AUTH-KOL-0000001", h1);
        assertEquals("AUTH-BHU-0000002", h2, "second selection gets the next number");

        // Submitting with the allocated hostname keeps that number (not re-numbered).
        Employee r = employee("E-RT-H2", "rt.h2", "Req H2", teamLead, "EMPLOYEE", "SYSTEM_ADMINISTRATOR");
        TicketForm f = imacRequest();
        f.setImacLocationId(bhu.getLocationId());
        f.setImacHostname(h2);
        Ticket t = ticketService.save(as(r), f);
        assertEquals("AUTH-BHU-0000002", ticketService.imacDetailFor(t.getTicketId()).getHostname());
    }

    @Test
    void imacLocationDrivesAddressAndGeneratedHostname() {
        com.nbfc.itsm.domain.Location loc = new com.nbfc.itsm.domain.Location();
        loc.setName("Mumbai");
        loc.setAddress("Plot 1, Andheri East, Mumbai 400069");
        loc.setActive(true);
        loc = locationRepository.save(loc);

        Employee imacReq = employee("E-RT-IMAC4", "rt.imac4", "IMAC Requester 4", teamLead, "EMPLOYEE", "SYSTEM_ADMINISTRATOR");
        TicketForm f = imacRequest();
        f.setImacLocationId(loc.getLocationId());
        Ticket t = ticketService.save(as(imacReq), f);

        com.nbfc.itsm.domain.ImacDetail d = ticketService.imacDetailFor(t.getTicketId());
        assertEquals("Mumbai", d.getLocation());
        assertEquals("Plot 1, Andheri East, Mumbai 400069", d.getOfficeAddress(), "address mapped from the location");
        assertEquals("AUTH-MUM-0000001", d.getHostname(), "hostname AUTH-<loc3>-NNNNNNN, generated non-editable");
    }

    @Test
    void imacHostnameNumberIncrementsAcrossTickets() {
        com.nbfc.itsm.domain.Location mum = new com.nbfc.itsm.domain.Location();
        mum.setName("Mumbai"); mum.setAddress("Addr M"); mum.setActive(true);
        mum = locationRepository.save(mum);
        com.nbfc.itsm.domain.Location del = new com.nbfc.itsm.domain.Location();
        del.setName("Delhi"); del.setAddress("Addr D"); del.setActive(true);
        del = locationRepository.save(del);

        Employee r1 = employee("E-RT-H1", "rt.h1", "Req H1", teamLead, "EMPLOYEE", "SYSTEM_ADMINISTRATOR");
        TicketForm f1 = imacRequest(); f1.setImacLocationId(mum.getLocationId());
        Ticket t1 = ticketService.save(as(r1), f1);
        TicketForm f2 = imacRequest(); f2.setImacLocationId(del.getLocationId());
        Ticket t2 = ticketService.save(as(r1), f2);

        assertEquals("AUTH-MUM-0000001", ticketService.imacDetailFor(t1.getTicketId()).getHostname());
        assertEquals("AUTH-DEL-0000002", ticketService.imacDetailFor(t2.getTicketId()).getHostname(),
                "the running number auto-increments across tickets");
    }

    @Test
    void imacCannotBeRaisedWithoutThePermission() {
        // requester is a plain EMPLOYEE without TICKET_RAISE_IMAC.
        assertThrows(org.springframework.security.access.AccessDeniedException.class,
                () -> ticketService.save(as(requester), imacRequest()));
    }

    @Test
    void imacDetailsAreSavedAndVisible() {
        Employee imacReq = employee("E-RT-IMAC2", "rt.imac2", "IMAC Requester 2", teamLead, "EMPLOYEE", "SYSTEM_ADMINISTRATOR");
        Ticket t = ticketService.save(as(imacReq), imacRequest());
        com.nbfc.itsm.domain.ImacDetail d = ticketService.imacDetailFor(t.getTicketId());
        assertEquals("rt.imac2 user", d.getUsername());
        assertEquals("HOST-123", d.getHostname());
        assertEquals("16 GB", d.getRam());
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
        f.setSerialMode("NA");
        f.setTicketTypeId(ticketTypeRepository.findByCode("SERVICE_REQUEST").orElseThrow(IllegalStateException::new)
                .getTicketTypeId());
        f.setCategoryId(network.getCategoryId());
        f.setSubCategoryId(sub.getSubCategoryId());
        f.setSubject("VPN access for remote work");
        f.setDescription("Need VPN access to work from the branch office.");
        f.setIntent("submit");
        return f;
    }

    private TicketForm imacRequest() {
        Category imac = categoryRepository.findByCode("IMAC").orElseThrow(IllegalStateException::new);
        SubCategory sub = subCategoryRepository.findByCategoryAndActiveTrueOrderBySortOrderAsc(imac).get(0);
        TicketForm f = new TicketForm();
        f.setSerialMode("NA");
        f.setTicketTypeId(ticketTypeRepository.findByCode("IMAC").orElseThrow(IllegalStateException::new).getTicketTypeId());
        f.setCategoryId(imac.getCategoryId());
        f.setSubCategoryId(sub.getSubCategoryId());
        f.setSubject("Move workstation to the new floor");
        f.setDescription("Please move my desktop and monitor to the 3rd floor seat.");
        f.setImacUsername("rt.imac2 user");
        f.setImacSapId("SAP-9001");
        f.setImacHostname("HOST-123");
        f.setImacRam("16 GB");
        f.setImacMake("Dell");
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
