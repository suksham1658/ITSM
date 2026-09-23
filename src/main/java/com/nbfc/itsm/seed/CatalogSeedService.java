package com.nbfc.itsm.seed;

import com.nbfc.itsm.domain.AssignmentGroup;
import com.nbfc.itsm.domain.AssignmentGroupRepository;
import com.nbfc.itsm.domain.AttachmentPolicy;
import com.nbfc.itsm.domain.AttachmentPolicyRepository;
import com.nbfc.itsm.domain.BusinessCalendar;
import com.nbfc.itsm.domain.BusinessCalendarRepository;
import com.nbfc.itsm.domain.Category;
import com.nbfc.itsm.domain.CategoryRepository;
import com.nbfc.itsm.domain.Department;
import com.nbfc.itsm.domain.DepartmentRepository;
import com.nbfc.itsm.domain.Holiday;
import com.nbfc.itsm.domain.HolidayRepository;
import com.nbfc.itsm.domain.Permission;
import com.nbfc.itsm.domain.PermissionRepository;
import com.nbfc.itsm.domain.Role;
import com.nbfc.itsm.domain.RoleRepository;
import com.nbfc.itsm.domain.SlaPolicy;
import com.nbfc.itsm.domain.SlaPolicyRepository;
import com.nbfc.itsm.domain.SubCategory;
import com.nbfc.itsm.domain.SubCategoryRepository;
import com.nbfc.itsm.domain.SystemSetting;
import com.nbfc.itsm.domain.SystemSettingRepository;
import com.nbfc.itsm.domain.TicketNumberConfig;
import com.nbfc.itsm.domain.TicketNumberConfigRepository;
import com.nbfc.itsm.domain.TicketType;
import com.nbfc.itsm.domain.TicketTypeRepository;
import com.nbfc.itsm.domain.WorkflowDefinition;
import com.nbfc.itsm.domain.WorkflowDefinitionRepository;
import com.nbfc.itsm.domain.WorkflowRule;
import com.nbfc.itsm.domain.WorkflowRuleRepository;
import com.nbfc.itsm.domain.WorkflowStage;
import com.nbfc.itsm.domain.WorkflowStageRepository;
import com.nbfc.itsm.domain.WorkflowStageTransition;
import com.nbfc.itsm.domain.WorkflowStageTransitionRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Idempotent H2 / test seed mirroring Flyway V4 masters (engine data, not Java if-else).
 */
@Service
public class CatalogSeedService {

    private static final String[][] PERMS = {
            {"TICKET_CREATE", "Create tickets"},
            {"TICKET_VIEW_OWN", "View own tickets"},
            {"TICKET_VIEW_TEAM", "View team tickets"},
            {"TICKET_VIEW_DEPARTMENT", "View department tickets"},
            {"TICKET_VIEW_SECURITY", "View security tickets"},
            {"TICKET_VIEW_QUEUE_ALL", "View service desk queue"},
            {"TICKET_APPROVE_ASSIGNED_STAGE", "Act on assigned workflow stage"},
            {"TICKET_ASSIGN", "Assign implementor"},
            {"TICKET_FULFIL", "Fulfil / resolve"},
            {"SLA_MONITOR", "Monitor SLA"},
            {"REPORT_VIEW", "View reports"},
            {"KB_READ", "Read knowledge base"},
            {"AUDIT_VIEW", "View audit log"},
            {"ADMIN_USER_MANAGE", "Manage portal users and roles"},
            {"ADMIN_MASTERDATA_PROPOSE", "Propose master-data changes"},
            {"ADMIN_MASTERDATA_APPROVE", "Approve master-data changes"},
            {"ADMIN_SYSTEM", "System settings"},
            {"ASSET_MANAGE", "Manage assets"}
    };

    private final DepartmentRepository departmentRepository;
    private final RoleRepository roleRepository;
    private final PermissionRepository permissionRepository;
    private final TicketTypeRepository ticketTypeRepository;
    private final CategoryRepository categoryRepository;
    private final SubCategoryRepository subCategoryRepository;
    private final SlaPolicyRepository slaPolicyRepository;
    private final TicketNumberConfigRepository numberConfigRepository;
    private final AttachmentPolicyRepository attachmentPolicyRepository;
    private final BusinessCalendarRepository calendarRepository;
    private final HolidayRepository holidayRepository;
    private final AssignmentGroupRepository assignmentGroupRepository;
    private final WorkflowDefinitionRepository definitionRepository;
    private final WorkflowStageRepository stageRepository;
    private final WorkflowStageTransitionRepository transitionRepository;
    private final WorkflowRuleRepository ruleRepository;
    private final SystemSettingRepository settingRepository;

    public CatalogSeedService(DepartmentRepository departmentRepository,
                              RoleRepository roleRepository,
                              PermissionRepository permissionRepository,
                              TicketTypeRepository ticketTypeRepository,
                              CategoryRepository categoryRepository,
                              SubCategoryRepository subCategoryRepository,
                              SlaPolicyRepository slaPolicyRepository,
                              TicketNumberConfigRepository numberConfigRepository,
                              AttachmentPolicyRepository attachmentPolicyRepository,
                              BusinessCalendarRepository calendarRepository,
                              HolidayRepository holidayRepository,
                              AssignmentGroupRepository assignmentGroupRepository,
                              WorkflowDefinitionRepository definitionRepository,
                              WorkflowStageRepository stageRepository,
                              WorkflowStageTransitionRepository transitionRepository,
                              WorkflowRuleRepository ruleRepository,
                              SystemSettingRepository settingRepository) {
        this.departmentRepository = departmentRepository;
        this.roleRepository = roleRepository;
        this.permissionRepository = permissionRepository;
        this.ticketTypeRepository = ticketTypeRepository;
        this.categoryRepository = categoryRepository;
        this.subCategoryRepository = subCategoryRepository;
        this.slaPolicyRepository = slaPolicyRepository;
        this.numberConfigRepository = numberConfigRepository;
        this.attachmentPolicyRepository = attachmentPolicyRepository;
        this.calendarRepository = calendarRepository;
        this.holidayRepository = holidayRepository;
        this.assignmentGroupRepository = assignmentGroupRepository;
        this.definitionRepository = definitionRepository;
        this.stageRepository = stageRepository;
        this.transitionRepository = transitionRepository;
        this.ruleRepository = ruleRepository;
        this.settingRepository = settingRepository;
    }

    @Transactional
    public void ensureSeeded() {
        if (ticketTypeRepository.count() > 0 && definitionRepository.count() > 0) {
            return;
        }
        seed();
    }

    @Transactional
    public void seed() {
        seedDepartments();
        Map<String, Permission> perms = seedPermissions();
        Map<String, Role> roles = seedRoles(perms);
        seedTicketTypes();
        Map<String, Category> cats = seedCategories();
        seedSubCategories(cats);
        seedSla();
        seedNumbering();
        seedAttachmentPolicy();
        seedCalendar();
        Map<String, AssignmentGroup> groups = seedGroups();
        seedWorkflows(roles, groups);
        seedSettings();
    }

    private void seedDepartments() {
        String[][] rows = {{"IT", "Information Technology"}, {"FIN", "Finance"}, {"HR", "Human Resources"}};
        for (String[] r : rows) {
            if (!departmentRepository.findByCode(r[0]).isPresent()) {
                Department d = new Department();
                d.setCode(r[0]);
                d.setName(r[1]);
                d.setActive(true);
                departmentRepository.save(d);
            }
        }
    }

    private Map<String, Permission> seedPermissions() {
        Map<String, Permission> map = new LinkedHashMap<String, Permission>();
        for (String[] p : PERMS) {
            Permission row = permissionRepository.findByCode(p[0]).orElse(null);
            if (row == null) {
                row = new Permission();
                row.setCode(p[0]);
                row.setDescription(p[1]);
                row = permissionRepository.save(row);
            }
            map.put(p[0], row);
        }
        return map;
    }

    private Map<String, Role> seedRoles(Map<String, Permission> perms) {
        Map<String, Role> roles = new LinkedHashMap<String, Role>();
        roles.put("EMPLOYEE", role("EMPLOYEE", "Employee", perms,
                "TICKET_CREATE", "TICKET_VIEW_OWN", "TICKET_APPROVE_ASSIGNED_STAGE", "KB_READ"));
        roles.put("MANAGER", role("MANAGER", "Manager", perms,
                "TICKET_CREATE", "TICKET_VIEW_OWN", "TICKET_VIEW_TEAM", "TICKET_APPROVE_ASSIGNED_STAGE",
                "REPORT_VIEW", "KB_READ"));
        roles.put("HOD", role("HOD", "HOD", perms,
                "TICKET_CREATE", "TICKET_VIEW_OWN", "TICKET_VIEW_DEPARTMENT", "TICKET_APPROVE_ASSIGNED_STAGE",
                "REPORT_VIEW", "KB_READ"));
        roles.put("CISO", role("CISO", "CISO", perms,
                "TICKET_CREATE", "TICKET_VIEW_OWN", "TICKET_VIEW_SECURITY", "TICKET_APPROVE_ASSIGNED_STAGE",
                "REPORT_VIEW", "AUDIT_VIEW"));
        roles.put("IT_SERVICE_DESK", role("IT_SERVICE_DESK", "IT Service Desk", perms,
                "TICKET_CREATE", "TICKET_VIEW_OWN", "TICKET_VIEW_QUEUE_ALL", "TICKET_ASSIGN",
                "SLA_MONITOR", "REPORT_VIEW", "KB_READ"));
        roles.put("IT_IMPLEMENTOR", role("IT_IMPLEMENTOR", "IT Implementor", perms,
                "TICKET_CREATE", "TICKET_VIEW_OWN", "TICKET_FULFIL", "KB_READ"));
        roles.put("IT_ADMIN", role("IT_ADMIN", "IT Admin", perms,
                "TICKET_CREATE", "TICKET_VIEW_OWN", "TICKET_VIEW_QUEUE_ALL", "REPORT_VIEW", "AUDIT_VIEW",
                "ADMIN_USER_MANAGE", "ADMIN_MASTERDATA_PROPOSE", "ADMIN_MASTERDATA_APPROVE", "ASSET_MANAGE"));
        Role sys = roleRepository.findByCode("SYSTEM_ADMINISTRATOR").orElse(null);
        if (sys == null) {
            sys = new Role();
            sys.setCode("SYSTEM_ADMINISTRATOR");
            sys.setName("System Administrator");
            sys.setSystem(true);
            sys.setActive(true);
        }
        sys.getPermissions().clear();
        sys.getPermissions().addAll(perms.values());
        sys = roleRepository.save(sys);
        roles.put("SYSTEM_ADMINISTRATOR", sys);
        return roles;
    }

    private Role role(String code, String name, Map<String, Permission> perms, String... codes) {
        Role r = roleRepository.findByCode(code).orElse(null);
        if (r == null) {
            r = new Role();
            r.setCode(code);
            r.setName(name);
            r.setSystem(true);
            r.setActive(true);
        }
        r.getPermissions().clear();
        for (String c : codes) {
            r.getPermissions().add(perms.get(c));
        }
        return roleRepository.save(r);
    }

    private void seedTicketTypes() {
        addType("INCIDENT", "Incident", 10);
        addType("SERVICE_REQUEST", "Service Request", 20);
        addType("ACCESS_REQUEST", "Access Request", 30);
        addType("CHANGE_REQUEST", "Change Request", 40);
        addType("SECURITY_INCIDENT", "Security Incident", 50);
        addType("PROBLEM", "Problem", 60);
        addType("HARDWARE_REQUEST", "Hardware Request", 70);
        addType("SOFTWARE_REQUEST", "Software Request", 80);
    }

    private void addType(String code, String name, int sort) {
        if (ticketTypeRepository.findByCode(code).isPresent()) {
            return;
        }
        TicketType t = new TicketType();
        t.setCode(code);
        t.setName(name);
        t.setSortOrder(sort);
        t.setActive(true);
        ticketTypeRepository.save(t);
    }

    private Map<String, Category> seedCategories() {
        Map<String, Category> map = new LinkedHashMap<String, Category>();
        String[][] rows = {
                {"HARDWARE", "Hardware", "10"}, {"SOFTWARE", "Software", "20"}, {"NETWORK", "Network", "30"},
                {"EMAIL", "Email", "40"}, {"APPLICATION", "Application", "50"},
                {"ACCESS_MGMT", "Access Management", "70"}, {"CYBER_SECURITY", "Cyber Security", "80"},
                {"OTHER", "Other", "120"}
        };
        for (String[] r : rows) {
            Category c = categoryRepository.findByCode(r[0]).orElse(null);
            if (c == null) {
                c = new Category();
                c.setCode(r[0]);
                c.setName(r[1]);
                c.setSortOrder(Integer.parseInt(r[2]));
                c.setActive(true);
                c = categoryRepository.save(c);
            }
            map.put(r[0], c);
        }
        return map;
    }

    private void seedSubCategories(Map<String, Category> cats) {
        addSub(cats.get("HARDWARE"), "LAPTOP", "Laptop Issue", 10);
        addSub(cats.get("HARDWARE"), "DESKTOP", "Desktop Issue", 20);
        addSub(cats.get("SOFTWARE"), "INSTALL", "Software Installation", 10);
        addSub(cats.get("SOFTWARE"), "BUG", "Software Bug", 30);
        addSub(cats.get("NETWORK"), "VPN", "VPN Access", 10);
        addSub(cats.get("NETWORK"), "WIFI", "Wi-Fi Connectivity", 20);
        addSub(cats.get("EMAIL"), "MAILBOX", "Mailbox Issue", 10);
        addSub(cats.get("APPLICATION"), "PORTAL", "Internal Portal Issue", 40);
        addSub(cats.get("ACCESS_MGMT"), "NEW_USER", "New User Access", 10);
        addSub(cats.get("ACCESS_MGMT"), "PRIVILEGED", "Privileged Access Request", 40);
        addSub(cats.get("CYBER_SECURITY"), "PHISHING", "Phishing Incident", 10);
        addSub(cats.get("OTHER"), "GENERAL", "General Query", 10);
    }

    private void addSub(Category cat, String code, String name, int sort) {
        if (cat == null) {
            return;
        }
        List<SubCategory> existing = subCategoryRepository.findByCategoryAndActiveTrueOrderBySortOrderAsc(cat);
        for (SubCategory s : existing) {
            if (code.equals(s.getCode())) {
                return;
            }
        }
        SubCategory s = new SubCategory();
        s.setCategory(cat);
        s.setCode(code);
        s.setName(name);
        s.setSortOrder(sort);
        s.setActive(true);
        subCategoryRepository.save(s);
    }

    private void seedSla() {
        sla("Critical", 15, 240, true);
        sla("High", 30, 480, false);
        sla("Medium", 120, 1440, false);
        sla("Low", 240, 4320, false);
    }

    private void sla(String code, int resp, int res, boolean allHours) {
        if (slaPolicyRepository.findByPriorityCodeAndActiveTrue(code).isPresent()) {
            return;
        }
        SlaPolicy p = new SlaPolicy();
        p.setPriorityCode(code);
        p.setResponseMinutes(resp);
        p.setResolutionMinutes(res);
        p.setAllHours(allHours);
        p.setActive(true);
        slaPolicyRepository.save(p);
    }

    private void seedNumbering() {
        if (numberConfigRepository.findFirstByOrderBySequenceYearDesc().isPresent()) {
            return;
        }
        TicketNumberConfig c = new TicketNumberConfig();
        c.setPrefix("ITSM");
        c.setIncludeYear(true);
        c.setPadding(6);
        c.setSequenceYear(2026);
        c.setLastAllocated(0L);
        numberConfigRepository.save(c);
    }

    private void seedAttachmentPolicy() {
        if (attachmentPolicyRepository.findFirstByOrderByAttachmentPolicyIdAsc().isPresent()) {
            return;
        }
        AttachmentPolicy p = new AttachmentPolicy();
        p.setMaxBytes(10 * 1024 * 1024);
        p.setAllowedExtensions(".pdf,.doc,.docx,.xls,.xlsx,.png,.jpg,.jpeg,.txt,.log,.msg");
        p.setAllowedMimeTypes("application/pdf,application/msword,"
                + "application/vnd.openxmlformats-officedocument.wordprocessingml.document,"
                + "application/vnd.ms-excel,"
                + "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet,"
                + "image/png,image/jpeg,text/plain");
        p.setVirusScanRequired(false);
        attachmentPolicyRepository.save(p);
    }

    private void seedCalendar() {
        if (calendarRepository.count() > 0) {
            return;
        }
        for (int d = 1; d <= 7; d++) {
            BusinessCalendar c = new BusinessCalendar();
            c.setWeekdayIso(d);
            boolean work = d <= 5;
            c.setWorkingDay(work);
            c.setStartTime(work ? LocalTime.of(9, 0) : LocalTime.MIDNIGHT);
            c.setEndTime(work ? LocalTime.of(18, 0) : LocalTime.MIDNIGHT);
            c.setTimezoneId("India Standard Time");
            calendarRepository.save(c);
        }
        if (!holidayRepository.existsByHolidayDate(LocalDate.of(2026, 1, 26))) {
            Holiday h = new Holiday();
            h.setHolidayDate(LocalDate.of(2026, 1, 26));
            h.setName("Republic Day");
            h.setNational(true);
            holidayRepository.save(h);
        }
    }

    private Map<String, AssignmentGroup> seedGroups() {
        Map<String, AssignmentGroup> map = new LinkedHashMap<String, AssignmentGroup>();
        map.put("IT_SERVICE_DESK", group("IT_SERVICE_DESK", "IT Service Desk"));
        map.put("IT_IMPLEMENTORS", group("IT_IMPLEMENTORS", "IT Implementors"));
        map.put("IT_SECURITY_TEAM", group("IT_SECURITY_TEAM", "IT Security Team"));
        return map;
    }

    private AssignmentGroup group(String code, String name) {
        AssignmentGroup g = assignmentGroupRepository.findByCode(code).orElse(null);
        if (g == null) {
            g = new AssignmentGroup();
            g.setCode(code);
            g.setName(name);
            g.setActive(true);
            g = assignmentGroupRepository.save(g);
        }
        return g;
    }

    private void seedWorkflows(Map<String, Role> roles, Map<String, AssignmentGroup> groups) {
        Role ciso = roles.get("CISO");
        AssignmentGroup sd = groups.get("IT_SERVICE_DESK");
        AssignmentGroup impl = groups.get("IT_IMPLEMENTORS");
        AssignmentGroup sec = groups.get("IT_SECURITY_TEAM");

        WorkflowDefinition sr = def("SR_CHAIN_TO_HOD_CISO_IMPL",
                "Service Request — chain to HOD, CISO, Implementor", "Active",
                "Default SR: expand LDAP manager hops until HOD, then CISO, then Implementor.");
        WorkflowDefinition srNo = def("SR_CHAIN_TO_HOD_IMPL",
                "Service Request — chain to HOD, Implementor (no CISO)", "Active",
                "Optional SR clone without CISO. Bind via a rule on non-security categories.");
        WorkflowDefinition inc = def("INCIDENT_SD_THEN_IMPL",
                "Incident — Service Desk then Implementor", "Active",
                "Default Incident: SD triage/assign, then Implementor, requester confirmation, close.");
        WorkflowDefinition incDirect = def("INCIDENT_DIRECT_IMPL",
                "Incident — direct to Implementor", "Inactive",
                "Optional skip-SD template. Inactive until an admin activates a higher-priority rule.");
        WorkflowDefinition security = def("SECURITY", "Security / highly confidential", "Active",
                "Manager chain to HOD, CISO, Implementor (same shape as default SR).");
        WorkflowDefinition priv = def("PRIVILEGED_ACCESS", "Privileged access", "Active",
                "Chain to HOD, CISO, IT Security Team, Implementor.");

        if (stageRepository.findByWorkflowDefinitionOrderByStageOrderAsc(sr).isEmpty()) {
            WorkflowStage h = stage(sr, 10, "hierarchy", "Reporting hierarchy through HOD",
                    "APPROVAL", "DYNAMIC_HIERARCHY_TO_HOD", null, null, "PREVIOUS_STAGE");
            WorkflowStage c = stage(sr, 20, "ciso", "CISO approval", "APPROVAL", "NAMED_ROLE", ciso, null, "PREVIOUS_STAGE");
            WorkflowStage i = stage(sr, 30, "implementor", "Implementor", "FULFILMENT", "IMPLEMENTOR", null, impl, null);
            WorkflowStage cf = stage(sr, 40, "confirmation", "Requester confirmation", "CONFIRMATION", "REQUESTER", null, null, null);
            WorkflowStage cl = stage(sr, 50, "closed", "Closed", "CLOSURE", "SYSTEM", null, null, null);
            approvalTransitions(h, c);
            fulfilTransitions(i);
            confirmTransitions(cf);
            trans(cl, "COMPLETE", false);
        }
        if (stageRepository.findByWorkflowDefinitionOrderByStageOrderAsc(srNo).isEmpty()) {
            WorkflowStage h = stage(srNo, 10, "hierarchy", "Reporting hierarchy through HOD",
                    "APPROVAL", "DYNAMIC_HIERARCHY_TO_HOD", null, null, "PREVIOUS_STAGE");
            WorkflowStage i = stage(srNo, 20, "implementor", "Implementor", "FULFILMENT", "IMPLEMENTOR", null, impl, null);
            WorkflowStage cf = stage(srNo, 30, "confirmation", "Requester confirmation", "CONFIRMATION", "REQUESTER", null, null, null);
            WorkflowStage cl = stage(srNo, 40, "closed", "Closed", "CLOSURE", "SYSTEM", null, null, null);
            approvalTransitions(h);
            fulfilTransitions(i);
            confirmTransitions(cf);
            trans(cl, "COMPLETE", false);
        }
        if (stageRepository.findByWorkflowDefinitionOrderByStageOrderAsc(inc).isEmpty()) {
            WorkflowStage s = stage(inc, 10, "servicedesk", "IT Service Desk triage",
                    "ASSIGNMENT", "SERVICE_DESK", null, sd, null);
            WorkflowStage i = stage(inc, 20, "implementor", "Implementor", "FULFILMENT", "IMPLEMENTOR", null, impl, null);
            WorkflowStage cf = stage(inc, 30, "confirmation", "Requester confirmation", "CONFIRMATION", "REQUESTER", null, null, null);
            WorkflowStage cl = stage(inc, 40, "closed", "Closed", "CLOSURE", "SYSTEM", null, null, null);
            trans(s, "ASSIGN", false);
            trans(s, "REASSIGN", false);
            fulfilTransitions(i);
            confirmTransitions(cf);
            trans(cl, "COMPLETE", false);
        }
        if (stageRepository.findByWorkflowDefinitionOrderByStageOrderAsc(incDirect).isEmpty()) {
            WorkflowStage i = stage(incDirect, 10, "implementor", "Implementor", "FULFILMENT", "IMPLEMENTOR", null, impl, null);
            WorkflowStage cf = stage(incDirect, 20, "confirmation", "Requester confirmation", "CONFIRMATION", "REQUESTER", null, null, null);
            WorkflowStage cl = stage(incDirect, 30, "closed", "Closed", "CLOSURE", "SYSTEM", null, null, null);
            fulfilTransitions(i);
            confirmTransitions(cf);
            trans(cl, "COMPLETE", false);
        }
        if (stageRepository.findByWorkflowDefinitionOrderByStageOrderAsc(security).isEmpty()) {
            WorkflowStage h = stage(security, 10, "hierarchy", "Reporting hierarchy through HOD",
                    "APPROVAL", "DYNAMIC_HIERARCHY_TO_HOD", null, null, "PREVIOUS_STAGE");
            WorkflowStage c = stage(security, 20, "ciso", "CISO approval", "APPROVAL", "NAMED_ROLE", ciso, null, "PREVIOUS_STAGE");
            WorkflowStage i = stage(security, 30, "implementor", "Implementor", "FULFILMENT", "IMPLEMENTOR", null, impl, null);
            WorkflowStage cf = stage(security, 40, "confirmation", "Requester confirmation", "CONFIRMATION", "REQUESTER", null, null, null);
            WorkflowStage cl = stage(security, 50, "closed", "Closed", "CLOSURE", "SYSTEM", null, null, null);
            approvalTransitions(h, c);
            fulfilTransitions(i);
            confirmTransitions(cf);
            trans(cl, "COMPLETE", false);
        }
        if (stageRepository.findByWorkflowDefinitionOrderByStageOrderAsc(priv).isEmpty()) {
            WorkflowStage h = stage(priv, 10, "hierarchy", "Reporting hierarchy through HOD",
                    "APPROVAL", "DYNAMIC_HIERARCHY_TO_HOD", null, null, "PREVIOUS_STAGE");
            WorkflowStage c = stage(priv, 20, "ciso", "CISO approval", "APPROVAL", "NAMED_ROLE", ciso, null, "PREVIOUS_STAGE");
            WorkflowStage st = stage(priv, 30, "security_team", "IT Security Team",
                    "ASSIGNMENT", "ASSIGNMENT_GROUP", null, sec, null);
            WorkflowStage i = stage(priv, 40, "implementor", "Implementor", "FULFILMENT", "IMPLEMENTOR", null, impl, null);
            WorkflowStage cf = stage(priv, 50, "confirmation", "Requester confirmation", "CONFIRMATION", "REQUESTER", null, null, null);
            WorkflowStage cl = stage(priv, 60, "closed", "Closed", "CLOSURE", "SYSTEM", null, null, null);
            approvalTransitions(h, c);
            trans(st, "ASSIGN", false);
            trans(st, "REASSIGN", false);
            fulfilTransitions(i);
            confirmTransitions(cf);
            trans(cl, "COMPLETE", false);
        }

        rule("Privileged access requests", 10, "{\"sub_category\":[\"Privileged Access Request\"]}", priv);
        rule("Cyber Security category", 20, "{\"category\":[\"Cyber Security\"]}", security);
        rule("Security Incident ticket type", 21, "{\"ticket_type\":[\"Security Incident\"]}", security);
        rule("Highly Confidential requests", 22, "{\"confidentiality\":[\"Highly Confidential\"]}", security);
        rule("Incident — Service Desk then Implementor", 30, "{\"ticket_type\":[\"Incident\"]}", inc);
        rule("Service Request default (CISO on template)", 40, "{\"ticket_type\":[\"Service Request\"]}", sr);
        rule("Catch-all fallback", 999, "{}", sr);
    }

    private WorkflowDefinition def(String code, String name, String status, String desc) {
        WorkflowDefinition d = definitionRepository.findByCodeAndVersionNo(code, 1).orElse(null);
        if (d == null) {
            d = new WorkflowDefinition();
            d.setCode(code);
            d.setVersionNo(1);
            d.setCreatedAtUtc(Instant.now());
        }
        d.setName(name);
        d.setStatusCode(status);
        d.setDescription(desc);
        return definitionRepository.save(d);
    }

    private WorkflowStage stage(WorkflowDefinition def, int order, String code, String label, String type,
                                String strategy, Role role, AssignmentGroup group, String sendBack) {
        WorkflowStage s = new WorkflowStage();
        s.setWorkflowDefinition(def);
        s.setStageOrder(order);
        s.setCode(code);
        s.setLabel(label);
        s.setStageType(type);
        s.setActorStrategy(strategy);
        s.setRole(role);
        s.setAssignmentGroup(group);
        s.setSendBackTarget(sendBack);
        return stageRepository.save(s);
    }

    private void approvalTransitions(WorkflowStage... stages) {
        for (WorkflowStage s : stages) {
            trans(s, "APPROVE", true);
            trans(s, "REJECT", true);
            trans(s, "SEND_BACK", true);
        }
    }

    private void fulfilTransitions(WorkflowStage s) {
        trans(s, "ACCEPT", false);
        trans(s, "START", false);
        trans(s, "HOLD", false);
        trans(s, "RESOLVE", false);
        trans(s, "REASSIGN", false);
    }

    private void confirmTransitions(WorkflowStage s) {
        trans(s, "APPROVE", true);
        trans(s, "SEND_BACK", true);
    }

    private void trans(WorkflowStage stage, String action, boolean remarks) {
        WorkflowStageTransition t = new WorkflowStageTransition();
        t.setWorkflowStage(stage);
        t.setActionCode(action);
        t.setRemarksRequired(remarks);
        transitionRepository.save(t);
    }

    private void rule(String name, int priority, String json, WorkflowDefinition def) {
        if (ruleRepository.findByPriorityAndStatusCode(priority, "Active").isPresent()) {
            return;
        }
        WorkflowRule r = new WorkflowRule();
        r.setName(name);
        r.setPriority(priority);
        r.setStatusCode("Active");
        r.setConditionJson(json);
        r.setWorkflowDefinition(def);
        ruleRepository.save(r);
    }

    private void seedSettings() {
        setting("approval.remarks-min-length", "10", "workflow", "Minimum remarks length");
        setting("workflow.max-manager-hops", "12", "workflow", "Cap for DYNAMIC_HIERARCHY_TO_HOD");
        setting("sla.amber-percent", "20", "sla", "Percent remaining when SLA turns amber");
    }

    private void setting(String key, String value, String cat, String desc) {
        if (settingRepository.findById(key).isPresent()) {
            return;
        }
        SystemSetting s = new SystemSetting();
        s.setSettingKey(key);
        s.setSettingValue(value);
        s.setCategory(cat);
        s.setDescription(desc);
        s.setSecret(false);
        settingRepository.save(s);
    }

    @SuppressWarnings("unused")
    private static List<String> unused(String... v) {
        return Arrays.asList(v);
    }
}
