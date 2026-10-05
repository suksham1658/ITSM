package com.nbfc.itsm.admin;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nbfc.itsm.audit.AuditRecorder;
import com.nbfc.itsm.domain.ConfigChangeRequest;
import com.nbfc.itsm.domain.ConfigChangeRequestRepository;
import com.nbfc.itsm.domain.Employee;
import com.nbfc.itsm.domain.EmployeeRepository;
import com.nbfc.itsm.domain.EmployeeRoleAssignment;
import com.nbfc.itsm.domain.EmployeeRoleAssignmentRepository;
import com.nbfc.itsm.domain.Permission;
import com.nbfc.itsm.domain.PermissionRepository;
import com.nbfc.itsm.domain.Role;
import com.nbfc.itsm.domain.RoleRepository;
import com.nbfc.itsm.domain.WorkflowInstanceStageRepository;
import com.nbfc.itsm.domain.WorkflowStageRepository;
import com.nbfc.itsm.exception.ItsmException;
import com.nbfc.itsm.security.ItsmUserPrincipal;
import com.nbfc.itsm.util.TimeUtc;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Pattern;

/**
 * Roles &amp; Permissions administration. Reading is immediate; creating or editing a role is
 * proposed as a {@code config_change_request} (maker) and only applied when a different
 * administrator approves it in Config approvals (checker), the same flow as user role changes.
 */
@Service
public class RoleAdminService {

    public static final String ENTITY = "role";
    public static final String CREATE = "ROLE_DEFINITION_CREATE";
    public static final String UPDATE = "ROLE_DEFINITION_UPDATE";
    static final String PENDING = "PendingApproval";

    /** A role change may never leave the portal without someone who can still administer it. */
    static final String[] GUARDED_PERMISSIONS = {"ADMIN_USER_MANAGE", "ADMIN_MASTERDATA_APPROVE"};

    private static final Pattern NAME_PATTERN = Pattern.compile("[A-Za-z0-9 &()/._-]+");
    private static final int NAME_MIN = 3;
    private static final int NAME_MAX = 128;
    private static final int CODE_MAX = 64;
    private static final int DESCRIPTION_MAX = 512;

    private final RoleRepository roleRepository;
    private final PermissionRepository permissionRepository;
    private final EmployeeRoleAssignmentRepository assignmentRepository;
    private final EmployeeRepository employeeRepository;
    private final WorkflowStageRepository workflowStageRepository;
    private final WorkflowInstanceStageRepository workflowInstanceStageRepository;
    private final ConfigChangeRequestRepository changeRequestRepository;
    private final AuditRecorder auditRecorder;
    private final ObjectMapper objectMapper;

    public RoleAdminService(RoleRepository roleRepository,
                            PermissionRepository permissionRepository,
                            EmployeeRoleAssignmentRepository assignmentRepository,
                            EmployeeRepository employeeRepository,
                            WorkflowStageRepository workflowStageRepository,
                            WorkflowInstanceStageRepository workflowInstanceStageRepository,
                            ConfigChangeRequestRepository changeRequestRepository,
                            AuditRecorder auditRecorder,
                            ObjectMapper objectMapper) {
        this.roleRepository = roleRepository;
        this.permissionRepository = permissionRepository;
        this.assignmentRepository = assignmentRepository;
        this.employeeRepository = employeeRepository;
        this.workflowStageRepository = workflowStageRepository;
        this.workflowInstanceStageRepository = workflowInstanceStageRepository;
        this.changeRequestRepository = changeRequestRepository;
        this.auditRecorder = auditRecorder;
        this.objectMapper = objectMapper;
    }

    // ------------------------------------------------------------------ read

    @PreAuthorize("hasAuthority('ADMIN_USER_MANAGE')")
    @Transactional(readOnly = true)
    public List<RoleSummary> list() {
        Map<Long, Long> holders = holderCounts();
        Set<String> pendingKeys = new LinkedHashSet<String>();
        for (ConfigChangeRequest ccr : pendingChanges()) {
            pendingKeys.add(ccr.getEntityKey());
        }
        List<RoleSummary> list = new ArrayList<RoleSummary>();
        for (Role role : roleRepository.findAllByOrderByNameAsc()) {
            Long count = holders.get(role.getRoleId());
            list.add(new RoleSummary(role, count == null ? 0 : count, describe(role.getPermissions()),
                    pendingKeys.contains(String.valueOf(role.getRoleId()))));
        }
        return list;
    }

    /** Pending create/update requests for roles, newest first (requester loaded for the view). */
    @PreAuthorize("hasAuthority('ADMIN_USER_MANAGE')")
    @Transactional(readOnly = true)
    public List<ConfigChangeRequest> pendingChanges() {
        List<ConfigChangeRequest> list =
                changeRequestRepository.findByEntityNameAndStatusCodeOrderByRequestedAtUtcDesc(ENTITY, PENDING);
        for (ConfigChangeRequest ccr : list) {
            ccr.getRequestedBy().getDisplayName();
        }
        return list;
    }

    @PreAuthorize("hasAuthority('ADMIN_USER_MANAGE')")
    @Transactional(readOnly = true)
    public RoleDetail get(Long roleId) {
        Role role = requireRole(roleId);
        List<Holder> holders = new ArrayList<Holder>();
        for (EmployeeRoleAssignment a : assignmentRepository.findByRole(role)) {
            Employee e = a.getEmployee();
            holders.add(new Holder(e.getEmployeeId(), e.getEmployeeNo(), e.getDisplayName(), e.isPortalActive()));
        }
        Collections.sort(holders, new Comparator<Holder>() {
            @Override
            public int compare(Holder a, Holder b) {
                return a.getDisplayName().compareToIgnoreCase(b.getDisplayName());
            }
        });
        List<ConfigChangeRequest> pending = new ArrayList<ConfigChangeRequest>();
        for (ConfigChangeRequest ccr : pendingChanges()) {
            if (UPDATE.equals(ccr.getChangeType()) && String.valueOf(roleId).equals(ccr.getEntityKey())) {
                pending.add(ccr);
            }
        }
        return new RoleDetail(role, holders, permissionGroups(codes(role), true), pending,
                workflowStageRepository.existsByRole(role));
    }

    /** Form backing object pre-filled from the current role. */
    @PreAuthorize("hasAuthority('ADMIN_USER_MANAGE')")
    @Transactional(readOnly = true)
    public RoleForm formFor(Long roleId) {
        Role role = requireRole(roleId);
        RoleForm form = new RoleForm();
        form.setName(role.getName());
        form.setActive(role.isActive());
        form.setPermissionCodes(new ArrayList<String>(codes(role)));
        return form;
    }

    @PreAuthorize("hasAuthority('ADMIN_USER_MANAGE')")
    @Transactional(readOnly = true)
    public Role role(Long roleId) {
        return requireRole(roleId);
    }

    /**
     * Every permission in the catalogue, grouped for the editor. With {@code selectedOnly} only
     * the given codes are returned (read view); otherwise all, with {@code selected} flags set.
     */
    @Transactional(readOnly = true)
    public List<PermissionGroup> permissionGroups(Collection<String> selected, boolean selectedOnly) {
        Map<String, PermissionGroup> groups = new LinkedHashMap<String, PermissionGroup>();
        for (String g : PermissionCatalog.groups()) {
            groups.put(g, new PermissionGroup(g));
        }
        List<Permission> all = new ArrayList<Permission>(permissionRepository.findAllByOrderByCodeAsc());
        Collections.sort(all, new Comparator<Permission>() {
            @Override
            public int compare(Permission a, Permission b) {
                int c = Integer.compare(PermissionCatalog.order(a.getCode()), PermissionCatalog.order(b.getCode()));
                return c != 0 ? c : a.getCode().compareTo(b.getCode());
            }
        });
        for (Permission p : all) {
            boolean isSelected = selected != null && selected.contains(p.getCode());
            if (selectedOnly && !isSelected) {
                continue;
            }
            PermissionCatalog.Entry e = PermissionCatalog.describe(p.getCode(), p.getDescription());
            groups.get(e.getGroup()).getOptions().add(new PermissionOption(e, isSelected));
        }
        List<PermissionGroup> result = new ArrayList<PermissionGroup>();
        for (PermissionGroup g : groups.values()) {
            if (!g.getOptions().isEmpty()) {
                result.add(g);
            }
        }
        return result;
    }

    // ------------------------------------------------------------------ propose (maker)

    @PreAuthorize("hasAuthority('ADMIN_USER_MANAGE') and hasAuthority('ADMIN_MASTERDATA_PROPOSE')")
    @Transactional
    public ConfigChangeRequest proposeCreate(RoleForm form, ItsmUserPrincipal maker) {
        String name = normalizeName(form.getName());
        String code = codeFor(name);
        assertNameAndCodeFree(name, code, null);
        if (changeRequestRepository.existsByEntityNameAndEntityKeyAndStatusCode(ENTITY, code, PENDING)) {
            throw new ItsmException("ROLE_PENDING", "A request to create role " + code + " is already awaiting approval.");
        }
        Set<String> perms = requirePermissions(form.getPermissionCodes());

        ConfigChangeRequest ccr = newRequest(CREATE, code, maker);
        ccr.setPayloadJson(json(snapshot(code, name, true, perms)));
        ccr.setDescription(truncate("Create role '" + name + "' (" + code + ") with permissions: " + join(perms)));
        changeRequestRepository.save(ccr);
        auditRecorder.record("ADMIN", "PROPOSE", ccr.getDescription(), "SUCCESS");
        return ccr;
    }

    @PreAuthorize("hasAuthority('ADMIN_USER_MANAGE') and hasAuthority('ADMIN_MASTERDATA_PROPOSE')")
    @Transactional
    public ConfigChangeRequest proposeUpdate(Long roleId, RoleForm form, ItsmUserPrincipal maker) {
        Role role = requireRole(roleId);
        String key = String.valueOf(roleId);
        if (changeRequestRepository.existsByEntityNameAndEntityKeyAndStatusCode(ENTITY, key, PENDING)) {
            throw new ItsmException("ROLE_PENDING",
                    "A change to this role is already awaiting approval. Approve or reject it first.");
        }
        String name = normalizeName(form.getName());
        if (!name.equalsIgnoreCase(role.getName())) {
            assertNameAndCodeFree(name, null, role.getRoleId());
        }
        Set<String> perms = requirePermissions(form.getPermissionCodes());
        Set<String> current = codes(role);
        boolean renamed = !name.equals(role.getName());
        boolean activeChanged = form.isActive() != role.isActive();
        if (!renamed && !activeChanged && perms.equals(current)) {
            throw new ItsmException("ROLE_NO_CHANGE", "Nothing changed. Edit the name, status or permissions first.");
        }
        assertSafeUpdate(role, form.isActive(), perms);

        ConfigChangeRequest ccr = newRequest(UPDATE, key, maker);
        ccr.setPayloadJson(json(snapshot(role.getCode(), name, form.isActive(), perms)));
        ccr.setPreviousJson(json(snapshot(role.getCode(), role.getName(), role.isActive(), current)));
        ccr.setDescription(truncate(describeUpdate(role, name, form.isActive(), current, perms)));
        changeRequestRepository.save(ccr);
        auditRecorder.record("ADMIN", "PROPOSE", ccr.getDescription(), "SUCCESS");
        return ccr;
    }

    // ------------------------------------------------------------------ immediate apply (System Administrator)

    /** Create a role immediately (System Administrator = final authority, no second approver). */
    @PreAuthorize("hasAuthority('ROLE_SYSTEM_ADMINISTRATOR')")
    @Transactional
    public Role createNow(RoleForm form, ItsmUserPrincipal actor) {
        String name = normalizeName(form.getName());
        String code = codeFor(name);
        assertNameAndCodeFree(name, code, null);
        if (changeRequestRepository.existsByEntityNameAndEntityKeyAndStatusCode(ENTITY, code, PENDING)) {
            throw new ItsmException("ROLE_PENDING", "A request to create role " + code + " is already awaiting approval; resolve it first.");
        }
        Set<String> perms = requirePermissions(form.getPermissionCodes());
        Role role = new Role();
        role.setCode(code);
        role.setName(name);
        role.setSystem(false);
        role.setActive(true);
        role.getPermissions().addAll(loadPermissions(perms));
        role = roleRepository.save(role);
        auditRecorder.record("ADMIN", "ROLE_CREATE",
                truncate(code + " " + join(perms) + " (applied immediately by " + actor.getUsername() + ")"), "SUCCESS");
        return role;
    }

    /** Update a role immediately (System Administrator). Bypasses maker-checker. */
    @PreAuthorize("hasAuthority('ROLE_SYSTEM_ADMINISTRATOR')")
    @Transactional
    public Role updateNow(Long roleId, RoleForm form, ItsmUserPrincipal actor) {
        Role role = requireRole(roleId);
        if (changeRequestRepository.existsByEntityNameAndEntityKeyAndStatusCode(ENTITY, String.valueOf(roleId), PENDING)) {
            throw new ItsmException("ROLE_PENDING", "A change to this role is already awaiting approval; resolve it first.");
        }
        String name = normalizeName(form.getName());
        if (!name.equalsIgnoreCase(role.getName())) {
            assertNameAndCodeFree(name, null, role.getRoleId());
        }
        Set<String> perms = requirePermissions(form.getPermissionCodes());
        Set<String> current = codes(role);
        boolean renamed = !name.equals(role.getName());
        boolean activeChanged = form.isActive() != role.isActive();
        if (!renamed && !activeChanged && perms.equals(current)) {
            throw new ItsmException("ROLE_NO_CHANGE", "Nothing changed. Edit the name, status or permissions first.");
        }
        assertSafeUpdate(role, form.isActive(), perms);
        role.setName(name);
        role.setActive(form.isActive());
        role.getPermissions().clear();
        role.getPermissions().addAll(loadPermissions(perms));
        role = roleRepository.save(role);
        auditRecorder.record("ADMIN", "ROLE_UPDATE",
                truncate(role.getCode() + " " + join(perms) + " (applied immediately by " + actor.getUsername() + ")"), "SUCCESS");
        return role;
    }

    // ------------------------------------------------------------------ add a new permission (System Administrator)

    private static final java.util.regex.Pattern PERM_CODE = java.util.regex.Pattern.compile("[A-Z][A-Z0-9_]{2,63}");

    /**
     * Adds a brand-new permission to the catalogue (System Administrator). It becomes assignable to roles
     * immediately and is granted to the System Administrator role so the invariant "sysadmin has every
     * permission" holds. Enforcement of a new permission in code is a separate development step.
     */
    @PreAuthorize("hasAuthority('ROLE_SYSTEM_ADMINISTRATOR')")
    @Transactional
    public Permission addPermission(String code, String description, ItsmUserPrincipal actor) {
        String c = code == null ? "" : code.trim().toUpperCase(Locale.ROOT).replaceAll("[^A-Z0-9]+", "_").replaceAll("^_+|_+$", "");
        if (!PERM_CODE.matcher(c).matches()) {
            throw new ItsmException("PERM_CODE",
                    "Permission code must be 3–64 characters: letters, digits and underscore, starting with a letter.");
        }
        if (permissionRepository.findByCode(c).isPresent()) {
            throw new ItsmException("PERM_EXISTS", "A permission with code " + c + " already exists.");
        }
        String desc = description == null ? "" : description.trim();
        if (desc.isEmpty()) {
            desc = c;
        }
        if (desc.length() > 256) {
            desc = desc.substring(0, 256);
        }
        Permission draft = new Permission();
        draft.setCode(c);
        draft.setDescription(desc);
        final Permission saved = permissionRepository.save(draft);
        // Keep the System Administrator all-powerful: grant the new permission to that role at once.
        roleRepository.findByCode("SYSTEM_ADMINISTRATOR").ifPresent(sys -> {
            sys.getPermissions().add(saved);
            roleRepository.save(sys);
        });
        auditRecorder.record("ADMIN", "PERMISSION_ADD", c + " — " + desc + " (by " + actor.getUsername() + ")", "SUCCESS");
        return saved;
    }

    // ------------------------------------------------------------------ delete (System Administrator)

    /**
     * Reasons the role cannot be deleted right now; empty when deletion is allowed. Mirrors the
     * prototype: only a role nobody holds can be deleted, so removing it takes access from no one.
     */
    @PreAuthorize("hasAuthority('ROLE_SYSTEM_ADMINISTRATOR')")
    @Transactional(readOnly = true)
    public List<String> deleteBlockers(Long roleId) {
        return deleteBlockers(requireRole(roleId));
    }

    /**
     * Deletes a custom role and its permission mappings ({@code role_permission}). Immediate, not
     * maker-checker, because it is only allowed when the role has no holders, no workflow use and
     * no pending change. Restricted to the System Administrator role.
     */
    @PreAuthorize("hasAuthority('ROLE_SYSTEM_ADMINISTRATOR')")
    @Transactional
    public void delete(Long roleId, ItsmUserPrincipal actor) {
        Role role = requireRole(roleId);
        List<String> blockers = deleteBlockers(role);
        if (!blockers.isEmpty()) {
            throw new ItsmException("ROLE_DELETE_BLOCKED", "Role cannot be deleted: " + blockers.get(0));
        }
        String summary = role.getCode() + " '" + role.getName() + "' with permissions: " + join(codes(role))
                + " (by " + actor.getUsername() + ")";
        role.getPermissions().clear();
        try {
            roleRepository.delete(role);
            roleRepository.flush();
        } catch (DataIntegrityViolationException ex) {
            throw new ItsmException("ROLE_DELETE_BLOCKED", "Role " + role.getCode()
                    + " is still referenced by other configuration (for example the escalation matrix) and cannot be deleted.");
        }
        auditRecorder.record("ADMIN", "ROLE_DELETE", truncate(summary), "SUCCESS");
    }

    private List<String> deleteBlockers(Role role) {
        List<String> reasons = new ArrayList<String>();
        if (role.isSystem()) {
            reasons.add("it is a seeded system role. Deactivate it from Edit Role instead.");
        }
        int holders = assignmentRepository.findByRole(role).size();
        if (holders > 0) {
            reasons.add("it is assigned to " + holders + (holders == 1 ? " employee" : " employees")
                    + ". Remove it from them in Admin → Users first.");
        }
        if (workflowStageRepository.existsByRole(role)) {
            reasons.add("it is an approver in a workflow stage.");
        }
        if (workflowInstanceStageRepository.existsByResolvedRole(role)) {
            reasons.add("ticket workflow history refers to it. Deactivate it instead to keep the history.");
        }
        if (changeRequestRepository.existsByEntityNameAndEntityKeyAndStatusCode(
                ENTITY, String.valueOf(role.getRoleId()), PENDING)) {
            reasons.add("a change to it is awaiting approval. Approve or reject that request first.");
        }
        return reasons;
    }

    // ------------------------------------------------------------------ apply (checker)

    /**
     * Applies an approved role request. Called from {@link AdminUserService#approve} inside its
     * transaction, after the maker/checker separation has been enforced. Every rule is re-checked
     * because the data may have changed since the request was proposed.
     */
    @PreAuthorize("hasAuthority('ADMIN_MASTERDATA_APPROVE')")
    @Transactional(propagation = Propagation.MANDATORY)
    public void apply(ConfigChangeRequest ccr) {
        JsonNode payload = parse(ccr.getPayloadJson());
        String name = payload.path("name").asText();
        boolean active = payload.path("active").asBoolean(true);
        Set<String> perms = new TreeSet<String>();
        for (JsonNode n : payload.path("permissionCodes")) {
            perms.add(n.asText());
        }
        List<Permission> permissions = loadPermissions(perms);

        if (CREATE.equals(ccr.getChangeType())) {
            String code = payload.path("code").asText();
            assertNameAndCodeFree(name, code, null);
            Role role = new Role();
            role.setCode(code);
            role.setName(name);
            role.setSystem(false);
            role.setActive(true);
            role.getPermissions().addAll(permissions);
            roleRepository.save(role);
            auditRecorder.record("ADMIN", "ROLE_CREATE", code + " " + join(perms), "SUCCESS");
            return;
        }
        if (UPDATE.equals(ccr.getChangeType())) {
            Role role = requireRole(Long.valueOf(ccr.getEntityKey()));
            if (!name.equalsIgnoreCase(role.getName())) {
                assertNameAndCodeFree(name, null, role.getRoleId());
            }
            assertSafeUpdate(role, active, perms);
            role.setName(name);
            role.setActive(active);
            role.getPermissions().clear();
            role.getPermissions().addAll(permissions);
            roleRepository.save(role);
            auditRecorder.record("ADMIN", "ROLE_UPDATE", role.getCode() + " " + join(perms), "SUCCESS");
            return;
        }
        throw new ItsmException("CCR_TYPE", "Unsupported role change " + ccr.getChangeType());
    }

    static boolean handles(ConfigChangeRequest ccr) {
        return CREATE.equals(ccr.getChangeType()) || UPDATE.equals(ccr.getChangeType());
    }

    // ------------------------------------------------------------------ rules

    static String normalizeName(String raw) {
        String name = raw == null ? "" : raw.trim().replaceAll("\\s+", " ");
        if (name.length() < NAME_MIN || name.length() > NAME_MAX) {
            throw new ItsmException("ROLE_NAME", "Role name must be " + NAME_MIN + " to " + NAME_MAX + " characters.");
        }
        if (!NAME_PATTERN.matcher(name).matches()) {
            throw new ItsmException("ROLE_NAME",
                    "Role name may contain letters, digits, spaces and & ( ) / . _ - only.");
        }
        return name;
    }

    /** "Procurement Approver" becomes PROCUREMENT_APPROVER. Codes never change after creation. */
    static String codeFor(String name) {
        String code = name.toUpperCase(Locale.ROOT).replaceAll("[^A-Z0-9]+", "_").replaceAll("^_+|_+$", "");
        if (code.length() > CODE_MAX) {
            code = code.substring(0, CODE_MAX).replaceAll("_+$", "");
        }
        if (code.isEmpty()) {
            throw new ItsmException("ROLE_NAME", "Role name must contain letters or digits.");
        }
        return code;
    }

    private void assertNameAndCodeFree(String name, String code, Long exceptRoleId) {
        Role byName = roleRepository.findByNameIgnoreCase(name).orElse(null);
        if (byName != null && !byName.getRoleId().equals(exceptRoleId)) {
            throw new ItsmException("ROLE_EXISTS", "A role named '" + byName.getName() + "' already exists.");
        }
        if (code != null && roleRepository.findByCode(code).isPresent()) {
            throw new ItsmException("ROLE_EXISTS", "A role with code " + code + " already exists.");
        }
    }

    private Set<String> requirePermissions(List<String> requested) {
        Set<String> perms = new TreeSet<String>();
        if (requested != null) {
            for (String c : requested) {
                if (c != null && c.trim().length() > 0) {
                    perms.add(c.trim());
                }
            }
        }
        if (perms.isEmpty()) {
            throw new ItsmException("ROLE_PERMISSIONS", "Select at least one permission for this role.");
        }
        loadPermissions(perms);
        return perms;
    }

    private List<Permission> loadPermissions(Set<String> codes) {
        List<Permission> found = permissionRepository.findByCodeIn(codes);
        if (found.size() != codes.size()) {
            Set<String> missing = new TreeSet<String>(codes);
            for (Permission p : found) {
                missing.remove(p.getCode());
            }
            throw new ItsmException("ROLE_PERMISSIONS", "Unknown permission(s): " + join(missing));
        }
        return found;
    }

    private void assertSafeUpdate(Role role, boolean active, Set<String> perms) {
        if (!active && role.isActive() && workflowStageRepository.existsByRole(role)) {
            throw new ItsmException("ROLE_IN_WORKFLOW",
                    "Role " + role.getCode() + " is an approver in a workflow stage and cannot be deactivated.");
        }
        List<EmployeeRoleAssignment> assignments = assignmentRepository.findAll();
        for (String guarded : GUARDED_PERMISSIONS) {
            boolean before = anyoneHolds(assignments, guarded, role, role.isActive(), codes(role));
            boolean after = anyoneHolds(assignments, guarded, role, active, perms);
            if (before && !after) {
                throw new ItsmException("ROLE_LOCKOUT", "This change would leave no active employee with "
                        + guarded + ". Grant it through another role first.");
            }
        }
    }

    /** True when some portal-active employee holds {@code permission}, with {@code changed} as proposed. */
    private static boolean anyoneHolds(List<EmployeeRoleAssignment> assignments, String permission,
                                       Role changed, boolean changedActive, Set<String> changedPerms) {
        for (EmployeeRoleAssignment a : assignments) {
            Employee e = a.getEmployee();
            Role r = a.getRole();
            if (e == null || r == null || !e.isPortalActive()) {
                continue;
            }
            boolean has = r.getRoleId().equals(changed.getRoleId())
                    ? changedActive && changedPerms.contains(permission)
                    : r.isActive() && codes(r).contains(permission);
            if (has) {
                return true;
            }
        }
        return false;
    }

    // ------------------------------------------------------------------ helpers

    private ConfigChangeRequest newRequest(String type, String key, ItsmUserPrincipal maker) {
        Employee makerEmp = employeeRepository.findById(maker.getEmployeeId())
                .orElseThrow(() -> new ItsmException("EMPLOYEE_NOT_FOUND", "Employee not found"));
        ConfigChangeRequest ccr = new ConfigChangeRequest();
        ccr.setChangeType(type);
        ccr.setEntityName(ENTITY);
        ccr.setEntityKey(key);
        ccr.setStatusCode(PENDING);
        ccr.setRequestedBy(makerEmp);
        ccr.setRequestedAtUtc(TimeUtc.now());
        return ccr;
    }

    private Role requireRole(Long roleId) {
        return roleRepository.findById(roleId)
                .orElseThrow(() -> new ItsmException("ROLE_NOT_FOUND", "Role not found"));
    }

    private Map<Long, Long> holderCounts() {
        Map<Long, Long> map = new HashMap<Long, Long>();
        for (Object[] row : assignmentRepository.countHoldersByRole()) {
            map.put((Long) row[0], ((Number) row[1]).longValue());
        }
        return map;
    }

    static Set<String> codes(Role role) {
        Set<String> codes = new TreeSet<String>();
        if (role.getPermissions() != null) {
            for (Permission p : role.getPermissions()) {
                codes.add(p.getCode());
            }
        }
        return codes;
    }

    private static List<PermissionCatalog.Entry> describe(Collection<Permission> permissions) {
        List<PermissionCatalog.Entry> list = new ArrayList<PermissionCatalog.Entry>();
        for (Permission p : permissions) {
            list.add(PermissionCatalog.describe(p.getCode(), p.getDescription()));
        }
        Collections.sort(list, new Comparator<PermissionCatalog.Entry>() {
            @Override
            public int compare(PermissionCatalog.Entry a, PermissionCatalog.Entry b) {
                return Integer.compare(PermissionCatalog.order(a.getCode()), PermissionCatalog.order(b.getCode()));
            }
        });
        return list;
    }

    private static Map<String, Object> snapshot(String code, String name, boolean active, Set<String> perms) {
        Map<String, Object> map = new LinkedHashMap<String, Object>();
        map.put("code", code);
        map.put("name", name);
        map.put("active", active);
        map.put("permissionCodes", new ArrayList<String>(perms));
        return map;
    }

    private static String describeUpdate(Role role, String name, boolean active,
                                         Set<String> before, Set<String> after) {
        List<String> parts = new ArrayList<String>();
        if (!name.equals(role.getName())) {
            parts.add("rename to '" + name + "'");
        }
        if (active != role.isActive()) {
            parts.add(active ? "reactivate" : "deactivate");
        }
        Set<String> added = new TreeSet<String>(after);
        added.removeAll(before);
        Set<String> removed = new TreeSet<String>(before);
        removed.removeAll(after);
        if (!added.isEmpty()) {
            parts.add("add " + join(added));
        }
        if (!removed.isEmpty()) {
            parts.add("remove " + join(removed));
        }
        return "Update role '" + role.getName() + "' (" + role.getCode() + "): " + String.join("; ", parts);
    }

    private static String join(Collection<String> values) {
        return String.join(", ", values);
    }

    private static String truncate(String s) {
        return s.length() <= DESCRIPTION_MAX ? s : s.substring(0, DESCRIPTION_MAX - 3) + "...";
    }

    private String json(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("Cannot serialise role change", ex);
        }
    }

    private JsonNode parse(String json) {
        try {
            return objectMapper.readTree(json);
        } catch (JsonProcessingException ex) {
            throw new ItsmException("CCR_PAYLOAD", "Role change payload is not valid JSON");
        }
    }

    // ------------------------------------------------------------------ view models

    public static class RoleSummary {
        private final Role role;
        private final long holderCount;
        private final List<PermissionCatalog.Entry> permissions;
        private final boolean changePending;

        RoleSummary(Role role, long holderCount, List<PermissionCatalog.Entry> permissions, boolean changePending) {
            this.role = role;
            this.holderCount = holderCount;
            this.permissions = permissions;
            this.changePending = changePending;
        }

        public Role getRole() {
            return role;
        }

        public long getHolderCount() {
            return holderCount;
        }

        public List<PermissionCatalog.Entry> getPermissions() {
            return permissions;
        }

        public boolean isChangePending() {
            return changePending;
        }
    }

    public static class RoleDetail {
        private final Role role;
        private final List<Holder> holders;
        private final List<PermissionGroup> permissionGroups;
        private final List<ConfigChangeRequest> pendingChanges;
        private final boolean usedInWorkflow;

        RoleDetail(Role role, List<Holder> holders, List<PermissionGroup> permissionGroups,
                   List<ConfigChangeRequest> pendingChanges, boolean usedInWorkflow) {
            this.role = role;
            this.holders = holders;
            this.permissionGroups = permissionGroups;
            this.pendingChanges = pendingChanges;
            this.usedInWorkflow = usedInWorkflow;
        }

        public Role getRole() {
            return role;
        }

        public List<Holder> getHolders() {
            return holders;
        }

        public List<PermissionGroup> getPermissionGroups() {
            return permissionGroups;
        }

        public List<ConfigChangeRequest> getPendingChanges() {
            return pendingChanges;
        }

        public boolean isUsedInWorkflow() {
            return usedInWorkflow;
        }
    }

    public static class Holder {
        private final Long employeeId;
        private final String employeeNo;
        private final String displayName;
        private final boolean portalActive;

        Holder(Long employeeId, String employeeNo, String displayName, boolean portalActive) {
            this.employeeId = employeeId;
            this.employeeNo = employeeNo;
            this.displayName = displayName;
            this.portalActive = portalActive;
        }

        public Long getEmployeeId() {
            return employeeId;
        }

        public String getEmployeeNo() {
            return employeeNo;
        }

        public String getDisplayName() {
            return displayName;
        }

        public boolean isPortalActive() {
            return portalActive;
        }
    }

    public static class PermissionGroup {
        private final String name;
        private final List<PermissionOption> options = new ArrayList<PermissionOption>();

        PermissionGroup(String name) {
            this.name = name;
        }

        public String getName() {
            return name;
        }

        public List<PermissionOption> getOptions() {
            return options;
        }
    }

    public static class PermissionOption {
        private final PermissionCatalog.Entry entry;
        private final boolean selected;

        PermissionOption(PermissionCatalog.Entry entry, boolean selected) {
            this.entry = entry;
            this.selected = selected;
        }

        public String getCode() {
            return entry.getCode();
        }

        public String getLabel() {
            return entry.getLabel();
        }

        public String getPages() {
            return entry.getPages();
        }

        public boolean isSelected() {
            return selected;
        }
    }
}
