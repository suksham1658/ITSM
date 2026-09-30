package com.nbfc.itsm.audit;

import com.nbfc.itsm.domain.AuditLog;
import com.nbfc.itsm.domain.AuditLogRepository;
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

import java.time.LocalDate;
import java.time.ZoneId;

import static org.hamcrest.Matchers.allOf;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Audit Trail page: filters and access. */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class AuditTrailTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private CatalogSeedService catalogSeedService;
    @Autowired private PortalUserService portalUserService;
    @Autowired private EmployeeRepository employeeRepository;
    @Autowired private RoleRepository roleRepository;
    @Autowired private EmployeeRoleAssignmentRepository assignmentRepository;
    @Autowired private AuditLogRepository auditLogRepository;

    private Employee auditor;
    private Employee plain;

    @BeforeEach
    void setUp() {
        catalogSeedService.ensureSeeded();
        auditor = employee("E-AT-AUD", "Audit Viewer", "EMPLOYEE", "CISO");
        plain = employee("E-AT-EMP", "Plain Person", "EMPLOYEE");
        log(auditor, "AUTH", "LOGIN", "zeta-login-marker", "SUCCESS", LocalDate.now(ZoneId.of("Asia/Kolkata")));
        log(plain, "ADMIN", "USER_DELEGATE", "zeta-delegate-marker", "SUCCESS", LocalDate.of(2026, 1, 10));
        log(plain, "AUTH", "LOGIN", "zeta-failed-marker", "FAILURE", LocalDate.of(2026, 1, 11));
    }

    @Test
    void filtersNarrowTheList() throws Exception {
        UsernamePasswordAuthenticationToken t = token(auditor);
        mockMvc.perform(get("/audit").with(authentication(t)))
                .andExpect(status().isOk())
                .andExpect(content().string(allOf(containsString("Immutable log"), containsString("zeta-login-marker"),
                        containsString("zeta-delegate-marker"), containsString("Plain Person"))));

        mockMvc.perform(get("/audit").param("action", "USER_DELEGATE").with(authentication(t)))
                .andExpect(content().string(allOf(containsString("zeta-delegate-marker"), not(containsString("zeta-login-marker")))));

        mockMvc.perform(get("/audit").param("user", "Plain").param("result", "FAILURE").with(authentication(t)))
                .andExpect(content().string(allOf(containsString("zeta-failed-marker"), not(containsString("zeta-delegate-marker")))));

        mockMvc.perform(get("/audit").param("from", "2026-01-10").param("to", "2026-01-10").param("text", "zeta")
                        .with(authentication(t)))
                .andExpect(content().string(allOf(containsString("zeta-delegate-marker"), not(containsString("zeta-failed-marker")))));

        mockMvc.perform(get("/audit").param("ticket", "NO-SUCH-TICKET").with(authentication(t)))
                .andExpect(content().string(containsString("No matching events")));
    }

    @Test
    void peopleWithoutAuditViewCannotOpenIt() throws Exception {
        mockMvc.perform(get("/audit").with(authentication(token(plain)))).andExpect(status().isForbidden());
    }

    private void log(Employee who, String module, String action, String detail, String result, LocalDate day) {
        AuditLog a = new AuditLog();
        a.setEmployeeId(who.getEmployeeId());
        a.setEmployeeNo(who.getEmployeeNo());
        a.setModuleCode(module);
        a.setActionCode(action);
        a.setNewValue(detail);
        a.setResultCode(result);
        a.setOccurredAtUtc(day.atTime(10, 0).atZone(ZoneId.of("Asia/Kolkata")).toInstant());
        auditLogRepository.save(a);
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
