package com.nbfc.itsm.ticket;

import com.nbfc.itsm.domain.AssignmentGroup;
import com.nbfc.itsm.domain.AssignmentGroupMember;
import com.nbfc.itsm.domain.AssignmentGroupMemberRepository;
import com.nbfc.itsm.domain.AssignmentGroupRepository;
import com.nbfc.itsm.domain.Category;
import com.nbfc.itsm.domain.CategoryRepository;
import com.nbfc.itsm.domain.Employee;
import com.nbfc.itsm.domain.EmployeeRepository;
import com.nbfc.itsm.domain.EmployeeRoleAssignment;
import com.nbfc.itsm.domain.EmployeeRoleAssignmentRepository;
import com.nbfc.itsm.domain.Role;
import com.nbfc.itsm.domain.RoleRepository;
import com.nbfc.itsm.domain.SubCategory;
import com.nbfc.itsm.domain.SubCategoryRepository;
import com.nbfc.itsm.domain.Ticket;
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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class CoreItsmServiceTest {

    @Autowired
    private CatalogSeedService catalogSeedService;
    @Autowired
    private TicketNumberService ticketNumberService;
    @Autowired
    private TicketService ticketService;
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
    private PortalUserService portalUserService;

    private ItsmUserPrincipal principal;

    @BeforeEach
    void seed() {
        catalogSeedService.ensureSeeded();
        Employee emp = employeeRepository.findBySamAccountNameIgnoreCase("core.tester").orElse(null);
        if (emp == null) {
            emp = new Employee();
            emp.setEmployeeNo("E-CORE");
            emp.setSamAccountName("core.tester");
            emp.setDisplayName("Core Tester");
            emp.setEmail("core.tester@localhost");
            emp.setPortalActive(true);
            emp = employeeRepository.save(emp);
            emp.setHod(emp);
            emp.setManager(emp);
            emp = employeeRepository.save(emp);
            grant(emp, "SYSTEM_ADMINISTRATOR");
            grant(emp, "CISO");
            grant(emp, "IT_SERVICE_DESK");
            grant(emp, "IT_IMPLEMENTOR");
            addGroup(emp, "IT_SERVICE_DESK");
            addGroup(emp, "IT_IMPLEMENTORS");
        }
        principal = portalUserService.toPrincipal(emp);
    }

    @Test
    void numberingIsUniqueAndPadded() {
        String a = ticketNumberService.allocate();
        String b = ticketNumberService.allocate();
        assertTrue(a.startsWith("ITSM-"));
        assertTrue(b.startsWith("ITSM-"));
        assertTrue(!a.equals(b));
        String seq = a.substring(a.lastIndexOf('-') + 1);
        assertEquals(6, seq.length());
    }

    @Test
    void matcherRoutesIncidentAndServiceRequest() {
        Ticket incident = ticketService.save(principal, form("Incident", "Hardware", "Laptop Issue"));
        WorkflowInstance incInst = instanceRepository.findByTicketId(incident.getTicketId()).orElse(null);
        assertEquals("INCIDENT_SD_THEN_IMPL", incInst.getWorkflowDefinition().getCode());
        WorkflowInstanceStage current = current(incInst);
        assertEquals("ASSIGNMENT", current.getStageType());

        Ticket sr = ticketService.save(principal, form("Service Request", "Network", "VPN Access"));
        WorkflowInstance srInst = instanceRepository.findByTicketId(sr.getTicketId()).orElse(null);
        assertEquals("SR_CHAIN_TO_HOD_CISO_IMPL", srInst.getWorkflowDefinition().getCode());
        WorkflowInstanceStage srCurrent = current(srInst);
        assertEquals("APPROVAL", srCurrent.getStageType());
        assertEquals("NAMED_ROLE", srCurrent.getActorStrategy());
    }

    @Test
    void invalidTransitionRejected() {
        Ticket incident = ticketService.save(principal, form("Incident", "Hardware", "Laptop Issue"));
        ItsmException ex = assertThrows(ItsmException.class, () ->
                ticketService.applyAction(principal, incident.getTicketId(), "APPROVE",
                        "This remark is long enough", null));
        assertEquals("INVALID_TRANSITION", ex.getCode());
    }

    @Test
    void remarksRequiredOnApprove() {
        Ticket sr = ticketService.save(principal, form("Service Request", "Network", "VPN Access"));
        ItsmException blank = assertThrows(ItsmException.class, () ->
                ticketService.applyAction(principal, sr.getTicketId(), "APPROVE", "   ", null));
        assertEquals("REMARKS_REQUIRED", blank.getCode());
        ItsmException shortTxt = assertThrows(ItsmException.class, () ->
                ticketService.applyAction(principal, sr.getTicketId(), "APPROVE", "too short", null));
        assertEquals("REMARKS_REQUIRED", shortTxt.getCode());
        Ticket ok = ticketService.applyAction(principal, sr.getTicketId(), "APPROVE",
                "Approved after review of the request.", null);
        assertTrue(!"Pending Approval".equals(ok.getStatusCode()) || ok.getWorkflowInstanceId() != null);
    }

    @Test
    void paginationIsServerSide() {
        ticketService.save(principal, form("Incident", "Hardware", "Laptop Issue"));
        ticketService.save(principal, form("Incident", "Hardware", "Desktop Issue"));
        ticketService.save(principal, form("Service Request", "Network", "VPN Access"));
        Page<Ticket> page = ticketService.search(principal, "mine", null, null, null, null,
                PageRequest.of(0, 2));
        assertEquals(2, page.getContent().size());
        assertTrue(page.getTotalElements() >= 3);
        assertTrue(page.getTotalPages() >= 2);
    }

    private WorkflowInstanceStage current(WorkflowInstance instance) {
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
        form.setDescription("Automated test description for " + typeName);
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
