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
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Asset Management CRUD and the Audit Trail Excel export + horizontal scroll. */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class AssetAndAuditTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private CatalogSeedService catalogSeedService;
    @Autowired private PortalUserService portalUserService;
    @Autowired private EmployeeRepository employeeRepository;
    @Autowired private RoleRepository roleRepository;
    @Autowired private EmployeeRoleAssignmentRepository assignmentRepository;

    private ItsmUserPrincipal admin;

    @BeforeEach
    void setUp() {
        catalogSeedService.ensureSeeded();
        Employee e = new Employee();
        e.setEmployeeNo("E-ASSET");
        e.setSamAccountName("asset.admin");
        e.setDisplayName("Asset Admin");
        e.setPortalActive(true);
        e = employeeRepository.save(e);
        EmployeeRoleAssignment row = new EmployeeRoleAssignment();
        row.setEmployee(e);
        // System Administrator carries ASSET_MANAGE and AUDIT_VIEW in the seed.
        row.setRole(roleRepository.findByCode("SYSTEM_ADMINISTRATOR").orElseThrow(IllegalStateException::new));
        assignmentRepository.save(row);
        admin = portalUserService.toPrincipal(e);
    }

    @Test
    void assetListPageRenders() throws Exception {
        mockMvc.perform(get("/assets").with(authentication(token(admin))))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Asset Management")))
                .andExpect(content().string(containsString("Add asset")));
    }

    @Test
    void addAssetThenItShowsInTheList() throws Exception {
        mockMvc.perform(post("/assets").with(authentication(token(admin))).with(csrf())
                        .param("assetTag", "NB-TEST-1")
                        .param("assetType", "Laptop")
                        .param("statusCode", "IN_STOCK"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/assets"));

        mockMvc.perform(get("/assets").with(authentication(token(admin))))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("NB-TEST-1")));
    }

    @Test
    void duplicateTagIsRejectedWithAMessage() throws Exception {
        mockMvc.perform(post("/assets").with(authentication(token(admin))).with(csrf())
                .param("assetTag", "NB-DUP").param("assetType", "Laptop").param("statusCode", "IN_STOCK"));
        mockMvc.perform(post("/assets").with(authentication(token(admin))).with(csrf())
                        .param("assetTag", "NB-DUP").param("assetType", "Desktop").param("statusCode", "IN_STOCK"))
                .andExpect(status().isOk()) // re-renders the form, not a redirect
                .andExpect(content().string(containsString("already exists")));
    }

    @Test
    void auditPageHasExportButtonAndHorizontalScroll() throws Exception {
        // Creating an asset writes an audit row, so the scrollable table renders.
        mockMvc.perform(post("/assets").with(authentication(token(admin))).with(csrf())
                .param("assetTag", "NB-AUD").param("assetType", "Laptop").param("statusCode", "IN_STOCK"));
        mockMvc.perform(get("/audit").with(authentication(token(admin))))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Export Excel")))
                .andExpect(content().string(containsString("audit-scroll")));
    }

    @Test
    void auditExportDownloadsExcelHonouringFilters() throws Exception {
        mockMvc.perform(get("/audit/export.xls").param("module", "ASSET")
                        .with(authentication(token(admin))))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Type", containsString("ms-excel")))
                .andExpect(header().string("Content-Disposition", containsString("audit-trail.xls")))
                .andExpect(content().string(containsString("ss:Name=\"Audit Trail\"")))
                .andExpect(content().string(containsString("Time (IST)")));
    }

    private static UsernamePasswordAuthenticationToken token(ItsmUserPrincipal p) {
        return new UsernamePasswordAuthenticationToken(p, null, p.getAuthorities());
    }
}
