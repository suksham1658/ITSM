package com.nbfc.itsm.admin;

import com.nbfc.itsm.domain.ConfigChangeRequest;
import com.nbfc.itsm.domain.Employee;
import com.nbfc.itsm.domain.EmployeeRepository;
import com.nbfc.itsm.domain.EmployeeRoleAssignment;
import com.nbfc.itsm.domain.EmployeeRoleAssignmentRepository;
import com.nbfc.itsm.domain.Role;
import com.nbfc.itsm.domain.RoleRepository;
import com.nbfc.itsm.exception.ItsmException;
import com.nbfc.itsm.identity.PortalUserService;
import com.nbfc.itsm.security.ItsmUserPrincipal;
import com.nbfc.itsm.seed.CatalogSeedService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class RoleAdminServiceTest {

    @Autowired
    private CatalogSeedService catalogSeedService;
    @Autowired
    private RoleAdminService roleAdminService;
    @Autowired
    private AdminUserService adminUserService;
    @Autowired
    private PortalUserService portalUserService;
    @Autowired
    private EmployeeRepository employeeRepository;
    @Autowired
    private RoleRepository roleRepository;
    @Autowired
    private EmployeeRoleAssignmentRepository assignmentRepository;

    private ItsmUserPrincipal maker;
    private ItsmUserPrincipal checker;

    @BeforeEach
    void setUp() {
        catalogSeedService.ensureSeeded();
        maker = admin("E-RMAKER", "role.maker", "Role Maker");
        checker = admin("E-RCHECK", "role.checker", "Role Checker");
    }

    @AfterEach
    void clearContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void createIsPendingUntilADifferentAdminApproves() {
        actAs(maker);
        ConfigChangeRequest ccr = roleAdminService.proposeCreate(
                form("Procurement Approver", "REPORT_VIEW", "TICKET_APPROVE_ASSIGNED_STAGE"), maker);

        assertEquals("PendingApproval", ccr.getStatusCode());
        assertEquals(RoleAdminService.CREATE, ccr.getChangeType());
        assertFalse(roleRepository.findByCode("PROCUREMENT_APPROVER").isPresent(), "not applied before approval");

        actAs(checker);
        adminUserService.approve(ccr.getConfigChangeRequestId(), checker);

        Role role = roleRepository.findByCode("PROCUREMENT_APPROVER").orElseThrow(IllegalStateException::new);
        assertEquals("Procurement Approver", role.getName());
        assertFalse(role.isSystem());
        assertTrue(role.isActive());
        assertEquals(RoleAdminService.codes(role),
                new java.util.TreeSet<String>(Arrays.asList("REPORT_VIEW", "TICKET_APPROVE_ASSIGNED_STAGE")));
    }

    @Test
    void makerCannotApproveOwnRoleChange() {
        actAs(maker);
        ConfigChangeRequest ccr = roleAdminService.proposeCreate(form("Self Approved Role", "KB_READ"), maker);
        ItsmException ex = assertThrows(ItsmException.class,
                () -> adminUserService.approve(ccr.getConfigChangeRequestId(), maker));
        assertEquals("CCR_SAME_USER", ex.getCode());
        assertFalse(roleRepository.findByCode("SELF_APPROVED_ROLE").isPresent());
    }

    @Test
    void rejectedCreateNeverCreatesTheRole() {
        actAs(maker);
        ConfigChangeRequest ccr = roleAdminService.proposeCreate(form("Rejected Role", "KB_READ"), maker);
        actAs(checker);
        adminUserService.reject(ccr.getConfigChangeRequestId(), "Not needed for this quarter", checker);
        assertFalse(roleRepository.findByCode("REJECTED_ROLE").isPresent());
        assertThrows(ItsmException.class,
                () -> adminUserService.reject(ccr.getConfigChangeRequestId(), "again", checker));
    }

    @Test
    void createValidatesNamePermissionsAndDuplicates() {
        actAs(maker);
        assertEquals("ROLE_EXISTS", assertThrows(ItsmException.class,
                () -> roleAdminService.proposeCreate(form("it admin", "KB_READ"), maker)).getCode());
        assertEquals("ROLE_PERMISSIONS", assertThrows(ItsmException.class,
                () -> roleAdminService.proposeCreate(form("No Permission Role"), maker)).getCode());
        assertEquals("ROLE_PERMISSIONS", assertThrows(ItsmException.class,
                () -> roleAdminService.proposeCreate(form("Bad Permission Role", "NOT_A_PERMISSION"), maker)).getCode());
        assertEquals("ROLE_NAME", assertThrows(ItsmException.class,
                () -> roleAdminService.proposeCreate(form("<script>", "KB_READ"), maker)).getCode());

        roleAdminService.proposeCreate(form("Twice Proposed", "KB_READ"), maker);
        assertEquals("ROLE_PENDING", assertThrows(ItsmException.class,
                () -> roleAdminService.proposeCreate(form("Twice Proposed", "KB_READ"), maker)).getCode());
    }

    @Test
    void editChangesPermissionsOnlyAfterApproval() {
        Role role = createApproved("Branch Auditor", "KB_READ");

        actAs(maker);
        RoleForm edit = roleAdminService.formFor(role.getRoleId());
        edit.setName("Branch Auditor Lead");
        edit.setPermissionCodes(Arrays.asList("KB_READ", "REPORT_VIEW", "AUDIT_VIEW"));
        ConfigChangeRequest ccr = roleAdminService.proposeUpdate(role.getRoleId(), edit, maker);
        assertTrue(ccr.getDescription().contains("add AUDIT_VIEW, REPORT_VIEW"), ccr.getDescription());
        assertEquals("Branch Auditor", roleRepository.findById(role.getRoleId()).get().getName());

        assertEquals("ROLE_PENDING", assertThrows(ItsmException.class,
                () -> roleAdminService.proposeUpdate(role.getRoleId(), edit, maker)).getCode());

        actAs(checker);
        adminUserService.approve(ccr.getConfigChangeRequestId(), checker);
        Role updated = roleRepository.findById(role.getRoleId()).get();
        assertEquals("Branch Auditor Lead", updated.getName());
        assertEquals("BRANCH_AUDITOR", updated.getCode(), "code never changes");
        assertTrue(RoleAdminService.codes(updated).containsAll(Arrays.asList("AUDIT_VIEW", "REPORT_VIEW", "KB_READ")));
    }

    @Test
    void editWithoutChangesIsRejected() {
        Role role = createApproved("Unchanged Role", "KB_READ");
        actAs(maker);
        assertEquals("ROLE_NO_CHANGE", assertThrows(ItsmException.class,
                () -> roleAdminService.proposeUpdate(role.getRoleId(), roleAdminService.formFor(role.getRoleId()), maker))
                .getCode());
    }

    @Test
    void cannotRemoveTheLastAdministratorPermission() {
        for (Employee e : employeeRepository.findAll()) {
            if (!e.getEmployeeId().equals(maker.getEmployeeId()) && !e.getEmployeeId().equals(checker.getEmployeeId())) {
                e.setPortalActive(false);
            }
        }
        Role itAdmin = roleRepository.findByCode("IT_ADMIN").orElseThrow(IllegalStateException::new);
        actAs(maker);
        RoleForm edit = roleAdminService.formFor(itAdmin.getRoleId());
        edit.getPermissionCodes().remove("ADMIN_MASTERDATA_APPROVE");
        ItsmException ex = assertThrows(ItsmException.class,
                () -> roleAdminService.proposeUpdate(itAdmin.getRoleId(), edit, maker));
        assertEquals("ROLE_LOCKOUT", ex.getCode());
    }

    @Test
    void workflowApproverRoleCannotBeDeactivated() {
        Role ciso = roleRepository.findByCode("CISO").orElseThrow(IllegalStateException::new);
        actAs(maker);
        RoleForm edit = roleAdminService.formFor(ciso.getRoleId());
        edit.setActive(false);
        assertEquals("ROLE_IN_WORKFLOW", assertThrows(ItsmException.class,
                () -> roleAdminService.proposeUpdate(ciso.getRoleId(), edit, maker)).getCode());
    }

    @Test
    void readShowsHoldersAndGroupedPermissions() {
        actAs(maker);
        Role itAdmin = roleRepository.findByCode("IT_ADMIN").orElseThrow(IllegalStateException::new);
        RoleAdminService.RoleDetail detail = roleAdminService.get(itAdmin.getRoleId());
        assertTrue(detail.getHolders().stream().anyMatch(h -> h.getEmployeeNo().equals("E-RMAKER")));
        assertTrue(detail.getPermissionGroups().stream().anyMatch(g -> g.getName().equals("Administration")));

        List<RoleAdminService.RoleSummary> list = roleAdminService.list();
        assertTrue(list.stream().anyMatch(s -> s.getRole().getCode().equals("IT_ADMIN") && s.getHolderCount() >= 2));
    }

    @Test
    void employeeCannotReadOrProposeRoles() {
        Employee plain = employee("E-RPLAIN", "role.plain", "Plain Employee");
        grant(plain, "EMPLOYEE");
        ItsmUserPrincipal employee = portalUserService.toPrincipal(plain);
        actAs(employee);
        assertThrows(AccessDeniedException.class, () -> roleAdminService.list());
        assertThrows(AccessDeniedException.class,
                () -> roleAdminService.proposeCreate(form("Sneaky Role", "ADMIN_USER_MANAGE"), employee));
    }

    @Test
    void inactiveRoleCannotBeAssigned() {
        Role role = createApproved("Retired Desk", "KB_READ");
        role.setActive(false);
        Employee target = employee("E-RTARGET", "role.target", "Role Target");
        actAs(maker);
        assertEquals("ROLE_INACTIVE", assertThrows(ItsmException.class,
                () -> adminUserService.proposeRole(target.getEmployeeId(), role.getRoleId(), true, maker)).getCode());
    }

    @Test
    void systemAdministratorDeletesUnusedCustomRoleAndItsPermissions() {
        Role role = createApproved("Temporary Auditor", "KB_READ", "REPORT_VIEW");
        Long id = role.getRoleId();
        ItsmUserPrincipal sysAdmin = sysAdmin();
        actAs(sysAdmin);
        assertTrue(roleAdminService.deleteBlockers(id).isEmpty());

        roleAdminService.delete(id, sysAdmin);

        assertFalse(roleRepository.findById(id).isPresent());
        assertFalse(roleRepository.findByCode("TEMPORARY_AUDITOR").isPresent());
    }

    @Test
    void roleWithHoldersOrSeededRoleCannotBeDeleted() {
        Role role = createApproved("Held Custom Role", "KB_READ");
        grant(employeeRepository.findByEmployeeNo("E-RMAKER").get(), "HELD_CUSTOM_ROLE");
        ItsmUserPrincipal sysAdmin = sysAdmin();
        actAs(sysAdmin);
        assertTrue(roleAdminService.deleteBlockers(role.getRoleId()).get(0).contains("assigned to 1 employee"));
        assertEquals("ROLE_DELETE_BLOCKED", assertThrows(ItsmException.class,
                () -> roleAdminService.delete(role.getRoleId(), sysAdmin)).getCode());

        Role employeeRole = roleRepository.findByCode("EMPLOYEE").orElseThrow(IllegalStateException::new);
        assertEquals("ROLE_DELETE_BLOCKED", assertThrows(ItsmException.class,
                () -> roleAdminService.delete(employeeRole.getRoleId(), sysAdmin)).getCode());
        assertTrue(roleRepository.findById(employeeRole.getRoleId()).isPresent());
    }

    @Test
    void onlySystemAdministratorCanDeleteRoles() {
        Role role = createApproved("Not Deletable By IT", "KB_READ");
        actAs(maker);
        assertThrows(AccessDeniedException.class, () -> roleAdminService.delete(role.getRoleId(), maker));
        assertTrue(roleRepository.findById(role.getRoleId()).isPresent());
    }

    // ------------------------------------------------------------------ helpers

    private ItsmUserPrincipal sysAdmin() {
        Employee e = employee("E-RSYS", "role.sysadmin", "Role SysAdmin");
        grant(e, "SYSTEM_ADMINISTRATOR");
        return portalUserService.toPrincipal(e);
    }

    private Role createApproved(String name, String... permissions) {
        actAs(maker);
        ConfigChangeRequest ccr = roleAdminService.proposeCreate(form(name, permissions), maker);
        actAs(checker);
        adminUserService.approve(ccr.getConfigChangeRequestId(), checker);
        return roleRepository.findByCode(RoleAdminService.codeFor(name)).orElseThrow(IllegalStateException::new);
    }

    private ItsmUserPrincipal admin(String no, String sam, String name) {
        Employee e = employee(no, sam, name);
        grant(e, "IT_ADMIN");
        return portalUserService.toPrincipal(e);
    }

    private Employee employee(String no, String sam, String name) {
        Employee e = new Employee();
        e.setEmployeeNo(no);
        e.setSamAccountName(sam);
        e.setDisplayName(name);
        e.setEmail(sam + "@localhost");
        e.setPortalActive(true);
        return employeeRepository.save(e);
    }

    private void grant(Employee e, String roleCode) {
        Role role = roleRepository.findByCode(roleCode).orElseThrow(() -> new IllegalStateException(roleCode));
        EmployeeRoleAssignment row = new EmployeeRoleAssignment();
        row.setEmployee(e);
        row.setRole(role);
        assignmentRepository.save(row);
    }

    private static RoleForm form(String name, String... permissions) {
        RoleForm f = new RoleForm();
        f.setName(name);
        f.setPermissionCodes(new java.util.ArrayList<String>(Arrays.asList(permissions)));
        return f;
    }

    private static void actAs(ItsmUserPrincipal principal) {
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities()));
    }
}
