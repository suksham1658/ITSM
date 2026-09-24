package com.nbfc.itsm.admin;

import com.nbfc.itsm.audit.AuditRecorder;
import com.nbfc.itsm.domain.ConfigChangeRequest;
import com.nbfc.itsm.domain.ConfigChangeRequestRepository;
import com.nbfc.itsm.domain.Employee;
import com.nbfc.itsm.domain.EmployeeRepository;
import com.nbfc.itsm.domain.EmployeeRoleAssignment;
import com.nbfc.itsm.domain.EmployeeRoleAssignmentRepository;
import com.nbfc.itsm.domain.EmployeeRoleId;
import com.nbfc.itsm.domain.Role;
import com.nbfc.itsm.domain.RoleRepository;
import com.nbfc.itsm.exception.ItsmException;
import com.nbfc.itsm.security.ItsmUserPrincipal;
import com.nbfc.itsm.util.TimeUtc;
import com.nbfc.itsm.validation.FieldLimits;
import com.nbfc.itsm.validation.Validation;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * User administration. Sensitive changes go through {@code config_change_request}
 * (maker-checker). Employees cannot assign their own roles.
 */
@Service
public class AdminUserService {

    private final EmployeeRepository employeeRepository;
    private final RoleRepository roleRepository;
    private final EmployeeRoleAssignmentRepository employeeRoleAssignmentRepository;
    private final ConfigChangeRequestRepository configChangeRequestRepository;
    private final RoleAdminService roleAdminService;
    private final AuditRecorder auditRecorder;

    public AdminUserService(EmployeeRepository employeeRepository,
                            RoleRepository roleRepository,
                            EmployeeRoleAssignmentRepository employeeRoleAssignmentRepository,
                            ConfigChangeRequestRepository configChangeRequestRepository,
                            RoleAdminService roleAdminService,
                            AuditRecorder auditRecorder) {
        this.employeeRepository = employeeRepository;
        this.roleRepository = roleRepository;
        this.employeeRoleAssignmentRepository = employeeRoleAssignmentRepository;
        this.configChangeRequestRepository = configChangeRequestRepository;
        this.roleAdminService = roleAdminService;
        this.auditRecorder = auditRecorder;
    }

    @PreAuthorize("hasAuthority('ADMIN_USER_MANAGE')")
    @Transactional(readOnly = true)
    public List<Employee> list(String q) {
        if (q == null || q.trim().length() == 0) {
            return employeeRepository.findAll();
        }
        String t = q.trim();
        return employeeRepository
                .findByDisplayNameContainingIgnoreCaseOrEmployeeNoContainingIgnoreCaseOrSamAccountNameContainingIgnoreCase(
                        t, t, t);
    }

    @PreAuthorize("hasAuthority('ADMIN_USER_MANAGE')")
    @Transactional(readOnly = true)
    public Employee get(Long id) {
        Employee employee = employeeRepository.findById(id)
                .orElseThrow(() -> new ItsmException("EMPLOYEE_NOT_FOUND", "Employee not found"));
        if (employee.getRoleAssignments() != null) {
            employee.getRoleAssignments().size();
            for (EmployeeRoleAssignment assignment : employee.getRoleAssignments()) {
                if (assignment.getRole() != null) {
                    assignment.getRole().getCode();
                }
            }
        }
        return employee;
    }

    @PreAuthorize("hasAuthority('ADMIN_USER_MANAGE')")
    @Transactional(readOnly = true)
    public List<Role> roles() {
        return roleRepository.findAll();
    }

    private Employee requireEmployee(Long id) {
        return employeeRepository.findById(id)
                .orElseThrow(() -> new ItsmException("EMPLOYEE_NOT_FOUND", "Employee not found"));
    }

    @PreAuthorize("hasAuthority('ADMIN_MASTERDATA_PROPOSE')")
    @Transactional
    public ConfigChangeRequest proposePortalActive(Long employeeId, boolean active, ItsmUserPrincipal maker) {
        Employee target = requireEmployee(employeeId);
        Employee makerEmp = requireEmployee(maker.getEmployeeId());
        ConfigChangeRequest ccr = new ConfigChangeRequest();
        ccr.setChangeType("USER_PORTAL_ACTIVE");
        ccr.setEntityName("employee");
        ccr.setEntityKey(String.valueOf(employeeId));
        ccr.setPayloadJson("{\"portalActive\":" + active + ",\"employeeId\":" + employeeId + "}");
        ccr.setPreviousJson("{\"portalActive\":" + target.isPortalActive() + "}");
        ccr.setDescription((active ? "Enable" : "Disable") + " portal access for " + target.getEmployeeNo());
        ccr.setStatusCode("PendingApproval");
        ccr.setRequestedBy(makerEmp);
        ccr.setRequestedAtUtc(TimeUtc.now());
        configChangeRequestRepository.save(ccr);
        auditRecorder.record("ADMIN", "PROPOSE", ccr.getDescription(), "SUCCESS");
        return ccr;
    }

    @PreAuthorize("hasAuthority('ADMIN_MASTERDATA_PROPOSE')")
    @Transactional
    public ConfigChangeRequest proposeRole(Long employeeId, Long roleId, boolean assign, ItsmUserPrincipal maker) {
        Employee target = requireEmployee(employeeId);
        Role role = roleRepository.findById(roleId)
                .orElseThrow(() -> new ItsmException("ROLE_NOT_FOUND", "Role not found"));
        if (assign && !role.isActive()) {
            throw new ItsmException("ROLE_INACTIVE", "Role " + role.getCode() + " is inactive and cannot be assigned.");
        }
        Employee makerEmp = requireEmployee(maker.getEmployeeId());
        ConfigChangeRequest ccr = new ConfigChangeRequest();
        ccr.setChangeType(assign ? "USER_ROLE_ASSIGN" : "USER_ROLE_REMOVE");
        ccr.setEntityName("employee_role");
        ccr.setEntityKey(employeeId + ":" + roleId);
        ccr.setPayloadJson("{\"employeeId\":" + employeeId + ",\"roleId\":" + roleId + ",\"assign\":" + assign + "}");
        ccr.setDescription((assign ? "Assign " : "Remove ") + role.getCode() + " for " + target.getEmployeeNo());
        ccr.setRequestedBy(makerEmp);
        ccr.setRequestedAtUtc(TimeUtc.now());

        if (isSystemAdministrator(maker)) {
            // Top of the admin tier: applied at once, nothing is forwarded for approval. Still recorded
            // as an Applied change request (maker = reviewer) so Config approvals keeps the history.
            ccr.setDescription(ccr.getDescription() + " (applied directly by System Administrator)");
            apply(ccr, maker);
            ccr.setStatusCode("Applied");
            ccr.setReviewedBy(makerEmp);
            ccr.setReviewedAtUtc(TimeUtc.now());
            configChangeRequestRepository.save(ccr);
            auditRecorder.record("ADMIN", assign ? "ROLE_ASSIGN" : "ROLE_REMOVE", ccr.getDescription(), "SUCCESS");
            return ccr;
        }

        ccr.setStatusCode("PendingApproval");
        configChangeRequestRepository.save(ccr);
        auditRecorder.record("ADMIN", "PROPOSE", ccr.getDescription(), "SUCCESS");
        return ccr;
    }

    /**
     * Removing {@code role} from {@code employee} must not leave the portal without an active
     * employee who can manage users and approve changes.
     */
    private void assertAdminsRemainWithout(Employee employee, Role role) {
        List<EmployeeRoleAssignment> all = employeeRoleAssignmentRepository.findAll();
        for (String permission : RoleAdminService.GUARDED_PERMISSIONS) {
            boolean before = false;
            boolean after = false;
            for (EmployeeRoleAssignment a : all) {
                Employee holder = a.getEmployee();
                Role r = a.getRole();
                if (holder == null || r == null || !holder.isPortalActive() || !r.isActive()
                        || !RoleAdminService.codes(r).contains(permission)) {
                    continue;
                }
                before = true;
                boolean removed = holder.getEmployeeId().equals(employee.getEmployeeId())
                        && r.getRoleId().equals(role.getRoleId());
                if (!removed) {
                    after = true;
                }
            }
            if (before && !after) {
                throw new ItsmException("ROLE_LOCKOUT", "Removing " + role.getCode() + " from "
                        + employee.getEmployeeNo() + " would leave no active employee with " + permission + ".");
            }
        }
    }

    /** System Administrator (as the active role) is the top of the admin tier. */
    public static boolean isSystemAdministrator(ItsmUserPrincipal principal) {
        return principal != null && principal.getRoleCodes().contains("SYSTEM_ADMINISTRATOR");
    }

    @PreAuthorize("hasAuthority('ADMIN_MASTERDATA_APPROVE')")
    @Transactional(readOnly = true)
    public List<ConfigChangeRequest> pending() {
        List<ConfigChangeRequest> list =
                configChangeRequestRepository.findByStatusCodeOrderByRequestedAtUtcDesc("PendingApproval");
        touchRequester(list);
        return list;
    }

    @PreAuthorize("hasAuthority('ADMIN_MASTERDATA_APPROVE')")
    @Transactional(readOnly = true)
    public List<ConfigChangeRequest> allChanges() {
        List<ConfigChangeRequest> list = configChangeRequestRepository.findAllByOrderByRequestedAtUtcDesc();
        touchRequester(list);
        return list;
    }

    private void touchRequester(List<ConfigChangeRequest> list) {
        for (ConfigChangeRequest ccr : list) {
            if (ccr.getRequestedBy() != null) {
                ccr.getRequestedBy().getEmployeeNo();
            }
        }
    }

    @PreAuthorize("hasAuthority('ADMIN_MASTERDATA_APPROVE')")
    @Transactional
    public void approve(Long ccrId, ItsmUserPrincipal checker) {
        ConfigChangeRequest ccr = configChangeRequestRepository.findById(ccrId)
                .orElseThrow(() -> new ItsmException("CCR_NOT_FOUND", "Change request not found"));
        if (!"PendingApproval".equals(ccr.getStatusCode())) {
            throw new ItsmException("CCR_NOT_PENDING", "Request is not pending");
        }
        if (ccr.getRequestedBy().getEmployeeId().equals(checker.getEmployeeId())) {
            throw new ItsmException("CCR_SAME_USER", "Maker and checker must be different administrators");
        }
        apply(ccr, checker);
        ccr.setStatusCode("Applied");
        ccr.setReviewedBy(requireEmployee(checker.getEmployeeId()));
        ccr.setReviewedAtUtc(TimeUtc.now());
        auditRecorder.record("ADMIN", "APPROVE", ccr.getDescription(), "SUCCESS");
    }

    @PreAuthorize("hasAuthority('ADMIN_MASTERDATA_APPROVE')")
    @Transactional
    public void reject(Long ccrId, String reason, ItsmUserPrincipal checker) {
        ConfigChangeRequest ccr = configChangeRequestRepository.findById(ccrId)
                .orElseThrow(() -> new ItsmException("CCR_NOT_FOUND", "Change request not found"));
        if (!"PendingApproval".equals(ccr.getStatusCode())) {
            throw new ItsmException("CCR_NOT_PENDING", "Request is not pending");
        }
        if (ccr.getRequestedBy().getEmployeeId().equals(checker.getEmployeeId())) {
            throw new ItsmException("CCR_SAME_USER", "Maker and checker must be different administrators");
        }
        if (reason == null || reason.trim().length() == 0) {
            throw new ItsmException("REASON_REQUIRED", "Rejection remarks are required");
        }
        Validation v = new Validation();
        v.text(reason, "Rejection remarks", FieldLimits.REJECT_REASON_MIN, FieldLimits.REJECT_REASON_MAX, true);
        v.throwIfInvalid("REASON_INVALID");
        ccr.setStatusCode("Rejected");
        ccr.setRejectReason(reason.trim());
        ccr.setReviewedBy(requireEmployee(checker.getEmployeeId()));
        ccr.setReviewedAtUtc(TimeUtc.now());
        auditRecorder.record("ADMIN", "REJECT", ccr.getDescription(), "SUCCESS");
    }

    private void apply(ConfigChangeRequest ccr, ItsmUserPrincipal checker) {
        if (RoleAdminService.handles(ccr)) {
            roleAdminService.apply(ccr);
            return;
        }
        String payload = ccr.getPayloadJson();
        if ("USER_PORTAL_ACTIVE".equals(ccr.getChangeType())) {
            Long employeeId = Long.valueOf(ccr.getEntityKey());
            boolean active = payload.indexOf("\"portalActive\":true") >= 0;
            Employee e = requireEmployee(employeeId);
            e.setPortalActive(active);
            return;
        }
        if ("USER_ROLE_ASSIGN".equals(ccr.getChangeType()) || "USER_ROLE_REMOVE".equals(ccr.getChangeType())) {
            String[] parts = ccr.getEntityKey().split(":");
            Long employeeId = Long.valueOf(parts[0]);
            Long roleId = Long.valueOf(parts[1]);
            Employee e = requireEmployee(employeeId);
            Role role = roleRepository.findById(roleId)
                    .orElseThrow(() -> new ItsmException("ROLE_NOT_FOUND", "Role not found"));
            boolean assign = "USER_ROLE_ASSIGN".equals(ccr.getChangeType());
            if (assign) {
                if (!employeeRoleAssignmentRepository.findByEmployeeAndRole(e, role).isPresent()) {
                    EmployeeRoleAssignment row = new EmployeeRoleAssignment();
                    row.setId(new EmployeeRoleId(employeeId, roleId));
                    row.setEmployee(e);
                    row.setRole(role);
                    row.setAssignedAtUtc(TimeUtc.now());
                    row.setAssignedBy(requireEmployee(checker.getEmployeeId()));
                    employeeRoleAssignmentRepository.save(row);
                }
            } else {
                assertAdminsRemainWithout(e, role);
                employeeRoleAssignmentRepository.deleteByEmployeeAndRole(e, role);
            }
        }
    }
}
