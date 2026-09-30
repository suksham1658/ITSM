package com.nbfc.itsm.web;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrlPattern;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class LoginPageTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void loginPageIsPublic() throws Exception {
        mockMvc.perform(get("/login"))
                .andExpect(status().isOk())
                .andExpect(view().name("login"));
    }

    @Test
    void dashboardRequiresAuthentication() throws Exception {
        mockMvc.perform(get("/"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrlPattern("**/login"));
    }

    @Test
    void ticketsRaiseUnauthorizedRedirectsToLogin() throws Exception {
        mockMvc.perform(get("/tickets/raise"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrlPattern("**/login"));
    }

    @Test
    void adminUsersUnauthorizedRedirectsToLogin() throws Exception {
        mockMvc.perform(get("/admin/users"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrlPattern("**/login"));
    }

    @Test
    @WithMockUser(username = "emp1", authorities = "TICKET_CREATE")
    void adminUsersForbiddenWithoutRole() throws Exception {
        mockMvc.perform(get("/admin/users"))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(username = "emp1", authorities = "TICKET_CREATE")
    void reportsForbiddenWithoutPermission() throws Exception {
        mockMvc.perform(get("/reports"))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(username = "rpt1", authorities = "REPORT_VIEW")
    void reportsAllowedWithPermission() throws Exception {
        mockMvc.perform(get("/reports"))
                .andExpect(status().isOk())
                .andExpect(view().name("reports"));
    }

    @Test
    @WithMockUser(username = "admin1", authorities = "ADMIN_USER_MANAGE")
    void adminUsersAllowedWithPermission() throws Exception {
        mockMvc.perform(get("/admin/users"))
                .andExpect(status().isOk())
                .andExpect(view().name("admin/users"));
    }

    @Test
    @WithMockUser(username = "emp1", authorities = {"TICKET_CREATE", "TICKET_VIEW_OWN", "KB_READ"})
    void dashboardAndKbLoad() throws Exception {
        mockMvc.perform(get("/"))
                .andExpect(status().isOk())
                .andExpect(view().name("dashboard"))
                .andExpect(content().string(containsString("viewport")));
        mockMvc.perform(get("/kb"))
                .andExpect(status().isOk())
                .andExpect(view().name("kb"));
        mockMvc.perform(get("/tickets/raise"))
                .andExpect(status().isOk())
                .andExpect(view().name("tickets/raise"))
                .andExpect(content().string(containsString("viewport")))
                .andExpect(content().string(containsString("Raise IT Request")));
    }

    @Test
    void reportsAnonymousRedirectsToLogin() throws Exception {
        mockMvc.perform(get("/reports"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrlPattern("**/login"));
    }

    @Test
    void loginPostWithoutCsrfIsNotProcessed() throws Exception {
        // Refused before authentication (credentials never checked); back to the login page with a clear message.
        mockMvc.perform(post("/login")
                        .param("username", "jdoe")
                        .param("password", "Secret123!"))
                .andExpect(redirectedUrl("/login?ended=1"))
                .andExpect(org.springframework.security.test.web.servlet.response.SecurityMockMvcResultMatchers.unauthenticated());
    }

    @Test
    @WithMockUser(username = "emp1", authorities = "TICKET_CREATE")
    void raisePostWithoutCsrfIsForbidden() throws Exception {
        mockMvc.perform(post("/tickets/raise")
                        .param("subject", "No CSRF"))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(username = "rpt1", authorities = "REPORT_VIEW")
    void reportsPageRendersNamedReports() throws Exception {
        mockMvc.perform(get("/reports"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Ticket Volume")))
                .andExpect(content().string(containsString("SLA Performance")))
                .andExpect(content().string(containsString("viewport")));
    }

    @Test
    @WithMockUser(username = "emp1", authorities = {"TICKET_CREATE", "TICKET_VIEW_OWN"})
    void loginWithCsrfStillRequiresDirectory() throws Exception {
        mockMvc.perform(post("/login")
                        .param("username", "emp1")
                        .param("password", "nope")
                        .with(csrf()))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/login?error"));
    }
}
