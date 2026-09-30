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
    private final SystemSettingsService systemSettingsService;

    public AdminUserService(EmployeeRepository employeeRepository,
                            RoleRepository roleRepository,
                            EmployeeRoleAssignmentRepository employeeRoleAssignmentRepository,
                            ConfigChangeRequestRepository configChangeRequestRepository,
                            RoleAdminService roleAdminService,
                            AuditRecorder auditRecorder,
                            SystemSettingsService systemSettingsService) {
        this.employeeRepository = employeeRepository;
        this.roleRepository = roleRepository;
        this.employeeRoleAssignmentRepository = employeeRoleAssignmentRepository;
        this.configChangeRequestRepository = configChangeRequestRepository;
        this.roleAdminService = roleAdminService;
        this.auditRecorder = auditRecorder;
        this.systemSettingsService = systemSettingsService;
    }

    @PreAuthorize("hasAuthority('ADMIN_USER_MANAGE')")
    @Transactional(readOnly = true)
    public List<Employee> list(String q) {
        return list(q, null);
    }

    /** Search by name / employee no / login ID; {@code status} "active", "inactive" or blank for all. */
    @PreAuthorize("hasAuthority('ADMIN_USER_MANAGE')")
    @Transactional(readOnly = true)
    public List<Employee> list(String q, String status) {
        List<Employee> found;
        if (q == null || q.trim().length() == 0) {
            found = employeeRepository.findAll();
        } else {
            String t = q.trim();
            found = employeeRepository
                    .findByDisplayNameContainingIgnoreCaseOrEmployeeNoContainingIgnoreCaseOrSamAccountNameContainingIgnoreCase(
                            t, t, t);
        }
        List<Employee> out = new java.util.ArrayList<Employee>();
        for (Employee e : found) {
            if ("active".equals(status) && !e.isPortalActive() || "inactive".equals(status) && e.isPortalActive()) {
                continue;
            }
            if (e.getManager() != null) {
                e.getManager().getDisplayName();
            }
            if (e.getDelegate() != null) {
                e.getDelegate().getDisplayName();
            }
            out.add(e);
        }
        out.sort(java.util.Comparator.comparing(Employee::getDisplayName, String.CASE_INSENSITIVE_ORDER));
        return out;
    }

    @PreAuthorize("hasAuthority('ADMIN_USER_MANAGE')")
    @Transactional(readOnly = true)
    public Employee get(Long id) {
        Employee employee = employeeRepository.findById(id)
                .orElseThrow(() -> new ItsmException("EMPLOYEE_NOT_FOUND", "Employee not found"));
        if (employee.getDelegate() != null) {
            employee.getDelegate().getDisplayName();
        }
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
        if (active && target.isPortalActive()) {
            throw new ItsmException("ALREADY_ACTIVE", target.getDisplayName() + " already has portal access.");
        }
        assertNotPending("USER_PORTAL_ACTIVE", String.valueOf(employeeId),
                "A change to " + target.getDisplayName() + "'s portal access is already waiting for approval.");
        Employee makerEmp = requireEmployee(maker.getEmployeeId());
        ConfigChangeRequest ccr = new ConfigChangeRequest();
        ccr.setChangeType("USER_PORTAL_ACTIVE");
        ccr.setEntityName("employee");
        ccr.setEntityKey(String.valueOf(employeeId));
        ccr.setPayloadJson("{\"portalActive\":" + active + ",\"employeeId\":" + employeeId + "}");
        ccr.setPreviousJson("{\"portalActive\":" + target.isPortalActive() + "}");
        ccr.setDescription((active ? "Enable" : "Disable") + " portal access for " + target.getEmployeeNo());
        ccr.setRequestedBy(makerEmp);
        ccr.setRequestedAtUtc(TimeUtc.now());
        return decide(ccr, maker, active ? "USER_REACTIVATE" : "USER_DEACTIVATE");
    }

    /**
     * A System Administrator is the final authority: their change is applied at once and recorded as Applied
     * (maker = reviewer). Anyone else's change waits for a System Administrator / checker in Config approvals.
     */
    ConfigChangeRequest decide(ConfigChangeRequest ccr, ItsmUserPrincipal maker, String appliedAction) {
        if (isSystemAdministrator(maker)) {
            apply(ccr, maker);
            ccr.setStatusCode("Applied");
            ccr.setReviewedBy(ccr.getRequestedBy());
            ccr.setReviewedAtUtc(TimeUtc.now());
            ccr.setDescription(ccr.getDescription() + " (applied by System Administrator)");
            configChangeRequestRepository.save(ccr);
            auditRecorder.record("ADMIN", appliedAction, ccr.getDescription(), "SUCCESS");
            return ccr;
        }
        ccr.setStatusCode("PendingApproval");
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
        if (maker.getEmployeeId().equals(employeeId)) {
            throw new ItsmException("SELF_ROLE", "You cannot change your own roles; ask another administrator.");
        }
        assertNotPending(assign ? "USER_ROLE_ASSIGN" : "USER_ROLE_REMOVE", employeeId + ":" + roleId,
                "This role change for " + target.getDisplayName() + " is already waiting for approval.");
        if (!assign) {
            assertAdminsRemainWithout(target, role);
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
        return decide(ccr, maker, assign ? "ROLE_ASSIGN" : "ROLE_REMOVE");
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

    /**
     * Deactivating {@code employee} must not leave the portal without an active employee who can manage
     * users and approve changes (the same guard as removing a role).
     */
    void assertAdminsRemainWithoutEmployee(Employee employee) {
        List<EmployeeRoleAssignment> all = employeeRoleAssignmentRepository.findAll();
        for (String permission : RoleAdminService.GUARDED_PERMISSIONS) {
            boolean held = false;
            boolean heldByOthers = false;
            for (EmployeeRoleAssignment a : all) {
                Employee holder = a.getEmployee();
                Role r = a.getRole();
                if (holder == null || r == null || !holder.isPortalActive() || !r.isActive()
                        || !RoleAdminService.codes(r).contains(permission)) {
                    continue;
                }
                held = true;
                if (!holder.getEmployeeId().equals(employee.getEmployeeId())) {
                    heldByOthers = true;
                }
            }
            if (held && !heldByOthers) {
                throw new ItsmException("ROLE_LOCKOUT", "Deactivating " + employee.getDisplayName()
                        + " would leave no active employee with " + permission + ".");
            }
        }
    }

    private void assertNotPending(String changeType, String entityKey, String message) {
        for (ConfigChangeRequest c : configChangeRequestRepository.findByStatusCodeOrderByRequestedAtUtcDesc("PendingApproval")) {
            if (changeType.equals(c.getChangeType()) && entityKey.equals(c.getEntityKey())) {
                throw new ItsmException("ALREADY_PENDING", message);
            }
        }
    }

    /** Pending change requests about this employee (role changes, reactivation), newest first. */
    @PreAuthorize("hasAuthority('ADMIN_USER_MANAGE')")
    @Transactional(readOnly = true)
    public List<ConfigChangeRequest> pendingFor(Long employeeId) {
        List<ConfigChangeRequest> out = new java.util.ArrayList<ConfigChangeRequest>();
        String id = String.valueOf(employeeId);
        for (ConfigChangeRequest c : configChangeRequestRepository.findByStatusCodeOrderByRequestedAtUtcDesc("PendingApproval")) {
            String key = c.getEntityKey();
            if (key != null && (key.equals(id) || key.startsWith(id + ":"))
                    && ("employee".equals(c.getEntityName()) || "employee_role".equals(c.getEntityName()))) {
                if (c.getRequestedBy() != null) {
                    c.getRequestedBy().getDisplayName();
                }
                out.add(c);
            }
        }
        return out;
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
        // Maker and checker differ, except for a System Administrator (final authority), who may confirm their own.
        if (ccr.getRequestedBy().getEmployeeId().equals(checker.getEmployeeId()) && !isSystemAdministrator(checker)) {
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
        if (ccr.getRequestedBy().getEmployeeId().equals(checker.getEmployeeId()) && !isSystemAdministrator(checker)) {
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
        if (SystemSettingsService.handles(ccr)) {
            systemSettingsService.apply(ccr);
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
