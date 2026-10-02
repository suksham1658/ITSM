package com.nbfc.itsm.web;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Arrays;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Security headers, no version disclosure, lock-out message, safe CSV export. */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class SecurityHardeningTest {

    @Autowired private MockMvc mockMvc;

    @Test
    void loginPageSendsTheSecurityHeaders() throws Exception {
        mockMvc.perform(get("/login"))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Security-Policy", containsString("frame-ancestors 'none'")))
                .andExpect(header().string("X-Frame-Options", "DENY"))
                .andExpect(header().string("X-Content-Type-Options", "nosniff"))
                .andExpect(header().string("Permissions-Policy", containsString("camera=()")));
    }

    @Test
    void infoEndpointDoesNotRevealVersions() throws Exception {
        mockMvc.perform(get("/actuator/info"))
                .andExpect(content().string(not(containsString("2.7"))))
                .andExpect(content().string(not(containsString("spring-boot"))));
    }

    @Test
    void lockedOutLoginShowsAClearMessage() throws Exception {
        mockMvc.perform(get("/login").param("error", "locked"))
                .andExpect(content().string(containsString("Too many failed sign-ins")));
    }

    @Test
    void csvCellsCannotRunFormulasInExcel() {
        assertEquals("'=HYPERLINK(\"\"http://evil\"\")", ReportController.csvLine(Arrays.asList("=HYPERLINK(\"http://evil\")"))
                .replaceAll("^\"|\"$", ""));
        assertEquals("'@SUM(A1),'+cmd,-12,15%", ReportController.csvLine(Arrays.asList("@SUM(A1)", "+cmd", "-12", "15%"))
                .replace("\"", ""));
    }
}
