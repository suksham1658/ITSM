package com.nbfc.itsm.web;

import com.nbfc.itsm.domain.Employee;
import com.nbfc.itsm.domain.EmployeeRepository;
import com.nbfc.itsm.domain.EmployeeRoleAssignment;
import com.nbfc.itsm.domain.EmployeeRoleAssignmentRepository;
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
import org.springframework.transaction.annotation.Transactional;

import static org.hamcrest.Matchers.allOf;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Horizontal top menu on every page, showing only what the role may open. */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class TopNavTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private CatalogSeedService catalogSeedService;
    @Autowired private PortalUserService portalUserService;
    @Autowired private EmployeeRepository employeeRepository;
    @Autowired private RoleRepository roleRepository;
    @Autowired private EmployeeRoleAssignmentRepository assignmentRepository;

    @BeforeEach
    void seed() {
        catalogSeedService.ensureSeeded();
    }

    @Test
    void employeeSeesTicketsButNoAdminMenu() throws Exception {
        mockMvc.perform(get("/").with(authentication(token(employee("E-TN-EMP", "Menu Employee", "EMPLOYEE")))))
                .andExpect(status().isOk())
                .andExpect(content().string(allOf(containsString("id=\"topNav\""), containsString("Raise Request"),
                        containsString("Approvals"), not(containsString("tn-menu tn-menu-right\" role=\"menu\">\n            <a role=\"menuitem\" href=\"/admin/users\"")),
                        not(containsString("> Workflow Config</a>")))));
    }

    @Test
    void systemAdministratorSeesTheAdminMenuAndTheCurrentSectionIsHighlighted() throws Exception {
        UsernamePasswordAuthenticationToken t = token(employee("E-TN-SA", "Menu Admin", "EMPLOYEE", "SYSTEM_ADMINISTRATOR"));
        mockMvc.perform(get("/admin/workflow").with(authentication(t)))
                .andExpect(status().isOk())
                .andExpect(content().string(allOf(containsString("id=\"topNav\""), containsString("> Workflow Config</a>"),
                        containsString("> System Configuration</a>"), containsString("class=\"tn-group  active\""))));
    }

    private UsernamePasswordAuthenticationToken token(Employee e) {
        ItsmUserPrincipal p = portalUserService.toPrincipal(employeeRepository.findById(e.getEmployeeId()).get());
        return new UsernamePasswordAuthenticationToken(p, null, p.getAuthorities());
    }

    private Employee employee(String no, String name, String... roles) {
        Employee e = new Employee();
        e.setEmployeeNo(no);
        e.setSamAccountName(no.toLowerCase());
        e.setDisplayName(name);
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
