package com.nbfc.itsm.identity;

import com.nbfc.itsm.domain.Employee;
import com.nbfc.itsm.domain.EmployeeRepository;
import com.nbfc.itsm.domain.EmployeeRoleAssignment;
import com.nbfc.itsm.domain.EmployeeRoleAssignmentRepository;
import com.nbfc.itsm.domain.Role;
import com.nbfc.itsm.domain.RoleRepository;
import com.nbfc.itsm.security.ItsmUserPrincipal;
import com.nbfc.itsm.seed.CatalogSeedService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.authentication.DisabledException;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class PortalUserServiceTest {

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

    @Test
    void unknownDirectoryUserIsNotSelfProvisioned() {
        catalogSeedService.ensureSeeded();
        long before = employeeRepository.count();
        LdapPerson person = new LdapPerson();
        person.setSamAccountName("ghost.user");
        person.setEmployeeNo("E-GHOST");
        person.setDisplayName("Ghost");
        assertThrows(DisabledException.class, () -> portalUserService.loadActivePrincipal(person));
        assertEquals(before, employeeRepository.count());
    }

    @Test
    void inactiveEmployeeCannotLoadPrincipal() {
        catalogSeedService.ensureSeeded();
        Employee emp = new Employee();
        emp.setEmployeeNo("E-INACT");
        emp.setSamAccountName("inactive.portal");
        emp.setDisplayName("Inactive Portal");
        emp.setEmail("inactive.portal@localhost");
        emp.setPortalActive(false);
        employeeRepository.save(emp);
        LdapPerson person = new LdapPerson();
        person.setSamAccountName("inactive.portal");
        person.setEmployeeNo("E-INACT");
        assertThrows(DisabledException.class, () -> portalUserService.loadActivePrincipal(person));
    }

    @Test
    void principalUsesAssignedRolesOnly() {
        catalogSeedService.ensureSeeded();
        Employee emp = new Employee();
        emp.setEmployeeNo("E-ROLES");
        emp.setSamAccountName("roles.only");
        emp.setDisplayName("Roles Only");
        emp.setEmail("roles.only@localhost");
        emp.setPortalActive(true);
        emp = employeeRepository.save(emp);
        Role employeeRole = roleRepository.findByCode("EMPLOYEE").orElseThrow(() -> new IllegalStateException("EMPLOYEE"));
        EmployeeRoleAssignment row = new EmployeeRoleAssignment();
        row.setEmployee(emp);
        row.setRole(employeeRole);
        assignmentRepository.save(row);
        ItsmUserPrincipal principal = portalUserService.toPrincipal(emp);
        assertTrue(principal.has("TICKET_CREATE"));
        assertFalse(principal.has("ADMIN_USER_MANAGE"));
        assertFalse(principal.has("REPORT_VIEW"));
        assertEquals(1, principal.getRoleCodes().size());
        assertEquals("EMPLOYEE", principal.getRoleCodes().get(0));
    }
}
