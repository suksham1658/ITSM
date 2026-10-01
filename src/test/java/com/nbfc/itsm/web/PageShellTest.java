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
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrlPattern;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Header / sidebar controls must work under the CSP {@code script-src 'self'}, which silently
 * blocks inline handlers such as {@code onclick="toggleSidebar()"}.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class PageShellTest {

    private static final Pattern INLINE_HANDLER = Pattern.compile("\\son[a-z]+\\s*=\\s*\"", Pattern.CASE_INSENSITIVE);

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

    private ItsmUserPrincipal user;

    @BeforeEach
    void setUp() {
        catalogSeedService.ensureSeeded();
        Employee e = new Employee();
        e.setEmployeeNo("E-SHELL");
        e.setSamAccountName("shell.user");
        e.setDisplayName("Shell User");
        e.setPortalActive(true);
        e = employeeRepository.save(e);
        EmployeeRoleAssignment row = new EmployeeRoleAssignment();
        row.setEmployee(e);
        row.setRole(roleRepository.findByCode("EMPLOYEE").orElseThrow(IllegalStateException::new));
        assignmentRepository.save(row);
        user = portalUserService.toPrincipal(e);
    }

    @Test
    void templatesContainNoInlineEventHandlers() throws IOException {
        Path root = Paths.get("src/main/resources/templates");
        List<String> hits = new ArrayList<String>();
        List<Path> files;
        try (Stream<Path> walk = Files.walk(root)) {
            files = walk.filter(p -> p.toString().endsWith(".html")).collect(Collectors.toList());
        }
        for (Path file : files) {
            String html = new String(Files.readAllBytes(file), StandardCharsets.UTF_8);
            Matcher m = INLINE_HANDLER.matcher(html);
            while (m.find()) {
                hits.add(root.relativize(file) + " offset " + m.start());
            }
        }
        assertTrue(hits.isEmpty(), "Inline handlers are blocked by the CSP; use data-action instead: " + hits);
    }

    @Test
    void noDeveloperPlaceholderTextIsShownToUsers() throws IOException {
        Pattern devText = Pattern.compile("Phase\\s*\\d|this phase|not wired|out of scope|seeded in SQL|view stub",
                Pattern.CASE_INSENSITIVE);
        List<String> hits = new ArrayList<String>();
        List<Path> files;
        try (Stream<Path> walk = Stream.concat(Files.walk(Paths.get("src/main/resources/templates")),
                Stream.of(Paths.get("src/main/resources/static/js/itsm.js"),
                        Paths.get("src/main/java/com/nbfc/itsm/web/PortalPageController.java"),
                        Paths.get("src/main/java/com/nbfc/itsm/web/AdminCatalogController.java")))) {
            files = walk.filter(p -> Files.isRegularFile(p)).collect(Collectors.toList());
        }
        for (Path file : files) {
            Matcher m = devText.matcher(new String(Files.readAllBytes(file), StandardCharsets.UTF_8));
            while (m.find()) {
                hits.add(file.getFileName() + ": " + m.group());
            }
        }
        assertTrue(hits.isEmpty(), "Developer placeholder text visible to users: " + hits);
    }

    @Test
    void shellControlsAreWiredThroughDataActions() throws Exception {
        mockMvc.perform(get("/tickets").with(authentication(token(user))))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("data-action=\"toggle-sidebar\"")))
                .andExpect(content().string(containsString("data-action=\"toggle-user-panel\"")))
                .andExpect(content().string(containsString("data-action=\"toggle-notif-panel\"")))
                .andExpect(content().string(containsString("action=\"/logout\"")))
                .andExpect(content().string(containsString("/js/itsm-")))
                .andExpect(content().string(not(containsString("onclick="))))
                .andExpect(content().string(not(containsString("id=\"topNav\""))));
    }

    @Test
    void brandingLoginAndDashboard() throws Exception {
        mockMvc.perform(get("/login"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("/images/authum-logo")))
                .andExpect(content().string(containsString("<div class=\"login-product\">IT Nexa</div>")))
                .andExpect(content().string(containsString("Next Starts Here")))
                .andExpect(content().string(not(containsString("Authum IT Nexa"))))
                .andExpect(content().string(not(containsString("One portal for every IT request"))))
                .andExpect(content().string(not(containsString("ITSM Portal"))));
        mockMvc.perform(get("/").with(authentication(token(user))))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("<strong>IT Nexa</strong>")))
                .andExpect(content().string(not(containsString("page-header-actions"))))
                .andExpect(content().string(not(containsString("ITSM Portal"))));
    }

    @Test
    void cssAndJsAreFingerprintedAndCachedByBrowsers() throws Exception {
        String page = mockMvc.perform(get("/login")).andReturn().getResponse().getContentAsString();
        Matcher m = Pattern.compile("href=\"(/css/itsm-[0-9a-f]+\\.css)\"").matcher(page);
        assertTrue(m.find(), "stylesheet link carries a content hash");
        mockMvc.perform(get(m.group(1)))
                .andExpect(status().isOk())
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.header()
                        .string("Cache-Control", containsString("max-age=31536000")));
    }

    @Test
    void logoutEndsTheSessionAndReturnsToLogin() throws Exception {
        MockHttpSession session = new MockHttpSession();
        mockMvc.perform(get("/tickets").session(session).with(authentication(token(user))))
                .andExpect(status().isOk());

        mockMvc.perform(post("/logout").session(session).with(authentication(token(user))).with(csrf()))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/login?logout"));
        assertTrue(session.isInvalid(), "session must be invalidated on logout");

        mockMvc.perform(get("/tickets"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrlPattern("**/login"));
        mockMvc.perform(get("/login").param("logout", ""))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("You have been signed out.")));
    }

    @Test
    void raisePageBacksOutToWhereTheUserCameFrom() throws Exception {
        mockMvc.perform(get("/tickets/raise").with(authentication(token(user)))
                        .header("Referer", "http://localhost/"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("class=\"page-back\" href=\"/\"")))
                .andExpect(content().string(containsString("Back to Dashboard")));
        mockMvc.perform(get("/tickets/raise").with(authentication(token(user))))
                .andExpect(content().string(containsString("class=\"page-back\" href=\"/tickets\"")))
                .andExpect(content().string(containsString("class=\"btn btn-danger-outline\" href=\"/tickets\"")));
    }

    @Test
    void errorPageOffersGoBack() throws Exception {
        mockMvc.perform(get("/403").with(authentication(token(user))))
                .andExpect(content().string(containsString("data-action=\"history-back\"")))
                .andExpect(content().string(containsString("Go back")));
    }

    @Test
    void logoutWithoutCsrfTokenIsRefused() throws Exception {
        mockMvc.perform(post("/logout").with(authentication(token(user))))
                .andExpect(status().isForbidden());
    }

    private static UsernamePasswordAuthenticationToken token(ItsmUserPrincipal p) {
        return new UsernamePasswordAuthenticationToken(p, null, p.getAuthorities());
    }
}
