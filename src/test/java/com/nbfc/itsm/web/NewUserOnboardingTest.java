package com.nbfc.itsm.web;

import com.nbfc.itsm.domain.Category;
import com.nbfc.itsm.domain.CategoryRepository;
import com.nbfc.itsm.domain.ConfigChangeRequestRepository;
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
import com.nbfc.itsm.domain.TicketTypeRepository;
import com.nbfc.itsm.identity.PortalUserService;
import com.nbfc.itsm.security.ItsmUserPrincipal;
import com.nbfc.itsm.seed.CatalogSeedService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextImpl;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

import static org.hamcrest.Matchers.containsString;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.flash;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrlPattern;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * A brand-new user (no roles, as created on first LDAP login) is given the Employee role by the
 * System Administrator: the change applies at once with no approval request, and the user's
 * existing session can raise and view tickets without signing in again.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class NewUserOnboardingTest {

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
    private ConfigChangeRequestRepository changeRequestRepository;
    @Autowired
    private TicketTypeRepository ticketTypeRepository;
    @Autowired
    private CategoryRepository categoryRepository;
    @Autowired
    private SubCategoryRepository subCategoryRepository;
    @Autowired
    private TicketRepository ticketRepository;

    private Employee newUser;
    private MockHttpSession sysAdminSession;
    private MockHttpSession newUserSession;

    @BeforeEach
    void setUp() {
        catalogSeedService.ensureSeeded();
        Employee sysAdmin = employee("E-ONB-SYS", "onb.sysadmin", "Onboarding SysAdmin");
        grant(sysAdmin, "SYSTEM_ADMINISTRATOR");
        sysAdminSession = sessionFor(portalUserService.toPrincipal(sysAdmin));

        // Signed in before any role was assigned: an empty principal sits in their session.
        newUser = employee("E-ONB-NEW", "onb.newuser", "Onboarding New User");
        newUserSession = sessionFor(portalUserService.toPrincipal(newUser));
    }

    @Test
    void systemAdministratorAssignmentAppliesImmediatelyWithoutApproval() throws Exception {
        Role employeeRole = role("EMPLOYEE");
        long pendingBefore = changeRequestRepository.countByStatusCode("PendingApproval");

        mockMvc.perform(post("/admin/users/{id}/propose-role", newUser.getEmployeeId())
                        .session(sysAdminSession).with(csrf())
                        .param("roleId", String.valueOf(employeeRole.getRoleId()))
                        .param("assign", "true"))
                .andExpect(status().is3xxRedirection())
                .andExpect(flash().attribute("message", containsString("Role assigned")));

        assertTrue(assignmentRepository.findByEmployeeAndRole(newUser, employeeRole).isPresent(), "applied at once");
        assertEquals(pendingBefore, changeRequestRepository.countByStatusCode("PendingApproval"),
                "nothing forwarded for approval");

        mockMvc.perform(post("/admin/users/{id}/propose-role", newUser.getEmployeeId())
                        .session(sysAdminSession).with(csrf())
                        .param("roleId", String.valueOf(employeeRole.getRoleId()))
                        .param("assign", "false"))
                .andExpect(flash().attribute("message", containsString("Role removed")));
        assertFalse(assignmentRepository.findByEmployeeAndRole(newUser, employeeRole).isPresent());
    }

    @Test
    void itAdminAssignmentStillNeedsApproval() throws Exception {
        Employee itAdmin = employee("E-ONB-ITA", "onb.itadmin", "Onboarding IT Admin");
        grant(itAdmin, "IT_ADMIN");
        Role employeeRole = role("EMPLOYEE");
        long pendingBefore = changeRequestRepository.countByStatusCode("PendingApproval");

        mockMvc.perform(post("/admin/users/{id}/propose-role", newUser.getEmployeeId())
                        .session(sessionFor(portalUserService.toPrincipal(itAdmin))).with(csrf())
                        .param("roleId", String.valueOf(employeeRole.getRoleId()))
                        .param("assign", "true"))
                .andExpect(flash().attribute("message", containsString("checker approval")));

        assertFalse(assignmentRepository.findByEmployeeAndRole(newUser, employeeRole).isPresent());
        assertEquals(pendingBefore + 1, changeRequestRepository.countByStatusCode("PendingApproval"));
    }

    @Test
    void newEmployeeCanRaiseAndViewTicketsWithoutSigningInAgain() throws Exception {
        mockMvc.perform(get("/tickets/raise").session(newUserSession))
                .andExpect(status().isForbidden());

        mockMvc.perform(post("/admin/users/{id}/propose-role", newUser.getEmployeeId())
                        .session(sysAdminSession).with(csrf())
                        .param("roleId", String.valueOf(role("EMPLOYEE").getRoleId()))
                        .param("assign", "true"))
                .andExpect(status().is3xxRedirection());

        // Same session as before the assignment: permissions are picked up on the next request.
        mockMvc.perform(get("/tickets/raise").session(newUserSession))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Raise IT Request")));

        Category hardware = categoryRepository.findByCode("HARDWARE").orElseThrow(IllegalStateException::new);
        List<SubCategory> subs = subCategoryRepository.findByCategoryAndActiveTrueOrderBySortOrderAsc(hardware);
        mockMvc.perform(post("/tickets/raise").session(newUserSession).with(csrf())
                        .param("ticketTypeId", String.valueOf(
                                ticketTypeRepository.findByCode("INCIDENT").orElseThrow(IllegalStateException::new).getTicketTypeId()))
                        .param("categoryId", String.valueOf(hardware.getCategoryId()))
                        .param("subCategoryId", String.valueOf(subs.get(0).getSubCategoryId()))
                        .param("subject", "New joiner laptop will not start")
                        .param("description", "Raised by a user who was just given the Employee role.")
                        .param("intent", "submit"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrlPattern("/tickets/*"));

        Ticket raised = null;
        for (Ticket t : ticketRepository.findAll()) {
            if ("New joiner laptop will not start".equals(t.getSubject())) {
                raised = t;
            }
        }
        assertTrue(raised != null && raised.getRequester().getEmployeeId().equals(newUser.getEmployeeId()));
        assertFalse("Draft".equals(raised.getStatusCode()), "submitted into the workflow, not left as a draft");
        assertTrue(raised.getPublicNumber().startsWith("ITSM-"), raised.getPublicNumber());

        mockMvc.perform(get("/tickets").session(newUserSession))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString(raised.getPublicNumber())));
        mockMvc.perform(get("/tickets/{id}", raised.getTicketId()).session(newUserSession))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("New joiner laptop will not start")));
        // Employee permissions only: admin pages stay closed.
        mockMvc.perform(get("/admin/users").session(newUserSession))
                .andExpect(status().isForbidden());
    }

    @Test
    void disablingAUserEndsTheirSession() throws Exception {
        grant(newUser, "EMPLOYEE");
        mockMvc.perform(get("/tickets").session(newUserSession)).andExpect(status().isOk());
        newUser.setPortalActive(false);
        employeeRepository.save(newUser);
        mockMvc.perform(get("/tickets").session(newUserSession))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/login?error=denied"));
    }

    @Test
    void systemAdministratorCannotRemoveTheLastAdministrator() throws Exception {
        Employee sysAdmin = employeeRepository.findByEmployeeNo("E-ONB-SYS").orElseThrow(IllegalStateException::new);
        for (Employee e : employeeRepository.findAll()) {
            if (!e.getEmployeeId().equals(sysAdmin.getEmployeeId())) {
                e.setPortalActive(false);
            }
        }
        Role sys = role("SYSTEM_ADMINISTRATOR");
        mockMvc.perform(post("/admin/users/{id}/propose-role", sysAdmin.getEmployeeId())
                        .session(sysAdminSession).with(csrf())
                        .param("roleId", String.valueOf(sys.getRoleId()))
                        .param("assign", "false"))
                .andExpect(flash().attribute("errorMessage", containsString("would leave no active employee")));
        assertTrue(assignmentRepository.findByEmployeeAndRole(sysAdmin, sys).isPresent());
    }

    // ------------------------------------------------------------------ helpers

    private MockHttpSession sessionFor(ItsmUserPrincipal principal) {
        SecurityContext context = new SecurityContextImpl(
                new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities()));
        MockHttpSession session = new MockHttpSession();
        session.setAttribute(HttpSessionSecurityContextRepository.SPRING_SECURITY_CONTEXT_KEY, context);
        return session;
    }

    private Employee employee(String no, String sam, String name) {
        Employee e = new Employee();
        e.setEmployeeNo(no);
        e.setSamAccountName(sam);
        e.setDisplayName(name);
        e.setPortalActive(true);
        return employeeRepository.save(e);
    }

    private void grant(Employee e, String roleCode) {
        EmployeeRoleAssignment row = new EmployeeRoleAssignment();
        row.setEmployee(e);
        row.setRole(role(roleCode));
        assignmentRepository.save(row);
    }

    private Role role(String code) {
        return roleRepository.findByCode(code).orElseThrow(() -> new IllegalStateException(code));
    }
}
