package com.nbfc.itsm.ticket;

import com.nbfc.itsm.domain.AssignmentGroup;
import com.nbfc.itsm.domain.AssignmentGroupMember;
import com.nbfc.itsm.domain.AssignmentGroupMemberRepository;
import com.nbfc.itsm.domain.AssignmentGroupRepository;
import com.nbfc.itsm.domain.Category;
import com.nbfc.itsm.domain.CategoryRepository;
import com.nbfc.itsm.domain.Department;
import com.nbfc.itsm.domain.DepartmentRepository;
import com.nbfc.itsm.domain.Employee;
import com.nbfc.itsm.domain.EmployeeRepository;
import com.nbfc.itsm.domain.EmployeeRoleAssignment;
import com.nbfc.itsm.domain.EmployeeRoleAssignmentRepository;
import com.nbfc.itsm.domain.Role;
import com.nbfc.itsm.domain.RoleRepository;
import com.nbfc.itsm.domain.SubCategory;
import com.nbfc.itsm.domain.SubCategoryRepository;
import com.nbfc.itsm.domain.Ticket;
import com.nbfc.itsm.domain.TicketRepository;
import com.nbfc.itsm.domain.TicketSla;
import com.nbfc.itsm.domain.TicketSlaRepository;
import com.nbfc.itsm.domain.TicketType;
import com.nbfc.itsm.domain.TicketTypeRepository;
import com.nbfc.itsm.domain.WorkflowInstance;
import com.nbfc.itsm.domain.WorkflowInstanceRepository;
import com.nbfc.itsm.domain.WorkflowInstanceStage;
import com.nbfc.itsm.exception.ItsmException;
import com.nbfc.itsm.identity.PortalUserService;
import com.nbfc.itsm.security.ItsmUserPrincipal;
import com.nbfc.itsm.seed.CatalogSeedService;
import com.nbfc.itsm.workflow.WorkflowEngine;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.util.Arrays;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class TicketLifecycleIntegrationTest {

    @Autowired
    private CatalogSeedService catalogSeedService;
    @Autowired
    private TicketService ticketService;
    @Autowired
    private TicketRepository ticketRepository;
    @Autowired
    private TicketSlaRepository ticketSlaRepository;
    @Autowired
    private WorkflowEngine workflowEngine;
    @Autowired
    private WorkflowInstanceRepository instanceRepository;
    @Autowired
    private EmployeeRepository employeeRepository;
    @Autowired
    private RoleRepository roleRepository;
    @Autowired
    private EmployeeRoleAssignmentRepository assignmentRepository;
    @Autowired
    private AssignmentGroupRepository groupRepository;
    @Autowired
    private AssignmentGroupMemberRepository groupMemberRepository;
    @Autowired
    private TicketTypeRepository ticketTypeRepository;
    @Autowired
    private CategoryRepository categoryRepository;
    @Autowired
    private SubCategoryRepository subCategoryRepository;
    @Autowired
    private DepartmentRepository departmentRepository;
    @Autowired
    private PortalUserService portalUserService;

    private Employee actor;
    private ItsmUserPrincipal principal;

    @BeforeEach
    void seed() {
        catalogSeedService.ensureSeeded();
        String tag = UUID.randomUUID().toString().substring(0, 8);
        Department it = departmentRepository.findByCode("IT").orElse(null);
        actor = new Employee();
        actor.setEmployeeNo("E-LIFE-" + tag);
        actor.setSamAccountName("life." + tag);
        actor.setDisplayName("Lifecycle " + tag);
        actor.setEmail("life." + tag + "@localhost");
        actor.setDepartment(it);
        actor.setPortalActive(true);
        actor = employeeRepository.save(actor);
        actor.setHod(actor);
        actor.setManager(actor);
        actor = employeeRepository.save(actor);
        grant(actor, "SYSTEM_ADMINISTRATOR");
        grant(actor, "CISO");
        grant(actor, "IT_SERVICE_DESK");
        grant(actor, "IT_IMPLEMENTOR");
        addGroup(actor, "IT_SERVICE_DESK");
        addGroup(actor, "IT_IMPLEMENTORS");
        principal = portalUserService.toPrincipal(actor);
    }

    @Test
    void incidentHappyPathClosesWithSlaClock() {
        Ticket incident = ticketService.save(principal, form("Incident", "Hardware", "Laptop Issue"));
        assertNotNull(incident.getPublicNumber());
        assertTrue(incident.getPublicNumber().startsWith("ITSM-"));
        TicketSla sla = ticketSlaRepository.findByTicket(incident).orElse(null);
        assertNotNull(sla);
        assertEquals("ASSIGNMENT", current(incident).getStageType());

        incident = ticketService.applyAction(principal, incident.getTicketId(), "ASSIGN", null, actor.getEmployeeId());
        assertEquals("FULFILMENT", current(incident).getStageType());
        incident = ticketService.applyAction(principal, incident.getTicketId(), "START", null, null);
        assertEquals("In Progress", incident.getStatusCode());
        incident = ticketService.applyAction(principal, incident.getTicketId(), "RESOLVE", null, null);
        assertEquals("Closed", incident.getStatusCode());
        TicketSla after = ticketSlaRepository.findByTicket(incident).orElse(null);
        assertNotNull(after.getResolvedUtc());
    }

    @Test
    void serviceRequestHappyPathSkipsHierarchyForHodOfSelf() {
        Ticket sr = ticketService.save(principal, form("Service Request", "Network", "VPN Access"));
        WorkflowInstanceStage first = current(sr);
        assertEquals("APPROVAL", first.getStageType());
        assertEquals("NAMED_ROLE", first.getActorStrategy());
        sr = ticketService.applyAction(principal, sr.getTicketId(), "APPROVE",
                "CISO approved the VPN request.", null);
        assertEquals("FULFILMENT", current(sr).getStageType());
        sr = ticketService.applyAction(principal, sr.getTicketId(), "START", null, null);
        sr = ticketService.applyAction(principal, sr.getTicketId(), "RESOLVE", null, null);
        assertEquals("Closed", sr.getStatusCode());
    }

    @Test
    void draftDoesNotAllocateWorkflowUntilSubmit() {
        TicketForm draftForm = form("Incident", "Hardware", "Desktop Issue");
        draftForm.setIntent("draft");
        Ticket draft = ticketService.save(principal, draftForm);
        assertEquals("Draft", draft.getStatusCode());
        assertTrue(draft.getPublicNumber().startsWith("DRAFT-"));
        assertTrue(!ticketSlaRepository.findByTicket(draft).isPresent());
        Ticket submitted = ticketService.submitDraft(principal, draft.getTicketId(), form("Incident", "Hardware", "Desktop Issue"));
        assertTrue(submitted.getPublicNumber().startsWith("ITSM-"));
        assertNotNull(submitted.getWorkflowInstanceId());
    }

    @Test
    void blankSubjectRejected() {
        TicketForm bad = form("Incident", "Hardware", "Laptop Issue");
        bad.setSubject("  ");
        final TicketForm invalid = bad;
        ItsmException ex = assertThrows(ItsmException.class, () -> ticketService.save(principal, invalid));
        assertEquals("TICKET_INVALID", ex.getCode());
    }

    @Test
    void mismatchedSubcategoryRejected() {
        TicketForm bad = form("Incident", "Hardware", "Laptop Issue");
        SubCategory vpn = null;
        for (SubCategory s : subCategoryRepository.findByActiveTrueOrderBySortOrderAsc()) {
            if ("VPN Access".equals(s.getName())) {
                vpn = s;
            }
        }
        bad.setSubCategoryId(vpn.getSubCategoryId());
        final TicketForm invalid = bad;
        ItsmException ex = assertThrows(ItsmException.class, () -> ticketService.save(principal, invalid));
        assertEquals("LOOKUP", ex.getCode());
    }

    @Test
    void openCountAndMineQueryAreServerSide() {
        ticketService.save(principal, form("Incident", "Hardware", "Laptop Issue"));
        ticketService.save(principal, form("Incident", "Hardware", "Desktop Issue"));
        long open = ticketRepository.countByStatusCodeNotIn(Arrays.asList("Closed", "Rejected"));
        assertTrue(open >= 2);
        Page<Ticket> page = ticketRepository.findMine(actor, PageRequest.of(0, 1));
        assertEquals(1, page.getContent().size());
        assertTrue(page.getTotalElements() >= 2);
    }

    private WorkflowInstanceStage current(Ticket ticket) {
        WorkflowInstance instance = instanceRepository.findByTicketId(ticket.getTicketId()).orElse(null);
        for (WorkflowInstanceStage s : workflowEngine.loadStages(instance)) {
            if ("Current".equals(s.getStatusCode())) {
                return s;
            }
        }
        throw new IllegalStateException("no current stage");
    }

    private TicketForm form(String typeName, String categoryName, String subName) {
        TicketType type = null;
        for (TicketType t : ticketTypeRepository.findByActiveTrueOrderBySortOrderAsc()) {
            if (typeName.equals(t.getName())) {
                type = t;
            }
        }
        Category cat = null;
        for (Category c : categoryRepository.findByActiveTrueOrderBySortOrderAsc()) {
            if (categoryName.equals(c.getName())) {
                cat = c;
            }
        }
        SubCategory sub = null;
        for (SubCategory s : subCategoryRepository.findByActiveTrueOrderBySortOrderAsc()) {
            if (subName.equals(s.getName())) {
                sub = s;
            }
        }
        TicketForm form = new TicketForm();
        form.setSerialMode("NA");
        form.setTicketTypeId(type.getTicketTypeId());
        form.setCategoryId(cat.getCategoryId());
        form.setSubCategoryId(sub.getSubCategoryId());
        form.setSubject(typeName + " " + subName);
        form.setDescription("Lifecycle description for " + typeName);
        form.setPriorityCode("Medium");
        form.setImpactCode("Individual");
        form.setUrgencyCode("Medium");
        form.setConfidentialityCode("Normal");
        form.setIntent("submit");
        return form;
    }

    private void grant(Employee emp, String roleCode) {
        Role role = roleRepository.findByCode(roleCode).orElseThrow(() -> new IllegalStateException(roleCode));
        if (assignmentRepository.findByEmployeeAndRole(emp, role).isPresent()) {
            return;
        }
        EmployeeRoleAssignment row = new EmployeeRoleAssignment();
        row.setEmployee(emp);
        row.setRole(role);
        assignmentRepository.save(row);
    }

    private void addGroup(Employee emp, String code) {
        AssignmentGroup g = groupRepository.findByCode(code).orElseThrow(() -> new IllegalStateException(code));
        if (groupMemberRepository.existsByAssignmentGroupAndEmployee(g, emp)) {
            return;
        }
        AssignmentGroupMember m = new AssignmentGroupMember();
        m.setAssignmentGroup(g);
        m.setEmployee(emp);
        groupMemberRepository.save(m);
    }
}
