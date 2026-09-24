package com.nbfc.itsm.web;

import com.nbfc.itsm.domain.ConfigChangeRequestRepository;
import com.nbfc.itsm.domain.Employee;
import com.nbfc.itsm.domain.EmployeeRepository;
import com.nbfc.itsm.domain.EmployeeRoleAssignment;
import com.nbfc.itsm.domain.EmployeeRoleAssignmentRepository;
import com.nbfc.itsm.domain.PermissionRepository;
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
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.springframework.transaction.annotation.Transactional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class AdminRolePagesTest {

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
    private PermissionRepository permissionRepository;

    private ItsmUserPrincipal admin;
    private ItsmUserPrincipal employee;

    @BeforeEach
    void setUp() {
        catalogSeedService.ensureSeeded();
        admin = principal("E-PGADMIN", "pages.admin", "Pages Admin", "IT_ADMIN");
        employee = principal("E-PGEMP", "pages.emp", "Pages Employee", "EMPLOYEE");
    }

    @Test
    void listDetailAndFormsRender() throws Exception {
        Role itAdmin = roleRepository.findByCode("IT_ADMIN").orElseThrow(IllegalStateException::new);
        mockMvc.perform(get("/admin/roles").with(as(admin)))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Roles &amp; Permissions")))
                .andExpect(content().string(containsString("IT Admin")));
        mockMvc.perform(get("/admin/roles/{id}", itAdmin.getRoleId()).with(as(admin)))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("ADMIN_MASTERDATA_APPROVE")))
                .andExpect(content().string(containsString("Pages Admin")));
        mockMvc.perform(get("/admin/roles/new").with(as(admin)))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Propose Role")));
        mockMvc.perform(get("/admin/roles/{id}/edit", itAdmin.getRoleId()).with(as(admin)))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Propose Changes")));
    }

    @Test
    void proposingARoleRedirectsAndQueuesTheChange() throws Exception {
        long before = changeRequestRepository.countByStatusCode("PendingApproval");
        mockMvc.perform(post("/admin/roles").with(as(admin)).with(csrf())
                        .param("name", "Vendor Liaison")
                        .param("permissionCodes", "REPORT_VIEW", "KB_READ"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/admin/roles"));
        assertEquals(before + 1, changeRequestRepository.countByStatusCode("PendingApproval"));
        assertFalse(roleRepository.findByCode("VENDOR_LIAISON").isPresent());
    }

    @Test
    void invalidProposalRedisplaysTheFormWithTheError() throws Exception {
        mockMvc.perform(post("/admin/roles").with(as(admin)).with(csrf())
                        .param("name", "Empty Role"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Select at least one permission")));
    }

    @Test
    void employeesCannotOpenRoleAdmin() throws Exception {
        mockMvc.perform(get("/admin/roles").with(as(employee)))
                .andExpect(status().isForbidden());
        mockMvc.perform(post("/admin/roles").with(as(employee)).with(csrf())
                        .param("name", "Sneaky").param("permissionCodes", "ADMIN_USER_MANAGE"))
                .andExpect(status().isForbidden());
    }

    @Test
    void deleteIconIsShownToSystemAdministratorOnly() throws Exception {
        ItsmUserPrincipal sysAdmin = principal("E-PGSYS", "pages.sys", "Pages SysAdmin", "SYSTEM_ADMINISTRATOR");
        mockMvc.perform(get("/admin/roles").with(as(sysAdmin)))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("fa-trash-can")))
                .andExpect(content().string(not(containsString("fa-pen-to-square"))));
        mockMvc.perform(get("/admin/roles").with(as(admin)))
                .andExpect(status().isOk())
                .andExpect(content().string(not(containsString("fa-trash-can"))));
    }

    @Test
    void systemAdministratorConfirmsAndDeletesACustomRole() throws Exception {
        Role role = new Role();
        role.setCode("PAGES_TEMP_ROLE");
        role.setName("Pages Temp Role");
        role.setSystem(false);
        role.getPermissions().add(permissionRepository.findByCode("KB_READ").orElseThrow(IllegalStateException::new));
        role = roleRepository.save(role);
        ItsmUserPrincipal sysAdmin = principal("E-PGSYS2", "pages.sys2", "Pages SysAdmin Two", "SYSTEM_ADMINISTRATOR");

        mockMvc.perform(get("/admin/roles/{id}/delete", role.getRoleId()).with(as(sysAdmin)))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("cannot be undone")));
        mockMvc.perform(post("/admin/roles/{id}/delete", role.getRoleId()).with(as(admin)).with(csrf()))
                .andExpect(status().isForbidden());
        mockMvc.perform(post("/admin/roles/{id}/delete", role.getRoleId()).with(as(sysAdmin)).with(csrf()))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/admin/roles"));
        assertFalse(roleRepository.findByCode("PAGES_TEMP_ROLE").isPresent());
    }

    @Test
    void seededRoleShowsWhyItCannotBeDeleted() throws Exception {
        ItsmUserPrincipal sysAdmin = principal("E-PGSYS3", "pages.sys3", "Pages SysAdmin Three", "SYSTEM_ADMINISTRATOR");
        Role employeeRole = roleRepository.findByCode("EMPLOYEE").orElseThrow(IllegalStateException::new);
        mockMvc.perform(get("/admin/roles/{id}/delete", employeeRole.getRoleId()).with(as(sysAdmin)))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Cannot Delete Role")))
                .andExpect(content().string(containsString("seeded system role")));
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
