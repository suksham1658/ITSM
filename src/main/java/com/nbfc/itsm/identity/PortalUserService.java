package com.nbfc.itsm.identity;

import com.nbfc.itsm.audit.AuditRecorder;
import com.nbfc.itsm.config.ItsmProperties;
import com.nbfc.itsm.domain.Department;
import com.nbfc.itsm.domain.DepartmentRepository;
import com.nbfc.itsm.domain.Employee;
import com.nbfc.itsm.domain.EmployeeRepository;
import com.nbfc.itsm.domain.EmployeeRoleAssignment;
import com.nbfc.itsm.domain.EmployeeRoleAssignmentRepository;
import com.nbfc.itsm.domain.Permission;
import com.nbfc.itsm.domain.Role;
import com.nbfc.itsm.domain.RoleRepository;
import com.nbfc.itsm.exception.ItsmException;
import com.nbfc.itsm.security.ItsmUserPrincipal;
import com.nbfc.itsm.util.TimeUtc;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.DisabledException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;

@Service
public class PortalUserService {

    private static final Logger log = LoggerFactory.getLogger(PortalUserService.class);

    private final EmployeeRepository employeeRepository;
    private final EmployeeRoleAssignmentRepository employeeRoleAssignmentRepository;
    private final DepartmentRepository departmentRepository;
    private final RoleRepository roleRepository;
    private final ItsmProperties properties;
    private final AuditRecorder auditRecorder;

    public PortalUserService(EmployeeRepository employeeRepository,
                             EmployeeRoleAssignmentRepository employeeRoleAssignmentRepository,
                             DepartmentRepository departmentRepository,
                             RoleRepository roleRepository,
                             ItsmProperties properties,
                             AuditRecorder auditRecorder) {
        this.employeeRepository = employeeRepository;
        this.employeeRoleAssignmentRepository = employeeRoleAssignmentRepository;
        this.departmentRepository = departmentRepository;
        this.roleRepository = roleRepository;
        this.properties = properties;
        this.auditRecorder = auditRecorder;
    }

    /**
     * Admin &gt; Users &gt; "Re-sync now from LDAP": the same attribute and manager-chain sync as a login,
     * read with the service account. Roles, delegate and portal access are never touched.
     */
    @Transactional
    public Employee resyncFromDirectory(Employee employee, LdapPerson person) {
        applyDirectoryAttributes(employee, person);
        employee.setLastLdapSyncUtc(TimeUtc.now());
        employeeRepository.save(employee);
        syncHierarchy(employee, person);
        return employee;
    }

    /**
     * Sync directory attributes onto an existing portal profile. Never creates employees
     * or assigns roles (IT Admin / SysAdmin only via {@code employee_role}).
     */
    @Transactional
    public ItsmUserPrincipal loadActivePrincipal(LdapPerson person) 
    {
        Employee employee = findProvisioned(person);
        if (employee == null) {
            //throw new DisabledException("not-provisioned");
            employee = createEmployeeFromLdap(person);

        }
        applyDirectoryAttributes(employee, person);
        employee.setLastLdapSyncUtc(TimeUtc.now());
        employeeRepository.save(employee);
        syncHierarchy(employee, person);
        if (!employee.isPortalActive()) {
            throw new DisabledException("portal-inactive");
        }
        applyBootstrapAdmin(employee);
        return toPrincipal(employee);
    }

    /**
     * First-run bootstrap for a new database: if this employee is listed in
     * {@code itsm.security.bootstrap-admins} (ITSM_BOOTSTRAP_ADMINS) and no portal-active employee
     * holds SYSTEM_ADMINISTRATOR yet, grant SYSTEM_ADMINISTRATOR and EMPLOYEE. Once a System
     * Administrator exists this does nothing, so a role removed later is never re-granted.
     */
    void applyBootstrapAdmin(Employee employee) {
        List<String> listed = properties.getSecurity().getBootstrapAdmins();
        if (listed == null || listed.isEmpty() || !isListed(employee, listed)) {
            return;
        }
        Role sys = roleRepository.findByCode("SYSTEM_ADMINISTRATOR").orElse(null);
        if (sys == null || !sys.isActive()) {
            log.warn("Bootstrap admin {} skipped: role SYSTEM_ADMINISTRATOR is missing or inactive", employee.getEmployeeNo());
            return;
        }
        for (EmployeeRoleAssignment a : employeeRoleAssignmentRepository.findByRole(sys)) {
            if (a.getEmployee() != null && a.getEmployee().isPortalActive()) {
                return;
            }
        }
        grant(employee, sys);
        roleRepository.findByCode("EMPLOYEE").ifPresent(r -> grant(employee, r));
        log.warn("BOOTSTRAP: {} ({}) granted SYSTEM_ADMINISTRATOR because no System Administrator existed. "
                + "Clear ITSM_BOOTSTRAP_ADMINS once set-up is done.", employee.getEmployeeNo(), employee.getDisplayName());
        auditRecorder.record("ADMIN", "BOOTSTRAP_ADMIN", employee.getEmployeeNo() + " granted SYSTEM_ADMINISTRATOR", "SUCCESS");
    }

    private static boolean isListed(Employee employee, List<String> listed) {
        for (String raw : listed) {
            String id = raw == null ? "" : raw.trim();
            if (id.isEmpty()) {
                continue;
            }
            if (id.equalsIgnoreCase(employee.getSamAccountName()) || id.equalsIgnoreCase(employee.getEmployeeNo())) {
                return true;
            }
        }
        return false;
    }

    private void grant(Employee employee, Role role) {
        if (employeeRoleAssignmentRepository.findByEmployeeAndRole(employee, role).isPresent()) {
            return;
        }
        EmployeeRoleAssignment row = new EmployeeRoleAssignment();
        row.setEmployee(employee);
        row.setRole(role);
        row.setAssignedAtUtc(TimeUtc.now());
        employeeRoleAssignmentRepository.save(row);
    }

    private Employee findProvisioned(LdapPerson person) {
        if (StringUtils.hasText(person.getEmployeeNo())) {
            Employee byNo = employeeRepository.findByEmployeeNo(person.getEmployeeNo()).orElse(null);
            if (byNo != null) {
                return byNo;
            }
        }
        if (StringUtils.hasText(person.getSamAccountName())) {
            return employeeRepository.findBySamAccountNameIgnoreCase(person.getSamAccountName()).orElse(null);
        }
        return null;
    }

    /**
     * Stores the directory's reporting line so Service Requests can move up it: each manager in
     * {@link LdapPerson#getManagerChain()} is found (or created, like a first login) and linked as
     * the {@code manager} of the person below. The department is set when the AD {@code department}
     * matches a portal department by name or code. Nothing is cleared when the directory has no
     * manager, so a manager set by an administrator is kept.
     */
    void syncHierarchy(Employee employee, LdapPerson person) {
        Department dept = matchDepartment(person.getDepartment());
        if (dept != null) {
            employee.setDepartment(dept);
        }
        Employee child = employee;
        for (LdapPerson m : person.getManagerChain()) {
            Employee manager = findProvisioned(m);
            if (manager == null) {
                manager = createEmployeeFromLdap(m);
                log.info("Created portal profile for manager {} ({}) from the directory",
                        manager.getEmployeeNo(), manager.getDisplayName());
            } else {
                applyDirectoryAttributes(manager, m);
            }
            Department managerDept = matchDepartment(m.getDepartment());
            if (managerDept != null) {
                manager.setDepartment(managerDept);
            }
            if (manager.getEmployeeId().equals(child.getEmployeeId())) {
                break;
            }
            child.setManager(manager);
            employeeRepository.save(child);
            child = manager;
        }
        employeeRepository.save(child);
    }

    private Department matchDepartment(String adDepartment) {
        if (!StringUtils.hasText(adDepartment)) {
            return null;
        }
        String wanted = adDepartment.trim();
        for (Department d : departmentRepository.findAll()) {
            if (wanted.equalsIgnoreCase(d.getName()) || wanted.equalsIgnoreCase(d.getCode())) {
                return d;
            }
        }
        return null;
    }

    private void applyDirectoryAttributes(Employee employee, LdapPerson person) {
        if (StringUtils.hasText(person.getDisplayName())) {
            employee.setDisplayName(person.getDisplayName());
        }
        if (StringUtils.hasText(person.getEmail())) {
            employee.setEmail(person.getEmail());
            employee.setUpn(person.getUpn() != null ? person.getUpn() : person.getEmail());
        }
        if (StringUtils.hasText(person.getDesignation())) {
            employee.setDesignation(person.getDesignation());
        }
        if (StringUtils.hasText(person.getSamAccountName())) {
            employee.setSamAccountName(person.getSamAccountName());
        }
    }

    public ItsmUserPrincipal toPrincipal(Employee employee) {
        return toPrincipal(employee, null);
    }

    /**
     * Builds the principal from the employee's active role assignments. {@code activeRoleCode}
     * narrows authorities to that one role (role switcher); {@code null} combines all roles.
     */
    public ItsmUserPrincipal toPrincipal(Employee employee, String activeRoleCode) {
        List<Role> roles = new ArrayList<Role>();
        for (EmployeeRoleAssignment assignment : employeeRoleAssignmentRepository.findByEmployee(employee)) {
            Role role = assignment.getRole();
            if (role != null && role.isActive()) {
                roles.add(role);
            }
        }
        Collections.sort(roles, new Comparator<Role>() {
            @Override
            public int compare(Role a, Role b) {
                return a.getName().compareToIgnoreCase(b.getName());
            }
        });
        List<ItsmUserPrincipal.AssignedRole> assigned = new ArrayList<ItsmUserPrincipal.AssignedRole>();
        for (Role role : roles) {
            Set<String> perms = new TreeSet<String>();
            if (role.getPermissions() != null) {
                for (Permission p : role.getPermissions()) {
                    perms.add(p.getCode());
                }
            }
            assigned.add(new ItsmUserPrincipal.AssignedRole(role.getCode(), role.getName(), perms));
        }
        String username = StringUtils.hasText(employee.getSamAccountName())
                ? employee.getSamAccountName()
                : employee.getEmployeeNo();
        return new ItsmUserPrincipal(employee.getEmployeeId(), employee.getEmployeeNo(), username,
                employee.getDisplayName(), assigned, activeRoleCode);
    }

    /**
     * Current view of a signed-in user, re-read from the database so role assignments, role
     * permission edits and portal disable take effect without a new login. Keeps the "View as"
     * role while it is still assigned. Returns {@code null} when the employee no longer exists or
     * is no longer portal-active (the session must then end).
     */
    @Transactional(readOnly = true)
    public ItsmUserPrincipal refresh(ItsmUserPrincipal current) {
        Employee employee = employeeRepository.findById(current.getEmployeeId()).orElse(null);
        if (employee == null || !employee.isPortalActive()) {
            return null;
        }
        ItsmUserPrincipal fresh = toPrincipal(employee, null);
        String active = current.getActiveRoleCode();
        if (active != null) {
            for (ItsmUserPrincipal.AssignedRole role : fresh.getAssignedRoles()) {
                if (role.getCode().equals(active)) {
                    return toPrincipal(employee, active);
                }
            }
        }
        return fresh;
    }

    /**
     * Re-reads the employee and their roles, then selects {@code roleCode} as the active role
     * ({@code null} or blank = all roles). Only roles currently assigned and active can be chosen.
     */
    @Transactional(readOnly = true)
    public ItsmUserPrincipal switchActiveRole(Long employeeId, String roleCode) {
        Employee employee = require(employeeId);
        if (!employee.isPortalActive()) {
            throw new DisabledException("portal-inactive");
        }
        ItsmUserPrincipal all = toPrincipal(employee, null);
        if (!StringUtils.hasText(roleCode)) {
            return all;
        }
        for (ItsmUserPrincipal.AssignedRole role : all.getAssignedRoles()) {
            if (role.getCode().equals(roleCode.trim())) {
                return toPrincipal(employee, role.getCode());
            }
        }
        throw new AccessDeniedException("Role " + roleCode + " is not assigned to you");
    }

    public Employee require(Long id) {
        return employeeRepository.findById(id)
                .orElseThrow(() -> new ItsmException("EMPLOYEE_NOT_FOUND", "Employee not found"));
    }
    
    private Employee createEmployeeFromLdap(LdapPerson person) {

    	System.out.println("===== LDAP DATA =====");
        System.out.println("Employee No     : " + person.getEmployeeNo());
        System.out.println("SAM Account     : " + person.getSamAccountName());
        System.out.println("Display Name    : " + person.getDisplayName());
        System.out.println("Email           : " + person.getEmail());
        System.out.println("Designation     : " + person.getDesignation());
        System.out.println("UPN             : " + person.getUpn());
        System.out.println("=====================");

        Employee employee = new Employee();

        employee.setEmployeeNo(person.getEmployeeNo());
        employee.setSamAccountName(person.getSamAccountName());
        employee.setDisplayName(person.getDisplayName());
        employee.setEmail(person.getEmail());
        employee.setDesignation(person.getDesignation());
        employee.setUpn(person.getUpn());

        employee.setPortalActive(true);

        employee.setLastLdapSyncUtc(TimeUtc.now());

        return employeeRepository.save(employee);
    }
}
