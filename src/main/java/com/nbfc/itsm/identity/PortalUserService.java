package com.nbfc.itsm.identity;

import com.nbfc.itsm.domain.Employee;
import com.nbfc.itsm.domain.EmployeeRepository;
import com.nbfc.itsm.domain.EmployeeRoleAssignment;
import com.nbfc.itsm.domain.EmployeeRoleAssignmentRepository;
import com.nbfc.itsm.domain.Permission;
import com.nbfc.itsm.domain.Role;
import com.nbfc.itsm.exception.ItsmException;
import com.nbfc.itsm.security.ItsmUserPrincipal;
import com.nbfc.itsm.util.TimeUtc;
import org.springframework.security.authentication.DisabledException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.LinkedHashSet;
import java.util.Set;

@Service
public class PortalUserService {

    private final EmployeeRepository employeeRepository;
    private final EmployeeRoleAssignmentRepository employeeRoleAssignmentRepository;

    public PortalUserService(EmployeeRepository employeeRepository,
                             EmployeeRoleAssignmentRepository employeeRoleAssignmentRepository) {
        this.employeeRepository = employeeRepository;
        this.employeeRoleAssignmentRepository = employeeRoleAssignmentRepository;
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
        if (!employee.isPortalActive()) {
            throw new DisabledException("portal-inactive");
        }
        return toPrincipal(employee);
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
        Set<String> perms = new LinkedHashSet<String>();
        Set<String> roleCodes = new LinkedHashSet<String>();
        java.util.List<EmployeeRoleAssignment> assignments = employeeRoleAssignmentRepository.findByEmployee(employee);
        for (EmployeeRoleAssignment assignment : assignments) {
            Role role = assignment.getRole();
            if (role == null || !role.isActive()) {
                continue;
            }
            roleCodes.add(role.getCode());
            if (role.getPermissions() != null) {
                for (Permission p : role.getPermissions()) {
                    perms.add(p.getCode());
                }
            }
        }
        String username = StringUtils.hasText(employee.getSamAccountName())
                ? employee.getSamAccountName()
                : employee.getEmployeeNo();
        return new ItsmUserPrincipal(employee.getEmployeeId(), employee.getEmployeeNo(), username,
                employee.getDisplayName(), perms, roleCodes);
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
