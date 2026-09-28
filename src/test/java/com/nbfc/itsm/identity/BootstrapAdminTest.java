package com.nbfc.itsm.identity;

import com.nbfc.itsm.config.ItsmProperties;
import com.nbfc.itsm.domain.Employee;
import com.nbfc.itsm.domain.EmployeeRepository;
import com.nbfc.itsm.domain.EmployeeRoleAssignment;
import com.nbfc.itsm.domain.EmployeeRoleAssignmentRepository;
import com.nbfc.itsm.domain.Role;
import com.nbfc.itsm.domain.RoleRepository;
import com.nbfc.itsm.security.ItsmUserPrincipal;
import com.nbfc.itsm.seed.CatalogSeedService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.util.Arrays;
import java.util.Collections;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** ITSM_BOOTSTRAP_ADMINS makes the first System Administrator on a new database, and only then. */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
class BootstrapAdminTest {

    @Autowired private CatalogSeedService catalogSeedService;
    @Autowired private PortalUserService portalUserService;
    @Autowired private ItsmProperties properties;
    @Autowired private EmployeeRepository employeeRepository;
    @Autowired private RoleRepository roleRepository;
    @Autowired private EmployeeRoleAssignmentRepository assignmentRepository;

    @BeforeEach
    void setUp() {
        catalogSeedService.ensureSeeded();
        // Simulate a new database: nobody else counts as an active System Administrator.
        for (Employee e : employeeRepository.findAll()) {
            e.setPortalActive(false);
        }
        properties.getSecurity().setBootstrapAdmins(Arrays.asList(" first.admin ", "E-BOOT-2"));
    }

    @AfterEach
    void reset() {
        properties.getSecurity().setBootstrapAdmins(Collections.<String>emptyList());
    }

    @Test
    void listedUserBecomesSystemAdministratorOnlyWhileNoneExists() {
        LdapPerson first = person("first.admin", "E-BOOT-1", "First Admin");
        ItsmUserPrincipal p1 = portalUserService.loadActivePrincipal(first);
        assertTrue(p1.getRoleCodes().contains("SYSTEM_ADMINISTRATOR"), "first listed login is bootstrapped");
        assertTrue(p1.getRoleCodes().contains("EMPLOYEE"));
        assertTrue(p1.has("ADMIN_USER_MANAGE"));

        // Second listed account: a System Administrator now exists, so nothing is granted.
        ItsmUserPrincipal p2 = portalUserService.loadActivePrincipal(person("second.admin", "E-BOOT-2", "Second Admin"));
        assertFalse(p2.getRoleCodes().contains("SYSTEM_ADMINISTRATOR"));
    }

    @Test
    void unlistedUserIsNeverBootstrapped() {
        ItsmUserPrincipal p = portalUserService.loadActivePrincipal(person("someone.else", "E-BOOT-9", "Someone Else"));
        assertTrue(p.getRoleCodes().isEmpty());
    }

    @Test
    void removedRoleIsNotReGrantedOnceAnAdminExists() {
        Employee other = new Employee();
        other.setEmployeeNo("E-BOOT-OTH");
        other.setSamAccountName("other.sys");
        other.setDisplayName("Existing SysAdmin");
        other.setPortalActive(true);
        other = employeeRepository.save(other);
        Role sys = roleRepository.findByCode("SYSTEM_ADMINISTRATOR").orElseThrow(IllegalStateException::new);
        EmployeeRoleAssignment row = new EmployeeRoleAssignment();
        row.setEmployee(other);
        row.setRole(sys);
        assignmentRepository.save(row);

        ItsmUserPrincipal p = portalUserService.loadActivePrincipal(person("first.admin", "E-BOOT-1", "First Admin"));
        assertFalse(p.getRoleCodes().contains("SYSTEM_ADMINISTRATOR"));
    }

    private static LdapPerson person(String sam, String no, String name) {
        LdapPerson p = new LdapPerson();
        p.setSamAccountName(sam);
        p.setEmployeeNo(no);
        p.setDisplayName(name);
        p.setDn("uid=" + sam + ",ou=people,dc=test");
        return p;
    }
}
