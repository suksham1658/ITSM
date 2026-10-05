package com.nbfc.itsm.web;

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

import java.time.Instant;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Audit Trail page: filter/export button, horizontal scroll, and the bold-header Excel export. */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class AuditWebTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private CatalogSeedService catalogSeedService;
    @Autowired private PortalUserService portalUserService;
    @Autowired private EmployeeRepository employeeRepository;
    @Autowired private RoleRepository roleRepository;
    @Autowired private EmployeeRoleAssignmentRepository assignmentRepository;
    @Autowired private AuditLogRepository auditLogRepository;

    private ItsmUserPrincipal admin;

    @BeforeEach
    void setUp() {
        catalogSeedService.ensureSeeded();
        Employee e = new Employee();
        e.setEmployeeNo("E-AUDIT");
        e.setSamAccountName("audit.admin");
        e.setDisplayName("Audit Admin");
        e.setPortalActive(true);
        e = employeeRepository.save(e);
        EmployeeRoleAssignment row = new EmployeeRoleAssignment();
        row.setEmployee(e);
        row.setRole(roleRepository.findByCode("SYSTEM_ADMINISTRATOR").orElseThrow(IllegalStateException::new));
        assignmentRepository.save(row);
        admin = portalUserService.toPrincipal(e);

        // One audit row so the (data-dependent) table + scroller render.
        AuditLog log = new AuditLog();
        log.setOccurredAtUtc(Instant.now());
        log.setEmployeeNo("E-AUDIT");
        log.setRoleCode("SYSTEM_ADMINISTRATOR");
        log.setModuleCode("AUTH");
        log.setActionCode("LOGIN");
        log.setResultCode("SUCCESS");
        auditLogRepository.save(log);
    }

    @Test
    void auditPageHasExportButtonAndHorizontalScroll() throws Exception {
        mockMvc.perform(get("/audit").with(authentication(token(admin))))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Export Excel")))
                .andExpect(content().string(containsString("audit-scroll")));
    }

    @Test
    void auditExportDownloadsExcelWithBoldHeaderHonouringFilters() throws Exception {
        mockMvc.perform(get("/audit/export.xls").param("module", "AUTH")
                        .with(authentication(token(admin))))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Type", containsString("ms-excel")))
                .andExpect(header().string("Content-Disposition", containsString("audit-trail.xls")))
                .andExpect(content().string(containsString("ss:Name=\"Audit Trail\"")))
                .andExpect(content().string(containsString("Time (IST)")))
                .andExpect(content().string(containsString("ss:ID=\"hdr\"")))
                .andExpect(content().string(containsString("ss:Bold=\"1\"")))
                .andExpect(content().string(containsString("<Cell ss:StyleID=\"hdr\">")));
    }

    private static UsernamePasswordAuthenticationToken token(ItsmUserPrincipal p) {
        return new UsernamePasswordAuthenticationToken(p, null, p.getAuthorities());
    }
}
