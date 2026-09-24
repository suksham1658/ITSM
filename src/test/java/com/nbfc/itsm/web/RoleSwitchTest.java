package com.nbfc.itsm.web;

import com.nbfc.itsm.domain.Employee;
import com.nbfc.itsm.domain.EmployeeRepository;
import com.nbfc.itsm.domain.EmployeeRoleAssignment;
import com.nbfc.itsm.domain.EmployeeRoleAssignmentRepository;
import com.nbfc.itsm.domain.Role;
import com.nbfc.itsm.domain.RoleRepository;
import com.nbfc.itsm.identity.PortalUserService;
import com.nbfc.itsm.security.ItsmUserPrincipal;
import com.nbfc.itsm.seed.CatalogSeedService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;

import java.util.Arrays;
import java.util.Collections;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class RoleSwitchTest {

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

    private Employee cisoEmployee;

    @BeforeEach
    void setUp() {
        catalogSeedService.ensureSeeded();
        cisoEmployee = new Employee();
        cisoEmployee.setEmployeeNo("E-SWITCH");
        cisoEmployee.setSamAccountName("switch.user");
        cisoEmployee.setDisplayName("Switch User");
        cisoEmployee.setPortalActive(true);
        cisoEmployee = employeeRepository.save(cisoEmployee);
        for (String code : Arrays.asList("EMPLOYEE", "CISO")) {
            Role role = roleRepository.findByCode(code).orElseThrow(() -> new IllegalStateException(code));
            EmployeeRoleAssignment row = new EmployeeRoleAssignment();
            row.setEmployee(cisoEmployee);
            row.setRole(role);
            assignmentRepository.save(row);
        }
    }

    @Test
    void defaultPrincipalCombinesAllAssignedRoles() {
        ItsmUserPrincipal all = portalUserService.toPrincipal(cisoEmployee);
        assertNull(all.getActiveRoleCode());
        assertTrue(all.isRoleSwitchAvailable());
        assertEquals(2, all.getAssignedRoles().size());
        assertTrue(all.has("TICKET_VIEW_SECURITY"));
        assertTrue(all.has("KB_READ"));
        assertEquals("All roles (2)", all.getPrimaryRoleLabel());
    }

    @Test
    void switchingNarrowsAuthoritiesToTheChosenRole() {
        ItsmUserPrincipal asEmployee = portalUserService.switchActiveRole(cisoEmployee.getEmployeeId(), "EMPLOYEE");
        assertEquals("EMPLOYEE", asEmployee.getActiveRoleCode());
        assertEquals(Collections.singletonList("EMPLOYEE"), asEmployee.getRoleCodes());
        assertTrue(asEmployee.has("TICKET_CREATE"));
        assertFalse(asEmployee.has("TICKET_VIEW_SECURITY"));
        assertFalse(asEmployee.has("ROLE_CISO"));
        assertEquals(2, asEmployee.getAssignedRoles().size(), "switcher still lists every assigned role");

        ItsmUserPrincipal back = portalUserService.switchActiveRole(cisoEmployee.getEmployeeId(), "");
        assertNull(back.getActiveRoleCode());
        assertTrue(back.has("TICKET_VIEW_SECURITY"));
    }

    @Test
    void cannotSwitchToARoleThatIsNotAssigned() {
        assertThrows(AccessDeniedException.class,
                () -> portalUserService.switchActiveRole(cisoEmployee.getEmployeeId(), "SYSTEM_ADMINISTRATOR"));
    }

    @Test
    void switchIsStoredInTheSessionAndEnforcedOnPages() throws Exception {
        ItsmUserPrincipal all = portalUserService.toPrincipal(cisoEmployee);
        MvcResult result = mockMvc.perform(post("/session/active-role")
                        .param("role", "EMPLOYEE")
                        .with(authentication(new UsernamePasswordAuthenticationToken(all, null, all.getAuthorities())))
                        .with(csrf()))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/"))
                .andReturn();

        MockHttpSession session = (MockHttpSession) result.getRequest().getSession(false);
        assertNotNull(session);
        SecurityContext saved = (SecurityContext) session.getAttribute(
                HttpSessionSecurityContextRepository.SPRING_SECURITY_CONTEXT_KEY);
        assertNotNull(saved);
        ItsmUserPrincipal switched = (ItsmUserPrincipal) saved.getAuthentication().getPrincipal();
        assertEquals("EMPLOYEE", switched.getActiveRoleCode());

        mockMvc.perform(get("/tickets/security").session(session))
                .andExpect(status().isForbidden());
    }

    @Test
    void craftedSwitchToUnassignedRoleIsDenied() throws Exception {
        ItsmUserPrincipal all = portalUserService.toPrincipal(cisoEmployee);
        mockMvc.perform(post("/session/active-role")
                        .param("role", "SYSTEM_ADMINISTRATOR")
                        .with(authentication(new UsernamePasswordAuthenticationToken(all, null, all.getAuthorities())))
                        .with(csrf()))
                .andExpect(status().isForbidden());
    }
}
