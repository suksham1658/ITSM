package com.nbfc.itsm.admin;

import com.nbfc.itsm.config.ItsmProperties;
import com.nbfc.itsm.domain.ConfigChangeRequest;
import com.nbfc.itsm.domain.Employee;
import com.nbfc.itsm.domain.EmployeeRepository;
import com.nbfc.itsm.domain.EmployeeRoleAssignment;
import com.nbfc.itsm.domain.EmployeeRoleAssignmentRepository;
import com.nbfc.itsm.domain.RoleRepository;
import com.nbfc.itsm.domain.SystemSettingRepository;
import com.nbfc.itsm.exception.ItsmException;
import com.nbfc.itsm.identity.PortalUserService;
import com.nbfc.itsm.security.ItsmUserPrincipal;
import com.nbfc.itsm.seed.CatalogSeedService;
import com.nbfc.itsm.workflow.WorkflowEngine;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import static org.hamcrest.Matchers.allOf;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** System Configuration: maker-checker, validation, runtime effect, page. */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class SystemSettingsTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private SystemSettingsService settings;
    @Autowired private AdminUserService adminUserService;
    @Autowired private ItsmProperties props;
    @Autowired private WorkflowEngine workflowEngine;
    @Autowired private CatalogSeedService catalogSeedService;
    @Autowired private PortalUserService portalUserService;
    @Autowired private EmployeeRepository employeeRepository;
    @Autowired private RoleRepository roleRepository;
    @Autowired private EmployeeRoleAssignmentRepository assignmentRepository;
    @Autowired private SystemSettingRepository settingRepository;

    private Employee maker;
    private Employee checker;
    private int originalTimeout;

    @BeforeEach
    void setUp() {
        catalogSeedService.ensureSeeded();
        maker = employee("E-SS-MK", "Settings Maker");
        checker = employee("E-SS-CK", "Settings Checker");
        originalTimeout = props.getLdap().getConnectTimeoutMs();
    }

    @AfterEach
    void restore() {
        props.getLdap().setConnectTimeoutMs(originalTimeout);
        SecurityContextHolder.clearContext();
    }

    @Test
    void changeTakesEffectOnlyAfterASecondAdministratorApproves() {
        ConfigChangeRequest ccr = settings.propose("ldap.connect-timeout-ms", "4500", as(maker));
        assertEquals(originalTimeout, props.getLdap().getConnectTimeoutMs(), "nothing changes before approval");

        ItsmException self = assertThrows(ItsmException.class, () -> adminUserService.approve(ccr.getConfigChangeRequestId(), as(maker)));
        assertEquals("CCR_SAME_USER", self.getCode());

        adminUserService.approve(ccr.getConfigChangeRequestId(), as(checker));
        assertEquals(4500, props.getLdap().getConnectTimeoutMs(), "applied at runtime");
        assertEquals("4500", settingRepository.findById("ldap.connect-timeout-ms").get().getSettingValue(), "stored for restarts");

        props.getLdap().setConnectTimeoutMs(1234);
        settings.run(null);
        assertEquals(4500, props.getLdap().getConnectTimeoutMs(), "re-applied at start-up");
    }

    @Test
    void workflowSettingIsReadByTheEngine() {
        ConfigChangeRequest ccr = settings.propose("approval.remarks-min-length", "15", as(maker));
        adminUserService.approve(ccr.getConfigChangeRequestId(), as(checker));
        assertEquals(15, workflowEngine.remarksMin());
    }

    @Test
    void invalidAndDuplicateChangesAreRefused() {
        ItsmUserPrincipal p = as(maker);
        assertThrows(ItsmException.class, () -> settings.propose("ldap.url", "http://not-ldap", p));
        assertThrows(ItsmException.class, () -> settings.propose("mail.port", "70000", p));
        assertThrows(ItsmException.class, () -> settings.propose("mail.from", "no-at-sign", p));
        assertThrows(ItsmException.class, () -> settings.propose("ldap.user-search-filter", "sAMAccountName=x", p));
        assertThrows(ItsmException.class, () -> settings.propose("no.such.key", "1", p));
        settings.propose("general.company-name", "Authum Investment", p);
        ItsmException dup = assertThrows(ItsmException.class, () -> settings.propose("general.company-name", "Other", p));
        assertEquals("ALREADY_PENDING", dup.getCode());
    }

    @Test
    void pageShowsTheSectionsAndNoSecrets() throws Exception {
        mockMvc.perform(get("/admin/config").with(authentication(token(maker))))
                .andExpect(status().isOk())
                .andExpect(content().string(allOf(containsString("LDAP / Active Directory Settings"), containsString("SMTP (E-mail) Settings"),
                        containsString("Session Settings"), containsString("General Settings"), containsString("Security Settings"),
                        containsString("ldap.url"), not(containsString("bind-password\"")))));
    }

    private ItsmUserPrincipal as(Employee e) {
        ItsmUserPrincipal p = portalUserService.toPrincipal(employeeRepository.findById(e.getEmployeeId()).get());
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(p, null, p.getAuthorities()));
        return p;
    }

    private UsernamePasswordAuthenticationToken token(Employee e) {
        ItsmUserPrincipal p = portalUserService.toPrincipal(employeeRepository.findById(e.getEmployeeId()).get());
        return new UsernamePasswordAuthenticationToken(p, null, p.getAuthorities());
    }

    private Employee employee(String no, String name) {
        Employee e = new Employee();
        e.setEmployeeNo(no);
        e.setSamAccountName(no.toLowerCase());
        e.setDisplayName(name);
        e.setPortalActive(true);
        e = employeeRepository.save(e);
        EmployeeRoleAssignment row = new EmployeeRoleAssignment();
        row.setEmployee(e);
        row.setRole(roleRepository.findByCode("SYSTEM_ADMINISTRATOR").orElseThrow(IllegalStateException::new));
        assignmentRepository.save(row);
        assertTrue(e.getEmployeeId() != null);
        return e;
    }
}
