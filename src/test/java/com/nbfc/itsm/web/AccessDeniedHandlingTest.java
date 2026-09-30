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

import static org.hamcrest.Matchers.containsString;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** A form sent with an expired security token (e.g. after a restart) and a real permission refusal on a POST. */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class AccessDeniedHandlingTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private CatalogSeedService catalogSeedService;
    @Autowired private PortalUserService portalUserService;
    @Autowired private EmployeeRepository employeeRepository;
    @Autowired private RoleRepository roleRepository;
    @Autowired private EmployeeRoleAssignmentRepository assignmentRepository;

    private UsernamePasswordAuthenticationToken employeeToken;

    @BeforeEach
    void setUp() {
        catalogSeedService.ensureSeeded();
        Employee e = new Employee();
        e.setEmployeeNo("E-AD-EMP");
        e.setSamAccountName("e-ad-emp");
        e.setDisplayName("Denied Employee");
        e.setPortalActive(true);
        e = employeeRepository.save(e);
        EmployeeRoleAssignment row = new EmployeeRoleAssignment();
        row.setEmployee(e);
        row.setRole(roleRepository.findByCode("EMPLOYEE").orElseThrow(IllegalStateException::new));
        assignmentRepository.save(row);
        ItsmUserPrincipal p = portalUserService.toPrincipal(e);
        employeeToken = new UsernamePasswordAuthenticationToken(p, null, p.getAuthorities());
    }

    @Test
    void expiredFormTokenSendsTheUserToSignInAgain() throws Exception {
        mockMvc.perform(post("/tickets/1/comments").param("body", "Posted after a restart")
                        .with(csrf().useInvalidToken()))
                .andExpect(redirectedUrl("/login?ended=1"));
        mockMvc.perform(get("/login").param("ended", "1"))
                .andExpect(content().string(containsString("Your session ended")));
    }

    @Test
    void signedInWithAWrongTokenStaysForbidden() throws Exception {
        mockMvc.perform(post("/tickets/1/comments").param("body", "Forged")
                        .with(csrf().useInvalidToken()).with(authentication(employeeToken)))
                .andExpect(status().isForbidden());
    }

    @Test
    void realPermissionRefusalOnAPostShowsTheForbiddenPage() throws Exception {
        mockMvc.perform(post("/admin/config").param("key", "general.company-name").param("value", "X")
                        .with(csrf()).with(authentication(employeeToken)))
                .andExpect(status().isForbidden());
    }
}
