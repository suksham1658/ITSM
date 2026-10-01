package com.nbfc.itsm.admin;

import com.nbfc.itsm.admin.WorkflowDesignService.StageForm;
import com.nbfc.itsm.domain.AssignmentGroupRepository;
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
import com.nbfc.itsm.domain.WorkflowDefinition;
import com.nbfc.itsm.domain.WorkflowDefinitionRepository;
import com.nbfc.itsm.domain.WorkflowInstance;
import com.nbfc.itsm.domain.WorkflowInstanceRepository;
import com.nbfc.itsm.domain.WorkflowInstanceStage;
import com.nbfc.itsm.domain.WorkflowInstanceStageRepository;
import com.nbfc.itsm.domain.WorkflowRule;
import com.nbfc.itsm.domain.WorkflowRuleRepository;
import com.nbfc.itsm.domain.WorkflowStage;
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
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.hamcrest.Matchers.allOf;
import static org.hamcrest.Matchers.containsString;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Workflow designer: draft, change stages, publish; running tickets keep their version. */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class WorkflowDesignServiceTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private WorkflowDesignService design;
    @Autowired private CatalogSeedService catalogSeedService;
    @Autowired private TicketService ticketService;
    @Autowired private PortalUserService portalUserService;
    @Autowired private EmployeeRepository employeeRepository;
    @Autowired private RoleRepository roleRepository;
    @Autowired private EmployeeRoleAssignmentRepository assignmentRepository;
    @Autowired private AssignmentGroupRepository groupRepository;
    @Autowired private WorkflowDefinitionRepository definitionRepository;
    @Autowired private WorkflowRuleRepository ruleRepository;
    @Autowired private WorkflowInstanceRepository instanceRepository;
    @Autowired private WorkflowInstanceStageRepository instanceStageRepository;
    @Autowired private TicketTypeRepository ticketTypeRepository;
    @Autowired private CategoryRepository categoryRepository;
    @Autowired private SubCategoryRepository subCategoryRepository;

    private Employee admin;
    private Employee requester;
    private WorkflowDefinition sr;

    @BeforeEach
    void setUp() {
        catalogSeedService.ensureSeeded();
        admin = employee("E-WD-SA", "wd.admin", "Workflow Admin", null, "EMPLOYEE", "SYSTEM_ADMINISTRATOR");
        Employee hod = employee("E-WD-HOD", "wd.hod", "WD HOD", null, "EMPLOYEE", "HOD");
        requester = employee("E-WD-REQ", "wd.req", "WD Requester", hod, "EMPLOYEE");
        sr = definitionRepository.findByCodeAndVersionNo("SR_CHAIN_TO_HOD_CISO_IMPL", 1).orElseThrow(IllegalStateException::new);
    }

    @AfterEach
    void clear() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void serviceDeskAddedBeforeImplementorForNewTicketsOnly() {
        Ticket before = raiseServiceRequest();

        as(admin);
        WorkflowDefinition draft = design.startDraft(sr.getWorkflowDefinitionId());
        assertEquals("Draft", draft.getStatusCode());
        assertEquals(2, draft.getVersionNo());
        assertEquals(draft.getWorkflowDefinitionId(), design.startDraft(sr.getWorkflowDefinitionId()).getWorkflowDefinitionId(),
                "Edit again opens the same draft");

        StageForm desk = new StageForm();
        desk.setLabel("IT Service Desk");
        desk.setStageType("ASSIGNMENT");
        desk.setActorStrategy("SERVICE_DESK");
        desk.setGroupId(groupRepository.findByCode("IT_SERVICE_DESK").get().getAssignmentGroupId());
        WorkflowStage added = design.addStage(draft.getWorkflowDefinitionId(), desk);
        assertEquals(Arrays.asList("Reporting hierarchy through HOD", "CISO approval", "Implementor",
                "Requester confirmation", "IT Service Desk", "Closed"), labels(draft), "added before Closed");

        design.moveStage(draft.getWorkflowDefinitionId(), added.getWorkflowStageId(), true);
        design.moveStage(draft.getWorkflowDefinitionId(), added.getWorkflowStageId(), true);
        assertEquals(Arrays.asList("Reporting hierarchy through HOD", "CISO approval", "IT Service Desk", "Implementor",
                "Requester confirmation", "Closed"), labels(draft));
        assertTrue(design.problems(draft.getWorkflowDefinitionId()).isEmpty(), design.problems(draft.getWorkflowDefinitionId()).toString());

        design.publish(draft.getWorkflowDefinitionId());
        assertEquals("Active", definitionRepository.findById(draft.getWorkflowDefinitionId()).get().getStatusCode());
        assertEquals("Retired", definitionRepository.findById(sr.getWorkflowDefinitionId()).get().getStatusCode());
        for (WorkflowRule r : ruleRepository.findAll()) {
            assertTrue(!r.getWorkflowDefinition().getWorkflowDefinitionId().equals(sr.getWorkflowDefinitionId()),
                    "no rule left on the retired version: " + r.getName());
        }

        Ticket after = raiseServiceRequest();
        assertEquals(Arrays.asList("APPROVAL:LDAP_MANAGER", "APPROVAL:NAMED_ROLE", "ASSIGNMENT:SERVICE_DESK",
                "FULFILMENT:IMPLEMENTOR", "CONFIRMATION:REQUESTER", "CLOSURE:SYSTEM"), shape(after), "new ticket gets the desk");
        assertEquals(Arrays.asList("APPROVAL:LDAP_MANAGER", "APPROVAL:NAMED_ROLE", "FULFILMENT:IMPLEMENTOR",
                "CONFIRMATION:REQUESTER", "CLOSURE:SYSTEM"), shape(before), "ticket raised before keeps its flow");
    }

    @Test
    void assignmentWithoutFulfilmentAfterItCannotBePublished() {
        as(admin);
        WorkflowDefinition draft = design.createWorkflow("wd test flow", "WD test flow", null, null);
        assertEquals("WD_TEST_FLOW", draft.getCode());
        StageForm desk = new StageForm();
        desk.setLabel("Desk");
        desk.setStageType("ASSIGNMENT");
        desk.setActorStrategy("SERVICE_DESK");
        desk.setGroupId(groupRepository.findByCode("IT_SERVICE_DESK").get().getAssignmentGroupId());
        design.addStage(draft.getWorkflowDefinitionId(), desk);

        assertTrue(design.problems(draft.getWorkflowDefinitionId()).get(0).contains("Fulfilment stage must come after it"));
        ItsmException ex = assertThrows(ItsmException.class, () -> design.publish(draft.getWorkflowDefinitionId()));
        assertTrue(ex.getMessage().startsWith("Cannot publish yet."), ex.getMessage());

        StageForm roleless = new StageForm();
        roleless.setLabel("Approver");
        roleless.setStageType("APPROVAL");
        roleless.setActorStrategy("NAMED_ROLE");
        assertThrows(ItsmException.class, () -> design.addStage(draft.getWorkflowDefinitionId(), roleless), "role required");

        StageForm wrong = new StageForm();
        wrong.setLabel("Oops");
        wrong.setStageType("FULFILMENT");
        wrong.setActorStrategy("LDAP_MANAGER");
        assertThrows(ItsmException.class, () -> design.addStage(draft.getWorkflowDefinitionId(), wrong), "strategy must fit type");

        design.discard(draft.getWorkflowDefinitionId());
        assertTrue(definitionRepository.findByCodeOrderByVersionNoDesc("WD_TEST_FLOW").isEmpty());
    }

    @Test
    void activeWorkflowsAreNeverEditedInPlace() {
        as(admin);
        StageForm f = new StageForm();
        f.setLabel("Extra");
        f.setStageType("CONFIRMATION");
        ItsmException ex = assertThrows(ItsmException.class, () -> design.addStage(sr.getWorkflowDefinitionId(), f));
        assertEquals("WF_NOT_DRAFT", ex.getCode());
    }

    @Test
    void rulesCanBeAddedRetargetedAndProtected() {
        as(admin);
        WorkflowDefinition noCiso = definitionRepository.findByCodeAndVersionNo("SR_CHAIN_TO_HOD_IMPL", 1).get();
        ItsmException clash = assertThrows(ItsmException.class, () -> design.saveRule(null, "Clashing rule", 40, "Active",
                "{\"department\":[\"Information Technology\"]}", noCiso.getWorkflowDefinitionId()));
        assertTrue(clash.getMessage().contains("priority 40"), clash.getMessage());

        WorkflowRule it = design.saveRule(null, "IT requests without CISO", 35, "Active",
                "{\"ticket_type\":[\"Service Request\"],\"department\":[\"Information Technology\"]}", noCiso.getWorkflowDefinitionId());
        assertEquals("SR_CHAIN_TO_HOD_IMPL", it.getWorkflowDefinition().getCode());
        assertThrows(ItsmException.class, () -> design.saveRule(it.getWorkflowRuleId(), "Bad json", 35, "Active", "[1,2]",
                noCiso.getWorkflowDefinitionId()));
        design.deleteRule(it.getWorkflowRuleId());

        Ticket t = raiseServiceRequest();
        WorkflowRule used = instanceRepository.findByTicketId(t.getTicketId()).get().getWorkflowRule();
        as(admin);
        ItsmException inUse = assertThrows(ItsmException.class, () -> design.deleteRule(used.getWorkflowRuleId()));
        assertEquals("RULE_IN_USE", inUse.getCode());
    }

    @Test
    void onlyTheSystemAdministratorCanChangeWorkflows() {
        as(requester);
        assertThrows(AccessDeniedException.class, () -> design.startDraft(sr.getWorkflowDefinitionId()));
        assertThrows(AccessDeniedException.class, () -> design.saveRule(null, "x rule", 5, "Active", "{}", sr.getWorkflowDefinitionId()));
    }

    @Test
    void pagesRenderForTheAdministrator() throws Exception {
        UsernamePasswordAuthenticationToken token = token(admin);
        mockMvc.perform(get("/admin/workflow").with(authentication(token)))
                .andExpect(status().isOk())
                .andExpect(content().string(allOf(containsString("SR_CHAIN_TO_HOD_CISO_IMPL"), containsString("Add rule"),
                        containsString("New workflow"))));
        as(admin);
        WorkflowDefinition draft = design.startDraft(sr.getWorkflowDefinitionId());
        mockMvc.perform(get("/admin/workflow/{id}", draft.getWorkflowDefinitionId()).with(authentication(token)))
                .andExpect(status().isOk())
                .andExpect(content().string(allOf(containsString("Add a stage"), containsString("Publish"),
                        containsString("data-types=\"APPROVAL\""))));
        mockMvc.perform(get("/admin/workflow/{id}", sr.getWorkflowDefinitionId()).with(authentication(token)))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Continue editing draft")));
    }

    // ------------------------------------------------------------------ helpers

    private List<String> labels(WorkflowDefinition d) {
        List<String> out = new ArrayList<String>();
        for (WorkflowStage s : design.stages(d.getWorkflowDefinitionId())) {
            out.add(s.getLabel());
        }
        return out;
    }

    private List<String> shape(Ticket t) {
        WorkflowInstance inst = instanceRepository.findByTicketId(t.getTicketId()).orElseThrow(IllegalStateException::new);
        List<String> out = new ArrayList<String>();
        for (WorkflowInstanceStage s : instanceStageRepository.findByWorkflowInstanceOrderByStageOrderAsc(inst)) {
            out.add(s.getStageType() + ":" + s.getActorStrategy());
        }
        return out;
    }

    private Ticket raiseServiceRequest() {
        as(requester);
        Category network = categoryRepository.findByCode("NETWORK").orElseThrow(IllegalStateException::new);
        SubCategory sub = subCategoryRepository.findByCategoryAndActiveTrueOrderBySortOrderAsc(network).get(0);
        TicketForm f = new TicketForm();
        f.setSerialMode("NA");
        f.setTicketTypeId(ticketTypeRepository.findByCode("SERVICE_REQUEST").orElseThrow(IllegalStateException::new).getTicketTypeId());
        f.setCategoryId(network.getCategoryId());
        f.setSubCategoryId(sub.getSubCategoryId());
        f.setSubject("VPN access for remote work");
        f.setDescription("Need VPN access to work from the branch office.");
        f.setIntent("submit");
        return ticketService.save(principal(requester), f);
    }

    private void as(Employee e) {
        SecurityContextHolder.getContext().setAuthentication(token(e));
    }

    private ItsmUserPrincipal principal(Employee e) {
        return portalUserService.toPrincipal(employeeRepository.findById(e.getEmployeeId()).get());
    }

    private UsernamePasswordAuthenticationToken token(Employee e) {
        ItsmUserPrincipal p = principal(e);
        return new UsernamePasswordAuthenticationToken(p, null, p.getAuthorities());
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
