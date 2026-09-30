package com.nbfc.itsm.admin;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nbfc.itsm.audit.AuditRecorder;
import com.nbfc.itsm.domain.AssignmentGroup;
import com.nbfc.itsm.domain.AssignmentGroupRepository;
import com.nbfc.itsm.domain.Role;
import com.nbfc.itsm.domain.RoleRepository;
import com.nbfc.itsm.domain.WorkflowDefinition;
import com.nbfc.itsm.domain.WorkflowDefinitionRepository;
import com.nbfc.itsm.domain.WorkflowInstanceRepository;
import com.nbfc.itsm.domain.WorkflowRule;
import com.nbfc.itsm.domain.WorkflowRuleRepository;
import com.nbfc.itsm.domain.WorkflowStage;
import com.nbfc.itsm.domain.WorkflowStageRepository;
import com.nbfc.itsm.domain.WorkflowStageTransition;
import com.nbfc.itsm.domain.WorkflowStageTransitionRepository;
import com.nbfc.itsm.exception.ItsmException;
import com.nbfc.itsm.validation.FieldLimits;
import com.nbfc.itsm.validation.Validation;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Workflow designer (Admin &gt; Workflow Config). Tickets keep pointing at the template stages they
 * started with (allowed actions, send-back target), so a workflow in use is never edited in place:
 * "Edit" makes a Draft copy (next version), the draft is changed freely, and "Publish" validates it,
 * makes it Active, moves the rules to it and retires the old version. Running tickets stay on the
 * version they started with. Allowed actions are generated from each stage's type. Changes are made
 * by the System Administrator only and are audited.
 */
@Service
public class WorkflowDesignService {

    public static final String ACTIVE = "Active";
    public static final String DRAFT = "Draft";
    public static final String RETIRED = "Retired";

    /** Stage types in the order they usually appear, with a short explanation for the page. */
    public static final Map<String, String> STAGE_TYPES = new LinkedHashMap<String, String>();
    /** Who acts, per strategy code. */
    public static final Map<String, String> STRATEGIES = new LinkedHashMap<String, String>();
    /** Strategies allowed for each stage type. */
    public static final Map<String, List<String>> STRATEGIES_BY_TYPE = new LinkedHashMap<String, List<String>>();
    /** Strategy code to the comma-separated stage types it fits (for the page's type/strategy filter). */
    public static final Map<String, String> TYPES_BY_STRATEGY = new LinkedHashMap<String, String>();

    static {
        STAGE_TYPES.put("APPROVAL", "Approval (approve / reject / send back)");
        STAGE_TYPES.put("ASSIGNMENT", "Assignment (e.g. IT Service Desk: send to implementor(s) or reject)");
        STAGE_TYPES.put("FULFILMENT", "Fulfilment (the work: accept / start / hold / resolve)");
        STAGE_TYPES.put("CONFIRMATION", "Requester confirmation");
        STAGE_TYPES.put("CLOSURE", "Closed (always the last stage)");

        STRATEGIES.put("DYNAMIC_HIERARCHY_TO_HOD", "Reporting line: manager up to the HOD (one step per person)");
        STRATEGIES.put("LDAP_MANAGER", "Requester's manager only");
        STRATEGIES.put("LDAP_HOD", "Requester's HOD (set on Admin > Users)");
        STRATEGIES.put("NAMED_ROLE", "Anyone with a role (choose the role)");
        STRATEGIES.put("SERVICE_DESK", "Service desk group (choose the group)");
        STRATEGIES.put("ASSIGNMENT_GROUP", "An assignment group (choose the group)");
        STRATEGIES.put("IMPLEMENTOR", "Implementor group (choose the group)");
        STRATEGIES.put("REQUESTER", "The requester");
        STRATEGIES.put("SYSTEM", "System (automatic)");

        STRATEGIES_BY_TYPE.put("APPROVAL", Arrays.asList("DYNAMIC_HIERARCHY_TO_HOD", "LDAP_MANAGER", "LDAP_HOD", "NAMED_ROLE"));
        STRATEGIES_BY_TYPE.put("ASSIGNMENT", Arrays.asList("SERVICE_DESK", "ASSIGNMENT_GROUP"));
        STRATEGIES_BY_TYPE.put("FULFILMENT", Arrays.asList("IMPLEMENTOR", "ASSIGNMENT_GROUP"));
        STRATEGIES_BY_TYPE.put("CONFIRMATION", Collections.singletonList("REQUESTER"));
        STRATEGIES_BY_TYPE.put("CLOSURE", Collections.singletonList("SYSTEM"));

        for (Map.Entry<String, List<String>> e : STRATEGIES_BY_TYPE.entrySet()) {
            for (String strategy : e.getValue()) {
                String types = TYPES_BY_STRATEGY.get(strategy);
                TYPES_BY_STRATEGY.put(strategy, types == null ? e.getKey() : types + "," + e.getKey());
            }
        }
    }

    static final List<String> GROUP_STRATEGIES = Arrays.asList("SERVICE_DESK", "ASSIGNMENT_GROUP", "IMPLEMENTOR");
    static final List<String> SEND_BACK_TARGETS = Arrays.asList("PREVIOUS_STAGE", "REQUESTER");
    static final int CODE_MAX = 64;
    static final int NAME_MAX = 128;
    static final int DESCRIPTION_MAX = 512;
    static final int LABEL_MAX = 128;
    static final int PRIORITY_MIN = 1;
    static final int PRIORITY_MAX = 99999;
    static final int STAGES_MAX = 20;

    private final WorkflowDefinitionRepository definitionRepository;
    private final WorkflowStageRepository stageRepository;
    private final WorkflowStageTransitionRepository transitionRepository;
    private final WorkflowRuleRepository ruleRepository;
    private final WorkflowInstanceRepository instanceRepository;
    private final RoleRepository roleRepository;
    private final AssignmentGroupRepository groupRepository;
    private final AuditRecorder auditRecorder;
    private final ObjectMapper objectMapper;

    public WorkflowDesignService(WorkflowDefinitionRepository definitionRepository,
                                 WorkflowStageRepository stageRepository,
                                 WorkflowStageTransitionRepository transitionRepository,
                                 WorkflowRuleRepository ruleRepository,
                                 WorkflowInstanceRepository instanceRepository,
                                 RoleRepository roleRepository,
                                 AssignmentGroupRepository groupRepository,
                                 AuditRecorder auditRecorder,
                                 ObjectMapper objectMapper) {
        this.definitionRepository = definitionRepository;
        this.stageRepository = stageRepository;
        this.transitionRepository = transitionRepository;
        this.ruleRepository = ruleRepository;
        this.instanceRepository = instanceRepository;
        this.roleRepository = roleRepository;
        this.groupRepository = groupRepository;
        this.auditRecorder = auditRecorder;
        this.objectMapper = objectMapper;
    }

    // ------------------------------------------------------------------ read

    @Transactional(readOnly = true)
    public List<WorkflowSummary> workflows(boolean includeRetired) {
        List<WorkflowSummary> out = new ArrayList<WorkflowSummary>();
        for (WorkflowDefinition d : definitionRepository.findAllByOrderByCodeAscVersionNoAsc()) {
            if (!includeRetired && RETIRED.equals(d.getStatusCode())) {
                continue;
            }
            List<WorkflowStage> stages = stageRepository.findByWorkflowDefinitionOrderByStageOrderAsc(d);
            out.add(new WorkflowSummary(d, flowText(stages), ruleRepository.findByWorkflowDefinition(d).size(),
                    instanceRepository.countByWorkflowDefinition(d), draftOf(d)));
        }
        return out;
    }

    @Transactional(readOnly = true)
    public WorkflowDefinition definition(Long id) {
        return definitionRepository.findById(id)
                .orElseThrow(() -> new ItsmException("WF_NOT_FOUND", "Workflow not found."));
    }

    /** Stages with role / group loaded, in order. */
    @Transactional(readOnly = true)
    public List<WorkflowStage> stages(Long definitionId) {
        List<WorkflowStage> stages = stageRepository.findByWorkflowDefinitionOrderByStageOrderAsc(definition(definitionId));
        for (WorkflowStage s : stages) {
            if (s.getRole() != null) {
                s.getRole().getName();
            }
            if (s.getAssignmentGroup() != null) {
                s.getAssignmentGroup().getName();
            }
        }
        return stages;
    }

    @Transactional(readOnly = true)
    public List<WorkflowRule> rules() {
        List<WorkflowRule> rules = ruleRepository.findAllByOrderByPriorityAsc();
        for (WorkflowRule r : rules) {
            r.getWorkflowDefinition().getCode();
        }
        return rules;
    }

    @Transactional(readOnly = true)
    public List<WorkflowDefinition> activeDefinitions() {
        return definitionRepository.findByStatusCodeOrderByCodeAsc(ACTIVE);
    }

    @Transactional(readOnly = true)
    public List<Role> roles() {
        List<Role> out = new ArrayList<Role>();
        for (Role r : roleRepository.findAllByOrderByNameAsc()) {
            if (r.isActive()) {
                out.add(r);
            }
        }
        return out;
    }

    @Transactional(readOnly = true)
    public List<AssignmentGroup> groups() {
        return groupRepository.findByActiveTrueOrderByNameAsc();
    }

    @Transactional(readOnly = true)
    public int activeRuleCount() {
        return ruleRepository.findByStatusCodeOrderByPriorityAsc(ACTIVE).size();
    }

    /** The Draft being prepared for the same workflow code, if any (null for drafts themselves). */
    @Transactional(readOnly = true)
    public WorkflowDefinition draftOf(WorkflowDefinition d) {
        if (DRAFT.equals(d.getStatusCode())) {
            return null;
        }
        for (WorkflowDefinition v : definitionRepository.findByCodeOrderByVersionNoDesc(d.getCode())) {
            if (DRAFT.equals(v.getStatusCode())) {
                return v;
            }
        }
        return null;
    }

    /** Problems that block publishing; empty when the draft is ready. */
    @Transactional(readOnly = true)
    public List<String> problems(Long definitionId) {
        return problems(stageRepository.findByWorkflowDefinitionOrderByStageOrderAsc(definition(definitionId)));
    }

    /** "Reporting line → CISO approval → Implementor → …" for lists and previews. */
    public static String flowText(List<WorkflowStage> stages) {
        List<String> labels = new ArrayList<String>();
        for (WorkflowStage s : stages) {
            labels.add(s.getLabel());
        }
        return String.join(" → ", labels);
    }

    // ------------------------------------------------------------------ drafts

    /** Opens (or creates) the Draft copy of an Active workflow; the Active version keeps serving tickets. */
    @PreAuthorize("hasAuthority('ROLE_SYSTEM_ADMINISTRATOR')")
    @Transactional
    public WorkflowDefinition startDraft(Long definitionId) {
        WorkflowDefinition source = definition(definitionId);
        if (DRAFT.equals(source.getStatusCode())) {
            return source;
        }
        WorkflowDefinition existing = draftOf(source);
        if (existing != null) {
            return existing;
        }
        WorkflowDefinition draft = new WorkflowDefinition();
        draft.setCode(source.getCode());
        draft.setName(source.getName());
        draft.setDescription(source.getDescription());
        draft.setVersionNo(nextVersion(source.getCode()));
        draft.setStatusCode(DRAFT);
        draft.setCreatedAtUtc(Instant.now());
        draft = definitionRepository.save(draft);
        copyStages(source, draft);
        audit("DRAFT_CREATE", draft.getCode() + " v" + draft.getVersionNo() + " from v" + source.getVersionNo());
        return draft;
    }

    /** A new workflow code, optionally starting from a copy of another workflow's stages. */
    @PreAuthorize("hasAuthority('ROLE_SYSTEM_ADMINISTRATOR')")
    @Transactional
    public WorkflowDefinition createWorkflow(String code, String name, String description, Long copyFromId) {
        Validation v = new Validation();
        String cleanCode = v.text(code, "Code", 3, CODE_MAX, true);
        String cleanName = v.text(name, "Name", 3, NAME_MAX, true);
        String cleanDescription = v.text(description, "Description", 0, DESCRIPTION_MAX, false);
        if (cleanCode != null) {
            cleanCode = cleanCode.toUpperCase(Locale.ROOT).replace(' ', '_');
            v.check(cleanCode.matches("[A-Z0-9_]+"), "Code may only contain letters, digits and _ (e.g. SR_WITH_SERVICE_DESK).");
            v.check(definitionRepository.findByCodeOrderByVersionNoDesc(cleanCode).isEmpty(),
                    "A workflow with code " + cleanCode + " already exists.");
        }
        WorkflowDefinition copyFrom = copyFromId == null ? null : definitionRepository.findById(copyFromId).orElse(null);
        v.check(copyFromId == null || copyFrom != null, "The workflow to copy from does not exist.");
        v.throwIfInvalid();
        WorkflowDefinition d = new WorkflowDefinition();
        d.setCode(cleanCode);
        d.setName(cleanName);
        d.setDescription(cleanDescription);
        d.setVersionNo(1);
        d.setStatusCode(DRAFT);
        d.setCreatedAtUtc(Instant.now());
        d = definitionRepository.save(d);
        if (copyFrom != null) {
            copyStages(copyFrom, d);
        } else {
            addStageInternal(d, "Requester confirmation", "CONFIRMATION", "REQUESTER", null, null, null);
            addStageInternal(d, "Closed", "CLOSURE", "SYSTEM", null, null, null);
        }
        audit("WORKFLOW_CREATE", d.getCode() + (copyFrom == null ? "" : " copied from " + copyFrom.getCode()
                + " v" + copyFrom.getVersionNo()));
        return d;
    }

    @PreAuthorize("hasAuthority('ROLE_SYSTEM_ADMINISTRATOR')")
    @Transactional
    public void updateDetails(Long draftId, String name, String description) {
        WorkflowDefinition d = requireDraft(draftId);
        Validation v = new Validation();
        String cleanName = v.text(name, "Name", 3, NAME_MAX, true);
        String cleanDescription = v.text(description, "Description", 0, DESCRIPTION_MAX, false);
        v.throwIfInvalid();
        d.setName(cleanName);
        d.setDescription(cleanDescription);
        definitionRepository.save(d);
        audit("DRAFT_DETAILS", d.getCode() + " v" + d.getVersionNo() + " name=" + cleanName);
    }

    /**
     * Validates the draft, makes it Active, points every rule of the same workflow code at it and
     * retires the previous Active version. Tickets already raised keep their old version.
     */
    @PreAuthorize("hasAuthority('ROLE_SYSTEM_ADMINISTRATOR')")
    @Transactional
    public WorkflowDefinition publish(Long draftId) {
        WorkflowDefinition draft = requireDraft(draftId);
        List<String> problems = problems(stageRepository.findByWorkflowDefinitionOrderByStageOrderAsc(draft));
        if (!problems.isEmpty()) {
            throw new ItsmException("WF_INVALID", "Cannot publish yet. " + Validation.message(problems));
        }
        int moved = 0;
        for (WorkflowDefinition old : definitionRepository.findByCodeOrderByVersionNoDesc(draft.getCode())) {
            if (old.getWorkflowDefinitionId().equals(draft.getWorkflowDefinitionId())) {
                continue;
            }
            for (WorkflowRule r : ruleRepository.findByWorkflowDefinition(old)) {
                r.setWorkflowDefinition(draft);
                ruleRepository.save(r);
                moved++;
            }
            if (ACTIVE.equals(old.getStatusCode())) {
                old.setStatusCode(RETIRED);
                definitionRepository.save(old);
            }
        }
        draft.setStatusCode(ACTIVE);
        definitionRepository.save(draft);
        audit("PUBLISH", draft.getCode() + " v" + draft.getVersionNo() + " active; rules moved=" + moved
                + "; flow=" + flowText(stageRepository.findByWorkflowDefinitionOrderByStageOrderAsc(draft)));
        return draft;
    }

    /** Deletes a Draft (never used by a ticket: only Active workflows are matched). Returns the Active version, if any. */
    @PreAuthorize("hasAuthority('ROLE_SYSTEM_ADMINISTRATOR')")
    @Transactional
    public WorkflowDefinition discard(Long draftId) {
        WorkflowDefinition draft = requireDraft(draftId);
        if (instanceRepository.countByWorkflowDefinition(draft) > 0) {
            throw new ItsmException("WF_IN_USE", "This draft is linked to tickets and cannot be deleted.");
        }
        if (!ruleRepository.findByWorkflowDefinition(draft).isEmpty()) {
            throw new ItsmException("WF_IN_USE", "A rule points at this draft; point it at another workflow first.");
        }
        for (WorkflowStage s : stageRepository.findByWorkflowDefinitionOrderByStageOrderAsc(draft)) {
            transitionRepository.deleteByWorkflowStage(s);
            stageRepository.delete(s);
        }
        stageRepository.flush();
        definitionRepository.delete(draft);
        audit("DRAFT_DISCARD", draft.getCode() + " v" + draft.getVersionNo());
        for (WorkflowDefinition v : definitionRepository.findByCodeOrderByVersionNoDesc(draft.getCode())) {
            if (ACTIVE.equals(v.getStatusCode())) {
                return v;
            }
        }
        return null;
    }

    // ------------------------------------------------------------------ stages (drafts only)

    /** Adds a stage just before "Closed" (or at the end when there is none). */
    @PreAuthorize("hasAuthority('ROLE_SYSTEM_ADMINISTRATOR')")
    @Transactional
    public WorkflowStage addStage(Long draftId, StageForm form) {
        WorkflowDefinition d = requireDraft(draftId);
        List<WorkflowStage> stages = stageRepository.findByWorkflowDefinitionOrderByStageOrderAsc(d);
        if (stages.size() >= STAGES_MAX) {
            throw new ItsmException("VALIDATION", "A workflow can have at most " + STAGES_MAX + " stages.");
        }
        Resolved r = resolve(form, null, stages);
        WorkflowStage s = addStageInternal(d, r.label, r.type, r.strategy, r.role, r.group, r.sendBack);
        int closure = -1;
        List<WorkflowStage> ordered = stageRepository.findByWorkflowDefinitionOrderByStageOrderAsc(d);
        for (int i = 0; i < ordered.size(); i++) {
            if ("CLOSURE".equals(ordered.get(i).getStageType()) && !ordered.get(i).getWorkflowStageId().equals(s.getWorkflowStageId())) {
                closure = i;
                break;
            }
        }
        if (closure >= 0 && !"CLOSURE".equals(s.getStageType())) {
            ordered.remove(s);
            ordered.add(closure, s);
            renumber(ordered);
        }
        audit("STAGE_ADD", d.getCode() + " v" + d.getVersionNo() + ": " + describe(s));
        return s;
    }

    @PreAuthorize("hasAuthority('ROLE_SYSTEM_ADMINISTRATOR')")
    @Transactional
    public void updateStage(Long draftId, Long stageId, StageForm form) {
        WorkflowDefinition d = requireDraft(draftId);
        WorkflowStage s = requireStage(d, stageId);
        Resolved r = resolve(form, s, stageRepository.findByWorkflowDefinitionOrderByStageOrderAsc(d));
        boolean typeChanged = !r.type.equals(s.getStageType());
        s.setLabel(r.label);
        s.setStageType(r.type);
        s.setActorStrategy(r.strategy);
        s.setRole(r.role);
        s.setAssignmentGroup(r.group);
        s.setSendBackTarget(r.sendBack);
        stageRepository.save(s);
        if (typeChanged) {
            transitionRepository.deleteByWorkflowStage(s);
            transitionRepository.flush();
            createTransitions(s);
        }
        audit("STAGE_UPDATE", d.getCode() + " v" + d.getVersionNo() + ": " + describe(s));
    }

    @PreAuthorize("hasAuthority('ROLE_SYSTEM_ADMINISTRATOR')")
    @Transactional
    public void deleteStage(Long draftId, Long stageId) {
        WorkflowDefinition d = requireDraft(draftId);
        WorkflowStage s = requireStage(d, stageId);
        String what = describe(s);
        transitionRepository.deleteByWorkflowStage(s);
        stageRepository.delete(s);
        stageRepository.flush();
        renumber(stageRepository.findByWorkflowDefinitionOrderByStageOrderAsc(d));
        audit("STAGE_DELETE", d.getCode() + " v" + d.getVersionNo() + ": " + what);
    }

    @PreAuthorize("hasAuthority('ROLE_SYSTEM_ADMINISTRATOR')")
    @Transactional
    public void moveStage(Long draftId, Long stageId, boolean up) {
        WorkflowDefinition d = requireDraft(draftId);
        WorkflowStage s = requireStage(d, stageId);
        List<WorkflowStage> ordered = stageRepository.findByWorkflowDefinitionOrderByStageOrderAsc(d);
        int i = ordered.indexOf(s);
        int j = up ? i - 1 : i + 1;
        if (i < 0 || j < 0 || j >= ordered.size()) {
            return;
        }
        Collections.swap(ordered, i, j);
        renumber(ordered);
        audit("STAGE_MOVE", d.getCode() + " v" + d.getVersionNo() + ": " + s.getLabel() + (up ? " up" : " down"));
    }

    // ------------------------------------------------------------------ rules

    /** Creates ({@code ruleId == null}) or updates a rule. */
    @PreAuthorize("hasAuthority('ROLE_SYSTEM_ADMINISTRATOR')")
    @Transactional
    public WorkflowRule saveRule(Long ruleId, String name, Integer priority, String statusCode, String conditionJson,
                                 Long definitionId) {
        WorkflowRule rule = ruleId == null ? new WorkflowRule()
                : ruleRepository.findById(ruleId).orElseThrow(() -> new ItsmException("RULE_NOT_FOUND", "Rule not found."));
        Validation v = new Validation();
        String cleanName = v.text(name, "Rule name", FieldLimits.RULE_NAME_MIN, FieldLimits.RULE_NAME_MAX, true);
        v.oneOf(statusCode, "Status", Arrays.asList(ACTIVE, "Inactive"));
        v.check(priority != null && priority >= PRIORITY_MIN && priority <= PRIORITY_MAX,
                "Priority must be a whole number from " + PRIORITY_MIN + " to " + PRIORITY_MAX + ".");
        String json = conditionJson == null || conditionJson.trim().isEmpty() ? "{}" : conditionJson.trim();
        if (json.length() > FieldLimits.RULE_JSON_MAX) {
            v.check(false, "Condition must be at most " + FieldLimits.RULE_JSON_MAX + " characters.");
        } else {
            try {
                JsonNode node = objectMapper.readTree(json);
                v.check(node != null && node.isObject(),
                        "Condition must be a JSON object, for example {} or {\"ticket_type\":[\"Service Request\"]}.");
            } catch (JsonProcessingException ex) {
                v.check(false, "Condition is not valid JSON: " + ex.getOriginalMessage());
            }
        }
        WorkflowDefinition target = definitionId == null ? null : definitionRepository.findById(definitionId).orElse(null);
        v.check(target != null && ACTIVE.equals(target.getStatusCode()),
                "Choose an active workflow for the rule (publish a draft first).");
        if (ACTIVE.equals(statusCode) && priority != null) {
            WorkflowRule clash = ruleRepository.findByPriorityAndStatusCode(priority, ACTIVE).orElse(null);
            v.check(clash == null || clash.getWorkflowRuleId().equals(rule.getWorkflowRuleId()),
                    "Another active rule already has priority " + priority + (clash == null ? "" : " (" + clash.getName() + ")")
                            + "; choose a different number.");
        }
        if (rule.getWorkflowRuleId() != null && ACTIVE.equals(rule.getStatusCode()) && !ACTIVE.equals(statusCode)) {
            v.check(activeRuleCount() > 1, "This is the last active rule; without one nobody can submit a request.");
        }
        v.throwIfInvalid();
        boolean created = rule.getWorkflowRuleId() == null;
        rule.setName(cleanName);
        rule.setPriority(priority);
        rule.setStatusCode(statusCode);
        rule.setConditionJson(json);
        rule.setWorkflowDefinition(target);
        rule = ruleRepository.save(rule);
        audit(created ? "RULE_CREATE" : "RULE_UPDATE", "#" + rule.getWorkflowRuleId() + " '" + cleanName + "' priority="
                + priority + " " + statusCode + " -> " + target.getCode() + " condition=" + json);
        return rule;
    }

    @PreAuthorize("hasAuthority('ROLE_SYSTEM_ADMINISTRATOR')")
    @Transactional
    public void deleteRule(Long ruleId) {
        WorkflowRule rule = ruleRepository.findById(ruleId)
                .orElseThrow(() -> new ItsmException("RULE_NOT_FOUND", "Rule not found."));
        long used = instanceRepository.countByWorkflowRule(rule);
        if (used > 0) {
            throw new ItsmException("RULE_IN_USE", "Rule '" + rule.getName() + "' was used by " + used
                    + " ticket(s), so it is kept for their history. Set it to Inactive instead.");
        }
        if (ACTIVE.equals(rule.getStatusCode()) && activeRuleCount() <= 1) {
            throw new ItsmException("RULE_LAST", "This is the last active rule; without one nobody can submit a request.");
        }
        ruleRepository.delete(rule);
        audit("RULE_DELETE", "#" + ruleId + " '" + rule.getName() + "'");
    }

    // ------------------------------------------------------------------ validation

    List<String> problems(List<WorkflowStage> stages) {
        List<String> out = new ArrayList<String>();
        if (stages.isEmpty()) {
            out.add("Add at least one stage.");
            return out;
        }
        int closures = 0;
        int hierarchies = 0;
        for (int i = 0; i < stages.size(); i++) {
            WorkflowStage s = stages.get(i);
            String type = s.getStageType();
            if ("CLOSURE".equals(type)) {
                closures++;
            }
            if ("DYNAMIC_HIERARCHY_TO_HOD".equals(s.getActorStrategy())) {
                hierarchies++;
            }
            List<String> allowed = STRATEGIES_BY_TYPE.get(type);
            if (allowed == null || !allowed.contains(s.getActorStrategy())) {
                out.add("Stage '" + s.getLabel() + "': who acts does not fit the stage type.");
            }
            if ("NAMED_ROLE".equals(s.getActorStrategy()) && (s.getRole() == null || !s.getRole().isActive())) {
                out.add("Stage '" + s.getLabel() + "': choose an active role.");
            }
            if (GROUP_STRATEGIES.contains(s.getActorStrategy())
                    && (s.getAssignmentGroup() == null || !s.getAssignmentGroup().isActive())) {
                out.add("Stage '" + s.getLabel() + "': choose an active group.");
            }
            if ("ASSIGNMENT".equals(type) && !hasFulfilmentAfter(stages, i)) {
                out.add("Stage '" + s.getLabel() + "' assigns a person, so a Fulfilment stage must come after it "
                        + "(its group is the list of people to assign).");
            }
        }
        if (closures != 1 || !"CLOSURE".equals(stages.get(stages.size() - 1).getStageType())) {
            out.add("The workflow must end with exactly one 'Closed' stage.");
        }
        if (hierarchies > 1) {
            out.add("Use the reporting-line stage only once.");
        }
        return out;
    }

    private static boolean hasFulfilmentAfter(List<WorkflowStage> stages, int index) {
        for (int k = index + 1; k < stages.size(); k++) {
            if ("FULFILMENT".equals(stages.get(k).getStageType())) {
                return true;
            }
        }
        return false;
    }

    private Resolved resolve(StageForm form, WorkflowStage editing, List<WorkflowStage> stages) {
        Validation v = new Validation();
        Resolved r = new Resolved();
        r.label = v.text(form.getLabel(), "Stage name", 2, LABEL_MAX, true);
        r.type = v.oneOf(form.getStageType(), "Stage type", STAGE_TYPES.keySet());
        List<String> allowed = r.type == null ? null : STRATEGIES_BY_TYPE.get(r.type);
        r.strategy = form.getActorStrategy();
        if (allowed != null && allowed.size() == 1) {
            r.strategy = allowed.get(0);
        }
        v.check(allowed == null || allowed.contains(r.strategy),
                "For a " + (r.type == null ? "" : r.type.toLowerCase(Locale.ROOT)) + " stage choose one of: "
                        + describeStrategies(allowed) + ".");
        if ("NAMED_ROLE".equals(r.strategy)) {
            r.role = form.getRoleId() == null ? null : roleRepository.findById(form.getRoleId()).orElse(null);
            v.check(r.role != null && r.role.isActive(), "Choose the role whose holders act on this stage.");
        }
        if (GROUP_STRATEGIES.contains(r.strategy)) {
            r.group = form.getGroupId() == null ? null : groupRepository.findById(form.getGroupId()).orElse(null);
            v.check(r.group != null && r.group.isActive(), "Choose the group that works on this stage.");
        }
        if ("APPROVAL".equals(r.type) || "CONFIRMATION".equals(r.type)) {
            String target = form.getSendBackTarget() == null || form.getSendBackTarget().isEmpty()
                    ? "PREVIOUS_STAGE" : form.getSendBackTarget();
            r.sendBack = v.oneOf(target, "Send back to", SEND_BACK_TARGETS);
        }
        if ("DYNAMIC_HIERARCHY_TO_HOD".equals(r.strategy)) {
            for (WorkflowStage s : stages) {
                if ("DYNAMIC_HIERARCHY_TO_HOD".equals(s.getActorStrategy()) && (editing == null
                        || !s.getWorkflowStageId().equals(editing.getWorkflowStageId()))) {
                    v.check(false, "This workflow already has a reporting-line stage ('" + s.getLabel() + "').");
                }
            }
        }
        if ("CLOSURE".equals(r.type)) {
            for (WorkflowStage s : stages) {
                if ("CLOSURE".equals(s.getStageType()) && (editing == null
                        || !s.getWorkflowStageId().equals(editing.getWorkflowStageId()))) {
                    v.check(false, "This workflow already has a 'Closed' stage.");
                }
            }
        }
        v.throwIfInvalid();
        return r;
    }

    private static String describeStrategies(List<String> codes) {
        if (codes == null) {
            return "";
        }
        List<String> names = new ArrayList<String>();
        for (String c : codes) {
            names.add(STRATEGIES.get(c));
        }
        return String.join("; ", names);
    }

    // ------------------------------------------------------------------ helpers

    private WorkflowDefinition requireDraft(Long id) {
        WorkflowDefinition d = definition(id);
        if (!DRAFT.equals(d.getStatusCode())) {
            throw new ItsmException("WF_NOT_DRAFT",
                    "Only a draft can be changed. Click 'Edit workflow' to make a draft copy of " + d.getCode() + ".");
        }
        return d;
    }

    private WorkflowStage requireStage(WorkflowDefinition d, Long stageId) {
        WorkflowStage s = stageRepository.findById(stageId)
                .orElseThrow(() -> new ItsmException("STAGE_NOT_FOUND", "Stage not found."));
        if (!s.getWorkflowDefinition().getWorkflowDefinitionId().equals(d.getWorkflowDefinitionId())) {
            throw new ItsmException("STAGE_NOT_FOUND", "Stage not found in this workflow.");
        }
        return s;
    }

    private int nextVersion(String code) {
        List<WorkflowDefinition> versions = definitionRepository.findByCodeOrderByVersionNoDesc(code);
        return versions.isEmpty() ? 1 : versions.get(0).getVersionNo() + 1;
    }

    private void copyStages(WorkflowDefinition from, WorkflowDefinition to) {
        for (WorkflowStage s : stageRepository.findByWorkflowDefinitionOrderByStageOrderAsc(from)) {
            WorkflowStage c = new WorkflowStage();
            c.setWorkflowDefinition(to);
            c.setStageOrder(s.getStageOrder());
            c.setCode(s.getCode());
            c.setLabel(s.getLabel());
            c.setStageType(s.getStageType());
            c.setActorStrategy(s.getActorStrategy());
            c.setRole(s.getRole());
            c.setAssignmentGroup(s.getAssignmentGroup());
            c.setSendBackTarget(s.getSendBackTarget());
            c = stageRepository.save(c);
            copyTransitions(s, c);
        }
    }

    /** Keeps the source's actions (and remarks rules); falls back to the defaults for the type. */
    private void copyTransitions(WorkflowStage from, WorkflowStage to) {
        List<WorkflowStageTransition> source = transitionRepository.findByWorkflowStage(from);
        if (source.isEmpty()) {
            createTransitions(to);
            return;
        }
        for (WorkflowStageTransition t : source) {
            WorkflowStageTransition c = new WorkflowStageTransition();
            c.setWorkflowStage(to);
            c.setActionCode(t.getActionCode());
            c.setRemarksRequired(t.isRemarksRequired());
            transitionRepository.save(c);
        }
    }

    private WorkflowStage addStageInternal(WorkflowDefinition d, String label, String type, String strategy, Role role,
                                           AssignmentGroup group, String sendBack) {
        List<WorkflowStage> stages = stageRepository.findByWorkflowDefinitionOrderByStageOrderAsc(d);
        WorkflowStage s = new WorkflowStage();
        s.setWorkflowDefinition(d);
        s.setStageOrder(stages.isEmpty() ? 10 : stages.get(stages.size() - 1).getStageOrder() + 10);
        s.setCode(uniqueCode(label, stages));
        s.setLabel(label);
        s.setStageType(type);
        s.setActorStrategy(strategy);
        s.setRole(role);
        s.setAssignmentGroup(group);
        s.setSendBackTarget(sendBack);
        s = stageRepository.save(s);
        createTransitions(s);
        return s;
    }

    /** The actions each stage type offers, as in the seeded workflows. */
    static List<String[]> defaultTransitions(String stageType) {
        List<String[]> t = new ArrayList<String[]>();
        if ("APPROVAL".equals(stageType)) {
            t.add(new String[] {"APPROVE", "true"});
            t.add(new String[] {"REJECT", "true"});
            t.add(new String[] {"SEND_BACK", "true"});
        } else if ("ASSIGNMENT".equals(stageType)) {
            t.add(new String[] {"ASSIGN", "false"});
            t.add(new String[] {"REJECT", "true"});
            t.add(new String[] {"REASSIGN", "false"});
        } else if ("FULFILMENT".equals(stageType)) {
            for (String a : new String[] {"ACCEPT", "START", "HOLD", "RESOLVE", "REASSIGN"}) {
                t.add(new String[] {a, "false"});
            }
        } else if ("CONFIRMATION".equals(stageType)) {
            t.add(new String[] {"APPROVE", "true"});
            t.add(new String[] {"SEND_BACK", "true"});
        } else if ("CLOSURE".equals(stageType)) {
            t.add(new String[] {"COMPLETE", "false"});
        }
        return t;
    }

    private void createTransitions(WorkflowStage s) {
        for (String[] a : defaultTransitions(s.getStageType())) {
            WorkflowStageTransition t = new WorkflowStageTransition();
            t.setWorkflowStage(s);
            t.setActionCode(a[0]);
            t.setRemarksRequired(Boolean.parseBoolean(a[1]));
            transitionRepository.save(t);
        }
    }

    /** Orders 10, 20, 30 … in list order; two passes so the (workflow, order) unique key never clashes. */
    private void renumber(List<WorkflowStage> ordered) {
        for (int i = 0; i < ordered.size(); i++) {
            ordered.get(i).setStageOrder(100000 + i);
            stageRepository.save(ordered.get(i));
        }
        stageRepository.flush();
        for (int i = 0; i < ordered.size(); i++) {
            ordered.get(i).setStageOrder((i + 1) * 10);
            stageRepository.save(ordered.get(i));
        }
        stageRepository.flush();
    }

    static String uniqueCode(String label, List<WorkflowStage> existing) {
        String base = label == null ? "stage" : label.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]+", "_")
                .replaceAll("^_+|_+$", "");
        if (base.isEmpty()) {
            base = "stage";
        }
        if (base.length() > 40) {
            base = base.substring(0, 40);
        }
        String code = base;
        int n = 2;
        while (codeTaken(code, existing)) {
            code = base + "_" + n++;
        }
        return code;
    }

    private static boolean codeTaken(String code, List<WorkflowStage> existing) {
        for (WorkflowStage s : existing) {
            if (code.equalsIgnoreCase(s.getCode())) {
                return true;
            }
        }
        return false;
    }

    private static String describe(WorkflowStage s) {
        return "'" + s.getLabel() + "' " + s.getStageType() + "/" + s.getActorStrategy()
                + (s.getRole() == null ? "" : " role=" + s.getRole().getCode())
                + (s.getAssignmentGroup() == null ? "" : " group=" + s.getAssignmentGroup().getCode());
    }

    private void audit(String action, String detail) {
        auditRecorder.record("WORKFLOW", action, detail, "SUCCESS");
    }

    private static class Resolved {
        String label;
        String type;
        String strategy;
        Role role;
        AssignmentGroup group;
        String sendBack;
    }

    /** Stage editor form. */
    public static class StageForm {
        private String label;
        private String stageType;
        private String actorStrategy;
        private Long roleId;
        private Long groupId;
        private String sendBackTarget;

        public String getLabel() { return label; }
        public void setLabel(String label) { this.label = label; }
        public String getStageType() { return stageType; }
        public void setStageType(String stageType) { this.stageType = stageType; }
        public String getActorStrategy() { return actorStrategy; }
        public void setActorStrategy(String actorStrategy) { this.actorStrategy = actorStrategy; }
        public Long getRoleId() { return roleId; }
        public void setRoleId(Long roleId) { this.roleId = roleId; }
        public Long getGroupId() { return groupId; }
        public void setGroupId(Long groupId) { this.groupId = groupId; }
        public String getSendBackTarget() { return sendBackTarget; }
        public void setSendBackTarget(String sendBackTarget) { this.sendBackTarget = sendBackTarget; }
    }

    /** One row of the workflow list. */
    public static class WorkflowSummary {
        private final WorkflowDefinition definition;
        private final String flow;
        private final int ruleCount;
        private final long ticketCount;
        private final WorkflowDefinition draft;

        WorkflowSummary(WorkflowDefinition definition, String flow, int ruleCount, long ticketCount, WorkflowDefinition draft) {
            this.definition = definition;
            this.flow = flow;
            this.ruleCount = ruleCount;
            this.ticketCount = ticketCount;
            this.draft = draft;
        }

        public WorkflowDefinition getDefinition() { return definition; }
        public String getFlow() { return flow; }
        public int getRuleCount() { return ruleCount; }
        public long getTicketCount() { return ticketCount; }
        public WorkflowDefinition getDraft() { return draft; }
    }
}
