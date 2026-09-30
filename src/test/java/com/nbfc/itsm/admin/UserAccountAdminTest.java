package com.nbfc.itsm.admin;

import com.nbfc.itsm.domain.Category;
import com.nbfc.itsm.domain.CategoryRepository;
import com.nbfc.itsm.domain.ConfigChangeRequestRepository;
import com.nbfc.itsm.domain.Employee;
import com.nbfc.itsm.domain.EmployeeRepository;
import com.nbfc.itsm.domain.EmployeeRoleAssignment;
import com.nbfc.itsm.domain.EmployeeRoleAssignmentRepository;
import com.nbfc.itsm.domain.RoleRepository;
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
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import static org.hamcrest.Matchers.allOf;
import static org.hamcrest.Matchers.containsString;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.flash;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Admin > Users: basic info, delegate (backup approver), immediate deactivation, re-activation by a second admin. */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class UserAccountAdminTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private CatalogSeedService catalogSeedService;
    @Autowired private PortalUserService portalUserService;
    @Autowired private UserAccountService userAccountService;
    @Autowired private TicketService ticketService;
    @Autowired private EmployeeRepository employeeRepository;
    @Autowired private RoleRepository roleRepository;
    @Autowired private EmployeeRoleAssignmentRepository assignmentRepository;
    @Autowired private ConfigChangeRequestRepository changeRequestRepository;
    @Autowired private TicketTypeRepository ticketTypeRepository;
    @Autowired private CategoryRepository categoryRepository;
    @Autowired private SubCategoryRepository subCategoryRepository;
    @Autowired private WorkflowInstanceRepository instanceRepository;
    @Autowired private WorkflowInstanceStageRepository instanceStageRepository;

    private Employee admin;
    private Employee manager;
    private Employee backup;
    private Employee requester;

    @BeforeEach
    void setUp() {
        catalogSeedService.ensureSeeded();
        admin = employee("E-UA-SA", "Users Admin", null, "EMPLOYEE", "SYSTEM_ADMINISTRATOR");
        employee("E-UA-SA2", "Second Admin", null, "EMPLOYEE", "SYSTEM_ADMINISTRATOR");
        manager = employee("E-UA-MGR", "Rajesh Manager", null, "EMPLOYEE", "HOD");
        backup = employee("E-UA-BAK", "Backup Approver", null, "EMPLOYEE");
        requester = employee("E-UA-REQ", "Users Requester", manager, "EMPLOYEE");
    }

    @AfterEach
    void clear() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void basicInfoIsSavedImmediatelyAndValidated() throws Exception {
        mockMvc.perform(post("/admin/users/{id}/basic", requester.getEmployeeId()).with(csrf()).with(authentication(token(admin)))
                        .param("displayName", "  Priya Sharma ").param("email", "priya.sharma@authum.com").param("designation", "Analyst"))
                .andExpect(flash().attribute("message", containsString("Basic information saved")));
        Employee e = employeeRepository.findById(requester.getEmployeeId()).get();
        assertEquals("Priya Sharma", e.getDisplayName());
        assertEquals("priya.sharma@authum.com", e.getEmail());

        mockMvc.perform(post("/admin/users/{id}/basic", requester.getEmployeeId()).with(csrf()).with(authentication(token(admin)))
                        .param("displayName", "Priya").param("email", "not-an-email"))
                .andExpect(flash().attribute("errorMessage", containsString("valid e-mail")));
    }

    @Test
    void delegateCanActOnApprovalsResolvedToTheOwner() {
        as(admin);
        userAccountService.setDelegate(manager.getEmployeeId(), backup.getEmployeeId(), principal(admin));
        assertThrows(ItsmException.class, () -> userAccountService.setDelegate(manager.getEmployeeId(), manager.getEmployeeId(), principal(admin)),
                "not their own delegate");

        Ticket sr = raiseServiceRequest();
        assertTrue(ticketService.approvalsFor(as(backup)).stream().anyMatch(t -> t.getTicketId().equals(sr.getTicketId())),
                "delegate sees it in Approvals");
        ticketService.applyAction(as(backup), sr.getTicketId(), "APPROVE", "Approved while Rajesh is on leave", null);

        WorkflowInstanceStage first = stages(sr).get(0);
        assertEquals("Completed", first.getStatusCode());
        assertTrue(first.getRemarks().startsWith("[On behalf of Rajesh Manager]"), first.getRemarks());

        as(admin);
        userAccountService.setDelegate(manager.getEmployeeId(), null, principal(admin));
        Ticket sr2 = raiseServiceRequest();
        assertThrows(AccessDeniedException.class,
                () -> ticketService.applyAction(as(backup), sr2.getTicketId(), "APPROVE", "No longer the delegate here", null));
    }

    @Test
    void deactivationIsImmediateAndOnlyASystemAdministratorReactivatesDirectly() throws Exception {
        mockMvc.perform(post("/admin/users/{id}/deactivate", requester.getEmployeeId()).with(csrf()).with(authentication(token(admin))))
                .andExpect(flash().attribute("message", containsString("deactivated immediately")));
        assertFalse(employeeRepository.findById(requester.getEmployeeId()).get().isPortalActive());

        mockMvc.perform(post("/admin/users/{id}/deactivate", admin.getEmployeeId()).with(csrf()).with(authentication(token(admin))))
                .andExpect(flash().attribute("errorMessage", containsString("own portal access")));

        // IT Admin (not a System Administrator): re-activation waits for approval.
        Employee itAdmin = employee("E-UA-ITA", "IT Admin Person", null, "EMPLOYEE", "IT_ADMIN");
        long pending = changeRequestRepository.countByStatusCode("PendingApproval");
        mockMvc.perform(post("/admin/users/{id}/propose-active", requester.getEmployeeId()).with(csrf()).with(authentication(token(itAdmin)))
                        .param("active", "true"))
                .andExpect(flash().attribute("message", containsString("System Administrator must approve")));
        assertEquals(pending + 1, changeRequestRepository.countByStatusCode("PendingApproval"));
        assertFalse(employeeRepository.findById(requester.getEmployeeId()).get().isPortalActive(), "still off until approved");
        mockMvc.perform(post("/admin/users/{id}/propose-active", requester.getEmployeeId()).with(csrf()).with(authentication(token(itAdmin)))
                        .param("active", "true"))
                .andExpect(flash().attribute("errorMessage", containsString("already waiting")));

        // System Administrator: final authority, re-activated at once.
        Employee other = employee("E-UA-OFF", "Offboarded Person", null, "EMPLOYEE");
        other.setPortalActive(false);
        employeeRepository.save(other);
        mockMvc.perform(post("/admin/users/{id}/propose-active", other.getEmployeeId()).with(csrf()).with(authentication(token(admin)))
                        .param("active", "true"))
                .andExpect(flash().attribute("message", containsString("re-activated")));
        assertTrue(employeeRepository.findById(other.getEmployeeId()).get().isPortalActive());
    }

    @Test
    void userPageShowsAllSections() throws Exception {
        mockMvc.perform(get("/admin/users/{id}", manager.getEmployeeId()).with(authentication(token(admin))))
                .andExpect(status().isOk())
                .andExpect(content().string(allOf(containsString("Basic Information"), containsString("Role Override"),
                        containsString("Delegate (Backup Approver)"), containsString("Deactivate portal access"),
                        containsString("Re-sync now from LDAP"))));
        mockMvc.perform(get("/admin/users").param("status", "active").with(authentication(token(admin))))
                .andExpect(status().isOk())
                .andExpect(content().string(allOf(containsString("Delegate"), containsString("Rajesh Manager"))));
    }

    @Test
    void resyncExplainsWhenTheDirectoryIsNotConfigured() throws Exception {
        mockMvc.perform(post("/admin/users/{id}/resync", manager.getEmployeeId()).with(csrf()).with(authentication(token(admin))))
                .andExpect(flash().attribute("errorMessage", containsString("Could not read")));
    }

    // ------------------------------------------------------------------ helpers

    private java.util.List<WorkflowInstanceStage> stages(Ticket t) {
        WorkflowInstance inst = instanceRepository.findByTicketId(t.getTicketId()).orElseThrow(IllegalStateException::new);
        return instanceStageRepository.findByWorkflowInstanceOrderByStageOrderAsc(inst);
    }

    private Ticket raiseServiceRequest() {
        Category network = categoryRepository.findByCode("NETWORK").orElseThrow(IllegalStateException::new);
        TicketForm f = new TicketForm();
        f.setTicketTypeId(ticketTypeRepository.findByCode("SERVICE_REQUEST").orElseThrow(IllegalStateException::new).getTicketTypeId());
        f.setCategoryId(network.getCategoryId());
        f.setSubCategoryId(subCategoryRepository.findByCategoryAndActiveTrueOrderBySortOrderAsc(network).get(0).getSubCategoryId());
        f.setSubject("VPN access for remote work");
        f.setDescription("Need VPN access to work from the branch office.");
        f.setIntent("submit");
        return ticketService.save(as(requester), f);
    }

    private ItsmUserPrincipal as(Employee e) {
        ItsmUserPrincipal p = principal(e);
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(p, null, p.getAuthorities()));
        return p;
    }

    private ItsmUserPrincipal principal(Employee e) {
        return portalUserService.toPrincipal(employeeRepository.findById(e.getEmployeeId()).get());
    }

    private UsernamePasswordAuthenticationToken token(Employee e) {
        ItsmUserPrincipal p = principal(e);
        return new UsernamePasswordAuthenticationToken(p, null, p.getAuthorities());
    }

    private Employee employee(String no, String name, Employee manager, String... roles) {
        Employee e = new Employee();
        e.setEmployeeNo(no);
        e.setSamAccountName(no.toLowerCase());
        e.setDisplayName(name);
        e.setManager(manager);
        e.setPortalActive(true);
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
