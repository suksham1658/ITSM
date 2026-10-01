package com.nbfc.itsm.identity;

import com.nbfc.itsm.domain.Employee;
import com.nbfc.itsm.domain.EmployeeRepository;
import com.nbfc.itsm.seed.CatalogSeedService;
import com.unboundid.ldap.listener.InMemoryDirectoryServer;
import com.unboundid.ldap.listener.InMemoryDirectoryServerConfig;
import com.unboundid.ldap.listener.InMemoryListenerConfig;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.support.TransactionTemplate;

import java.net.InetAddress;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;

/**
 * Logging in reads the user's {@code manager} DN from the directory and follows it upwards, so
 * the portal knows the reporting line a Service Request must climb.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class LdapHierarchySyncTest {

    private static final InMemoryDirectoryServer DIRECTORY = startDirectory();

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private EmployeeRepository employeeRepository;
    @Autowired
    private CatalogSeedService catalogSeedService;
    @Autowired
    private TransactionTemplate tx;

    @DynamicPropertySource
    static void ldapProperties(DynamicPropertyRegistry registry) {
        registry.add("itsm.ldap.url", () -> "ldap://127.0.0.1:" + DIRECTORY.getListenPort());
        registry.add("itsm.ldap.base-dn", () -> "dc=corp,dc=in");
        registry.add("itsm.ldap.user-dn-pattern", () -> "uid={0},ou=people,dc=corp,dc=in");
        registry.add("itsm.ldap.user-search-filter", () -> "(uid={0})");
        registry.add("itsm.ldap.bind-dn", () -> "");
        registry.add("itsm.ldap.bind-password", () -> "");
        registry.add("itsm.ldap.employee-id-attribute", () -> "employeeNumber");
    }

    @AfterAll
    static void stopDirectory() {
        DIRECTORY.shutDown(true);
    }

    @BeforeEach
    void seed() {
        catalogSeedService.ensureSeeded();
    }

    @Test
    void loginStoresTheWholeReportingLine() throws Exception {
        mockMvc.perform(post("/login").param("username", "asha").param("password", "Secret123!").with(csrf()))
                .andExpect(redirectedUrl("/"));

        tx.execute(status -> {
            Employee asha = employeeRepository.findByEmployeeNo("H100").orElseThrow(IllegalStateException::new);
            Employee ravi = asha.getManager();
            assertNotNull(ravi, "immediate manager linked");
            assertEquals("H200", ravi.getEmployeeNo());
            assertEquals("Ravi Menon", ravi.getDisplayName());
            assertTrue(ravi.isPortalActive(), "manager profile created active so they can approve");
            Employee neha = ravi.getManager();
            assertNotNull(neha, "manager's manager linked");
            assertEquals("H300", neha.getEmployeeNo());
            assertNull(neha.getManager(), "top of the chain");
            assertNotNull(asha.getDepartment(), "AD department mapped to a portal department");
            assertEquals("IT", asha.getDepartment().getCode());
            assertEquals("+91 22 4000 100", asha.getPhoneNumber(), "AD telephoneNumber stored");
            assertEquals("Mumbai HO, 5th floor", asha.getOfficeLocation(), "AD office stored");
            return null;
        });
    }

    private static InMemoryDirectoryServer startDirectory() {
        try {
            InMemoryDirectoryServerConfig config = new InMemoryDirectoryServerConfig("dc=corp,dc=in");
            config.setSchema(null);
            config.setListenerConfigs(InMemoryListenerConfig.createLDAPConfig(
                    "default", InetAddress.getByName("127.0.0.1"), 0, null));
            InMemoryDirectoryServer server = new InMemoryDirectoryServer(config);
            server.startListening();
            server.add("dn: dc=corp,dc=in", "objectClass: domain", "dc: corp");
            server.add("dn: ou=people,dc=corp,dc=in", "objectClass: organizationalUnit", "ou: people");
            person(server, "neha", "H300", "Neha Rao/Corp/IT", null);
            person(server, "ravi", "H200", "Ravi Menon", "uid=neha,ou=people,dc=corp,dc=in");
            person(server, "asha", "H100", "Asha Iyer", "uid=ravi,ou=people,dc=corp,dc=in");
            return server;
        } catch (Exception ex) {
            throw new IllegalStateException("Unable to start in-memory LDAP", ex);
        }
    }

    private static void person(InMemoryDirectoryServer server, String uid, String no, String cn, String managerDn)
            throws Exception {
        java.util.List<String> lines = new java.util.ArrayList<String>(java.util.Arrays.asList(
                "dn: uid=" + uid + ",ou=people,dc=corp,dc=in",
                "objectClass: inetOrgPerson", "objectClass: organizationalPerson", "objectClass: person",
                "objectClass: top", "uid: " + uid, "cn: " + cn, "displayName: " + cn, "sn: " + uid,
                "mail: " + uid + "@corp.in", "employeeNumber: " + no, "department: Information Technology",
                "telephoneNumber: +91 22 4000 " + no.substring(1), "physicalDeliveryOfficeName: Mumbai HO, 5th floor",
                "userPassword: Secret123!"));
        if (managerDn != null) {
            lines.add("manager: " + managerDn);
        }
        server.add(lines.toArray(new String[0]));
    }
}
