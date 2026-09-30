package com.nbfc.itsm.web;

import com.nbfc.itsm.domain.Category;
import com.nbfc.itsm.domain.CategoryRepository;
import com.nbfc.itsm.domain.ConfigChangeRequest;
import com.nbfc.itsm.domain.ConfigChangeRequestRepository;
import com.nbfc.itsm.domain.Employee;
import com.nbfc.itsm.domain.EmployeeRepository;
import com.nbfc.itsm.domain.EmployeeRoleAssignment;
import com.nbfc.itsm.domain.EmployeeRoleAssignmentRepository;
import com.nbfc.itsm.domain.RoleRepository;
import com.nbfc.itsm.domain.SubCategoryRepository;
import com.nbfc.itsm.domain.TicketRepository;
import com.nbfc.itsm.domain.TicketTypeRepository;
import com.nbfc.itsm.domain.WorkflowRule;
import com.nbfc.itsm.domain.WorkflowRuleRepository;
import com.nbfc.itsm.identity.PortalUserService;
import com.nbfc.itsm.security.ItsmUserPrincipal;
import com.nbfc.itsm.seed.CatalogSeedService;
import com.nbfc.itsm.ticket.TicketForm;
import com.nbfc.itsm.util.TimeUtc;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.springframework.transaction.annotation.Transactional;

import static org.hamcrest.Matchers.allOf;
import static org.hamcrest.Matchers.containsString;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.flash;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class FormValidationTest {

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private CatalogSeedService catalogSeedService;
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
    private TicketRepository ticketRepository;
    @Autowired
    private WorkflowRuleRepository ruleRepository;
    @Autowired
    private ConfigChangeRequestRepository changeRequestRepository;

    private ItsmUserPrincipal employee;
    private ItsmUserPrincipal admin;

    @BeforeEach
    void setUp() {
        catalogSeedService.ensureSeeded();
        employee = principal("E-VAL-EMP", "val.emp", "Validation Employee", "EMPLOYEE");
        admin = principal("E-VAL-ADM", "val.adm", "Validation Admin", "IT_ADMIN");
    }

    @Test
    void raiseReportsEveryProblemAndKeepsWhatWasTyped() throws Exception {
        long before = ticketRepository.count();
        MvcResult result = mockMvc.perform(post("/tickets/raise").with(as(employee)).with(csrf())
                        .param("subject", "Hi")
                        .param("description", "   ")
                        .param("priorityCode", "Urgent")
                        .param("location", "Branch 7")
                        .param("intent", "submit"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/tickets/raise"))
                .andExpect(flash().attribute("errorMessage", allOf(
                        containsString("Select a ticket type."),
                        containsString("Select a category."),
                        containsString("Subject must be at least 5 characters."),
                        containsString("Description is required."),
                        containsString("Priority must be one of"))))
                .andReturn();
        assertEquals(before, ticketRepository.count(), "nothing saved");
        TicketForm kept = (TicketForm) result.getFlashMap().get("form");
        assertEquals("Branch 7", kept.getLocation());

        // The re-displayed form shows the typed values and the server limits.
        mockMvc.perform(get("/tickets/raise").with(as(employee)).flashAttr("form", kept))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("value=\"Branch 7\"")))
                .andExpect(content().string(containsString("maxlength=\"256\"")))
                .andExpect(content().string(containsString("minlength=\"10\"")));
    }

    @Test
    void missingLookupsNoLongerCauseAServerError() throws Exception {
        mockMvc.perform(post("/tickets/raise").with(as(employee)).with(csrf())
                        .param("subject", "Printer offline on floor 2")
                        .param("description", "Printer shows offline since morning.")
                        .param("intent", "submit"))
                .andExpect(status().is3xxRedirection())
                .andExpect(flash().attribute("errorMessage", containsString("Select a ticket type.")));
    }

    @Test
    void validTicketIsStillAccepted() throws Exception {
        Category hw = categoryRepository.findByCode("HARDWARE").orElseThrow(IllegalStateException::new);
        mockMvc.perform(post("/tickets/raise").with(as(employee)).with(csrf())
                        .param("ticketTypeId", String.valueOf(
                                ticketTypeRepository.findByCode("INCIDENT").orElseThrow(IllegalStateException::new).getTicketTypeId()))
                        .param("categoryId", String.valueOf(hw.getCategoryId()))
                        .param("subCategoryId", String.valueOf(
                                subCategoryRepository.findByCategoryAndActiveTrueOrderBySortOrderAsc(hw).get(0).getSubCategoryId()))
                        .param("subject", "  Laptop fan very loud  ")
                        .param("description", "Fan noise started after the last update.")
                        .param("priorityCode", "High")
                        .param("location", "")
                        .param("intent", "submit"))
                .andExpect(status().is3xxRedirection())
                .andExpect(flash().attribute("message", containsString("submitted")));
        assertTrue(ticketRepository.findAll().stream().anyMatch(t -> "Laptop fan very loud".equals(t.getSubject())),
                "subject stored trimmed");
    }

    @Test
    void workflowRuleRejectsInvalidJsonAndBlankName() throws Exception {
        WorkflowRule rule = ruleRepository.findAll().get(0);
        String originalJson = rule.getConditionJson();
        // IT Admin may look at Workflow Config but not change it (System Administrator only).
        mockMvc.perform(post("/admin/workflow/rules/{id}", rule.getWorkflowRuleId()).with(as(admin)).with(csrf())
                        .param("name", rule.getName())
                        .param("priority", String.valueOf(rule.getPriority()))
                        .param("statusCode", rule.getStatusCode())
                        .param("conditionJson", "{}")
                        .param("workflowDefinitionId", String.valueOf(rule.getWorkflowDefinition().getWorkflowDefinitionId())))
                .andExpect(flash().attribute("errorMessage", containsString("Only the System Administrator")));
        assertEquals(originalJson, ruleRepository.findById(rule.getWorkflowRuleId()).get().getConditionJson());

        ItsmUserPrincipal sysAdmin = principal("E-VAL-SYS", "val.sys", "Validation SysAdmin", "SYSTEM_ADMINISTRATOR");
        mockMvc.perform(post("/admin/workflow/rules/{id}", rule.getWorkflowRuleId()).with(as(sysAdmin)).with(csrf())
                        .param("name", " ")
                        .param("priority", String.valueOf(rule.getPriority()))
                        .param("workflowDefinitionId", String.valueOf(rule.getWorkflowDefinition().getWorkflowDefinitionId()))
                        .param("statusCode", "Enabled")
                        .param("conditionJson", "{ticket_type: Incident"))
                .andExpect(status().is3xxRedirection())
                .andExpect(flash().attribute("errorMessage", allOf(
                        containsString("Rule name is required."),
                        containsString("Status must be one of: Active, Inactive."),
                        containsString("Condition is not valid JSON"))));
        assertEquals(originalJson, ruleRepository.findById(rule.getWorkflowRuleId()).get().getConditionJson());

        mockMvc.perform(post("/admin/workflow/rules/{id}", rule.getWorkflowRuleId()).with(as(sysAdmin)).with(csrf())
                        .param("name", rule.getName())
                        .param("priority", String.valueOf(rule.getPriority()))
                        .param("workflowDefinitionId", String.valueOf(rule.getWorkflowDefinition().getWorkflowDefinitionId()))
                        .param("statusCode", rule.getStatusCode())
                        .param("conditionJson", "[1,2]"))
                .andExpect(flash().attribute("errorMessage", containsString("must be a JSON object")));
    }

    @Test
    void rejectionReasonMustBeMeaningful() throws Exception {
        ItsmUserPrincipal checker = principal("E-VAL-CHK", "val.chk", "Validation Checker", "IT_ADMIN");
        ConfigChangeRequest ccr = new ConfigChangeRequest();
        ccr.setChangeType("USER_PORTAL_ACTIVE");
        ccr.setEntityName("employee");
        ccr.setEntityKey(String.valueOf(employee.getEmployeeId()));
        ccr.setPayloadJson("{\"portalActive\":false}");
        ccr.setDescription("Disable test");
        ccr.setStatusCode("PendingApproval");
        ccr.setRequestedBy(employeeRepository.findById(admin.getEmployeeId()).get());
        ccr.setRequestedAtUtc(TimeUtc.now());
        ccr = changeRequestRepository.save(ccr);

        mockMvc.perform(post("/admin/change-requests/{id}/reject", ccr.getConfigChangeRequestId())
                        .with(as(checker)).with(csrf()).param("reason", "no"))
                .andExpect(flash().attribute("errorMessage", containsString("at least 10 characters")));
        assertEquals("PendingApproval", changeRequestRepository.findById(ccr.getConfigChangeRequestId()).get().getStatusCode());
    }

    @Test
    void reportDateRangeIsCorrectedWithAMessage() throws Exception {
        ItsmUserPrincipal reporter = principal("E-VAL-REP", "val.rep", "Validation Reporter", "IT_ADMIN");
        mockMvc.perform(get("/reports/volume").with(as(reporter))
                        .param("from", "2026-09-30").param("to", "2026-09-01"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("dates were swapped")));
    }

    @Test
    void overlongLoginIsRejectedBeforeLdap() throws Exception {
        String longName = new String(new char[300]).replace('\0', 'a');
        mockMvc.perform(post("/login").with(csrf()).param("username", longName).param("password", "x"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/login?error"));
    }

    private ItsmUserPrincipal principal(String no, String sam, String name, String roleCode) {
        Employee e = new Employee();
        e.setEmployeeNo(no);
        e.setSamAccountName(sam);
        e.setDisplayName(name);
        e.setPortalActive(true);
        e = employeeRepository.save(e);
        EmployeeRoleAssignment row = new EmployeeRoleAssignment();
        row.setEmployee(e);
        row.setRole(roleRepository.findByCode(roleCode).orElseThrow(() -> new IllegalStateException(roleCode)));
        assignmentRepository.save(row);
        return portalUserService.toPrincipal(e);
    }

    private static RequestPostProcessor as(ItsmUserPrincipal p) {
        return authentication(new UsernamePasswordAuthenticationToken(p, null, p.getAuthorities()));
    }
}
