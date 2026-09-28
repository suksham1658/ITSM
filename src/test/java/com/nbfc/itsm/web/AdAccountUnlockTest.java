package com.nbfc.itsm.web;

import com.nbfc.itsm.domain.Employee;
import com.nbfc.itsm.domain.EmployeeRepository;
import com.nbfc.itsm.domain.EmployeeRoleAssignment;
import com.nbfc.itsm.domain.EmployeeRoleAssignmentRepository;
import com.nbfc.itsm.domain.NotificationRepository;
import com.nbfc.itsm.domain.RoleRepository;
import com.nbfc.itsm.identity.PortalUserService;
import com.nbfc.itsm.security.ItsmUserPrincipal;
import com.nbfc.itsm.seed.CatalogSeedService;
import com.unboundid.ldap.listener.InMemoryDirectoryServer;
import com.unboundid.ldap.listener.InMemoryDirectoryServerConfig;
import com.unboundid.ldap.listener.InMemoryListenerConfig;
import com.unboundid.ldap.sdk.Modification;
import com.unboundid.ldap.sdk.ModificationType;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.net.InetAddress;

import static org.hamcrest.Matchers.allOf;
import static org.hamcrest.Matchers.containsString;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.flash;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * IT Service Desk / System Administrator find a locked-out directory account and unlock it
 * (lockoutTime = 0 written with the service account); other roles cannot reach the page.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class AdAccountUnlockTest {

    private static final String LOCKED_AT = "133700000000000000"; // a 2024 FILETIME
    private static final InMemoryDirectoryServer DIRECTORY = startDirectory();

    @Autowired private MockMvc mockMvc;
    @Autowired private CatalogSeedService catalogSeedService;
    @Autowired private PortalUserService portalUserService;
    @Autowired private EmployeeRepository employeeRepository;
    @Autowired private RoleRepository roleRepository;
    @Autowired private EmployeeRoleAssignmentRepository assignmentRepository;
    @Autowired private NotificationRepository notificationRepository;

    @DynamicPropertySource
    static void ldapProperties(DynamicPropertyRegistry registry) {
        registry.add("itsm.ldap.url", () -> "ldap://127.0.0.1:" + DIRECTORY.getListenPort());
        registry.add("itsm.ldap.base-dn", () -> "dc=corp,dc=in");
        registry.add("itsm.ldap.user-dn-pattern", () -> "uid={0},ou=people,dc=corp,dc=in");
        registry.add("itsm.ldap.user-search-filter", () -> "(uid={0})");
        registry.add("itsm.ldap.account-search-filter", () -> "(|(uid={0}*)(displayName=*{0}*))");
        registry.add("itsm.ldap.bind-dn", () -> "cn=svc-itsm");
        registry.add("itsm.ldap.bind-password", () -> "SvcPass1!");
        registry.add("itsm.ldap.employee-id-attribute", () -> "employeeNumber");
    }

    @AfterAll
    static void stopDirectory() {
        DIRECTORY.shutDown(true);
    }

    @BeforeEach
    void seed() throws Exception {
        catalogSeedService.ensureSeeded();
        DIRECTORY.modify("uid=lina,ou=people,dc=corp,dc=in",
                new Modification(ModificationType.REPLACE, "lockoutTime", LOCKED_AT));
    }

    @Test
    void serviceDeskFindsAndUnlocksALockedAccount() throws Exception {
        Employee desk = employee("E-UL-SD", "Unlock Desk Agent", "EMPLOYEE", "IT_SERVICE_DESK");
        Employee lina = employee("H510", "Lina Das", "EMPLOYEE");
        lina.setSamAccountName("lina");
        employeeRepository.save(lina);
        UsernamePasswordAuthenticationToken agent = token(desk);

        mockMvc.perform(get("/ad-accounts").param("q", "lina").with(authentication(agent)))
                .andExpect(status().isOk())
                .andExpect(content().string(allOf(containsString("Lina Das"), containsString("Locked"),
                        containsString("/ad-accounts/account?id=lina"))));

        mockMvc.perform(get("/ad-accounts/account").param("id", "lina").with(authentication(agent)))
                .andExpect(status().isOk())
                .andExpect(content().string(allOf(containsString("Unlock account"), containsString("2024"))));

        mockMvc.perform(post("/ad-accounts/unlock").param("id", "lina")
                        .with(csrf()).with(authentication(agent)))
                .andExpect(flash().attribute("message", containsString("is unlocked")));
        assertEquals("0", lockoutTime("lina"), "lockoutTime cleared in the directory");
        assertEquals(1, notificationRepository.findAll().stream()
                .filter(n -> n.getRecipientId().equals(lina.getEmployeeId()) && n.getTitle().contains("unlocked")).count());

        mockMvc.perform(post("/ad-accounts/unlock").param("id", "lina")
                        .with(csrf()).with(authentication(agent)))
                .andExpect(flash().attribute("errorMessage", containsString("not locked out")));
    }

    @Test
    void systemAdministratorCanOpenThePage() throws Exception {
        Employee admin = employee("E-UL-SA", "Unlock Admin", "EMPLOYEE", "SYSTEM_ADMINISTRATOR");
        mockMvc.perform(get("/ad-accounts").param("q", "omar").with(authentication(token(admin))))
                .andExpect(status().isOk())
                .andExpect(content().string(allOf(containsString("Omar Khan"), containsString("Not locked"))));
    }

    @Test
    void otherRolesCannotUnlock() throws Exception {
        Employee plain = employee("E-UL-EMP", "Plain Employee", "EMPLOYEE", "IT_IMPLEMENTOR");
        UsernamePasswordAuthenticationToken t = token(plain);
        mockMvc.perform(get("/ad-accounts").with(authentication(t))).andExpect(status().isForbidden());
        mockMvc.perform(post("/ad-accounts/unlock").param("id", "lina")
                        .with(csrf()).with(authentication(t)))
                .andExpect(status().isForbidden());
        assertEquals(LOCKED_AT, lockoutTime("lina"));
    }

    private static String lockoutTime(String uid) throws Exception {
        return DIRECTORY.getEntry("uid=" + uid + ",ou=people,dc=corp,dc=in").getAttributeValue("lockoutTime");
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

    private static InMemoryDirectoryServer startDirectory() {
        try {
            InMemoryDirectoryServerConfig config = new InMemoryDirectoryServerConfig("dc=corp,dc=in");
            config.setSchema(null);
            config.addAdditionalBindCredentials("cn=svc-itsm", "SvcPass1!");
            config.setListenerConfigs(InMemoryListenerConfig.createLDAPConfig(
                    "default", InetAddress.getByName("127.0.0.1"), 0, null));
            InMemoryDirectoryServer server = new InMemoryDirectoryServer(config);
            server.startListening();
            server.add("dn: dc=corp,dc=in", "objectClass: domain", "dc: corp");
            server.add("dn: ou=people,dc=corp,dc=in", "objectClass: organizationalUnit", "ou: people");
            person(server, "lina", "H510", "Lina Das", LOCKED_AT);
            person(server, "omar", "H520", "Omar Khan", "0");
            return server;
        } catch (Exception ex) {
            throw new IllegalStateException("Unable to start in-memory LDAP", ex);
        }
    }

    private static void person(InMemoryDirectoryServer server, String uid, String no, String cn, String lockoutTime)
            throws Exception {
        server.add("dn: uid=" + uid + ",ou=people,dc=corp,dc=in",
                "objectClass: inetOrgPerson", "objectClass: person", "objectClass: top",
                "uid: " + uid, "cn: " + cn, "displayName: " + cn, "sn: " + uid, "mail: " + uid + "@corp.in",
                "employeeNumber: " + no, "department: Operations", "userPassword: Secret123!",
                "lockoutTime: " + lockoutTime, "badPwdCount: 5", "userAccountControl: 512");
    }
}
