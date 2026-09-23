package com.nbfc.itsm.web;

import com.nbfc.itsm.domain.Employee;
import com.nbfc.itsm.domain.EmployeeRepository;
import com.nbfc.itsm.domain.EmployeeRoleAssignment;
import com.nbfc.itsm.domain.EmployeeRoleAssignmentRepository;
import com.nbfc.itsm.domain.Permission;
import com.nbfc.itsm.domain.PermissionRepository;
import com.nbfc.itsm.domain.Role;
import com.nbfc.itsm.domain.RoleRepository;
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

import java.net.InetAddress;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class LdapLoginTest {

    private static final InMemoryDirectoryServer DIRECTORY = startDirectory();

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private EmployeeRepository employeeRepository;
    @Autowired
    private RoleRepository roleRepository;
    @Autowired
    private PermissionRepository permissionRepository;
    @Autowired
    private EmployeeRoleAssignmentRepository employeeRoleAssignmentRepository;

    @DynamicPropertySource
    static void ldapProperties(DynamicPropertyRegistry registry) {
        registry.add("itsm.ldap.url", () -> "ldap://127.0.0.1:" + DIRECTORY.getListenPort());
        registry.add("itsm.ldap.base-dn", () -> "dc=nbfc,dc=in");
        registry.add("itsm.ldap.user-dn-pattern", () -> "uid={0},ou=people,dc=nbfc,dc=in");
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
    void seedEmployee() {
        employeeRoleAssignmentRepository.deleteAll();
        employeeRepository.deleteAll();
        roleRepository.deleteAll();
        permissionRepository.deleteAll();

        Permission manage = new Permission();
        manage.setCode("ADMIN_USER_MANAGE");
        manage.setDescription("Manage portal users");
        permissionRepository.save(manage);

        Role admin = new Role();
        admin.setCode("IT_ADMIN");
        admin.setName("IT Admin");
        admin.setActive(true);
        admin.getPermissions().add(manage);
        roleRepository.save(admin);

        Employee jane = new Employee();
        jane.setEmployeeNo("E1001");
        jane.setSamAccountName("jdoe");
        jane.setDisplayName("Jane Doe");
        jane.setPortalActive(true);
        employeeRepository.save(jane);

        EmployeeRoleAssignment assignment = new EmployeeRoleAssignment();
        assignment.setEmployee(jane);
        assignment.setRole(admin);
        employeeRoleAssignmentRepository.save(assignment);

        Employee inactive = new Employee();
        inactive.setEmployeeNo("E1002");
        inactive.setSamAccountName("inactive");
        inactive.setDisplayName("Inactive User");
        inactive.setPortalActive(false);
        employeeRepository.save(inactive);
    }

    @Test
    void ldapLoginSucceedsWhenPortalProfileActive() throws Exception {
        mockMvc.perform(post("/login")
                        .param("username", "jdoe")
                        .param("password", "Secret123!")
                        .with(csrf()))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/"));
    }

    @Test
    void ldapLoginRejectedWhenPortalInactive() throws Exception {
        mockMvc.perform(post("/login")
                        .param("username", "inactive")
                        .param("password", "Secret123!")
                        .with(csrf()))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/login?error=denied"));
    }

    @Test
    void ldapLoginRejectedWhenNotProvisioned() throws Exception {
        mockMvc.perform(post("/login")
                        .param("username", "orphan")
                        .param("password", "Secret123!")
                        .with(csrf()))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/login?error=denied"));
    }

    @Test
    void ldapLoginRejectedOnBadPassword() throws Exception {
        mockMvc.perform(post("/login")
                        .param("username", "jdoe")
                        .param("password", "wrong")
                        .with(csrf()))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/login?error"));
    }

    private static InMemoryDirectoryServer startDirectory() {
        try {
            InMemoryDirectoryServerConfig config = new InMemoryDirectoryServerConfig("dc=nbfc,dc=in");
            config.setSchema(null);
            config.setListenerConfigs(InMemoryListenerConfig.createLDAPConfig(
                    "default", InetAddress.getByName("127.0.0.1"), 0, null));
            InMemoryDirectoryServer server = new InMemoryDirectoryServer(config);
            server.startListening();
            server.add("dn: dc=nbfc,dc=in", "objectClass: domain", "dc: nbfc");
            server.add("dn: ou=people,dc=nbfc,dc=in", "objectClass: organizationalUnit", "ou: people");
            addPerson(server, "jdoe", "E1001", "Jane Doe");
            addPerson(server, "inactive", "E1002", "Inactive User");
            addPerson(server, "orphan", "E9999", "Orphan User");
            return server;
        } catch (Exception ex) {
            throw new IllegalStateException("Unable to start in-memory LDAP", ex);
        }
    }

    private static void addPerson(InMemoryDirectoryServer server, String uid, String employeeNo, String cn)
            throws Exception {
        server.add(
                "dn: uid=" + uid + ",ou=people,dc=nbfc,dc=in",
                "objectClass: inetOrgPerson",
                "objectClass: organizationalPerson",
                "objectClass: person",
                "objectClass: top",
                "uid: " + uid,
                "cn: " + cn,
                "sn: " + uid,
                "sAMAccountName: " + uid,
                "mail: " + uid + "@nbfc.in",
                "employeeNumber: " + employeeNo,
                "userPassword: Secret123!");
    }
}
