package com.nbfc.itsm.admin;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.nbfc.itsm.audit.AuditRecorder;
import com.nbfc.itsm.domain.Category;
import com.nbfc.itsm.domain.CategoryRepository;
import com.nbfc.itsm.domain.SubCategory;
import com.nbfc.itsm.domain.SubCategoryRepository;
import com.nbfc.itsm.domain.TicketType;
import com.nbfc.itsm.domain.TicketTypeRepository;
import com.nbfc.itsm.domain.WorkflowRule;
import com.nbfc.itsm.domain.WorkflowRuleRepository;
import com.nbfc.itsm.exception.ItsmException;
import com.nbfc.itsm.security.ItsmUserPrincipal;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Admin &gt; Categories: add, edit and delete ticket types, categories and sub-categories shown on Raise
 * Request. System Administrator only; changes apply immediately (final authority).
 * <ul>
 *   <li>Delete hides the entry from Raise Request; tickets that already use it keep showing it.
 *       Adding the same name again brings it back.</li>
 *   <li>Workflow rules match on names, so a rename also updates the rule conditions that mention it.</li>
 *   <li>A few entries are used by name in reports and security views; those cannot be renamed or deleted.</li>
 * </ul>
 */
@Service
public class CatalogAdminService {

    /** Ticket types the reports, security views and Change Requests page look up by name. */
    public static final Set<String> LOCKED_TYPES = Collections.unmodifiableSet(new HashSet<String>(Arrays.asList(
            "INCIDENT", "SERVICE_REQUEST", "CHANGE_REQUEST", "SECURITY_INCIDENT")));
    /** Categories used by name (asset report, security views) or code (serial number on Raise Request). */
    public static final Set<String> LOCKED_CATEGORIES = Collections.unmodifiableSet(new HashSet<String>(Arrays.asList(
            "HARDWARE", "CYBER_SECURITY")));

    private final TicketTypeRepository typeRepository;
    private final CategoryRepository categoryRepository;
    private final SubCategoryRepository subCategoryRepository;
    private final WorkflowRuleRepository ruleRepository;
    private final AuditRecorder auditRecorder;
    private final ObjectMapper objectMapper;

    public CatalogAdminService(TicketTypeRepository typeRepository, CategoryRepository categoryRepository,
                               SubCategoryRepository subCategoryRepository, WorkflowRuleRepository ruleRepository,
                               AuditRecorder auditRecorder, ObjectMapper objectMapper) {
        this.typeRepository = typeRepository;
        this.categoryRepository = categoryRepository;
        this.subCategoryRepository = subCategoryRepository;
        this.ruleRepository = ruleRepository;
        this.auditRecorder = auditRecorder;
        this.objectMapper = objectMapper;
    }

    // ------------------------------------------------------------------ ticket types

    @PreAuthorize("hasAuthority('ROLE_SYSTEM_ADMINISTRATOR')")
    @Transactional
    public TicketType addType(String name, ItsmUserPrincipal actor) {
        String clean = cleanName(name);
        String code = codeOf(clean);
        for (TicketType t : typeRepository.findAll()) {
            if (t.isActive() && t.getName().equalsIgnoreCase(clean)) {
                throw new ItsmException("DUPLICATE", "Ticket type \"" + clean + "\" already exists.");
            }
        }
        TicketType t = typeRepository.findByCode(code).orElse(null);
        if (t == null) {
            t = new TicketType();
            t.setCode(code);
            t.setSortOrder(nextTypeOrder());
        }
        t.setName(clean);
        t.setActive(true);
        t = typeRepository.save(t);
        audit("TICKET_TYPE_ADD", code + " \"" + clean + "\"", actor);
        return t;
    }

    @PreAuthorize("hasAuthority('ROLE_SYSTEM_ADMINISTRATOR')")
    @Transactional
    public int renameType(Long id, String name, ItsmUserPrincipal actor) {
        TicketType t = typeRepository.findById(id).orElseThrow(() -> notFound("Ticket type"));
        String clean = cleanName(name);
        if (clean.equals(t.getName())) {
            return 0;
        }
        if (LOCKED_TYPES.contains(t.getCode())) {
            throw locked(t.getName());
        }
        for (TicketType o : typeRepository.findAll()) {
            if (o.isActive() && !o.getTicketTypeId().equals(id) && o.getName().equalsIgnoreCase(clean)) {
                throw new ItsmException("DUPLICATE", "Ticket type \"" + clean + "\" already exists.");
            }
        }
        String old = t.getName();
        t.setName(clean);
        typeRepository.save(t);
        int rules = renameInRules("ticket_type", old, clean);
        audit("TICKET_TYPE_RENAME", t.getCode() + " \"" + old + "\" -> \"" + clean + "\"", actor);
        return rules;
    }

    @PreAuthorize("hasAuthority('ROLE_SYSTEM_ADMINISTRATOR')")
    @Transactional
    public void deleteType(Long id, ItsmUserPrincipal actor) {
        TicketType t = typeRepository.findById(id).orElseThrow(() -> notFound("Ticket type"));
        if (LOCKED_TYPES.contains(t.getCode())) {
            throw locked(t.getName());
        }
        t.setActive(false);
        typeRepository.save(t);
        audit("TICKET_TYPE_DELETE", t.getCode() + " \"" + t.getName() + "\"", actor);
    }

    // ------------------------------------------------------------------ categories

    @PreAuthorize("hasAuthority('ROLE_SYSTEM_ADMINISTRATOR')")
    @Transactional
    public Category addCategory(String name, ItsmUserPrincipal actor) {
        String clean = cleanName(name);
        String code = codeOf(clean);
        for (Category c : categoryRepository.findAll()) {
            if (c.isActive() && c.getName().equalsIgnoreCase(clean)) {
                throw new ItsmException("DUPLICATE", "Category \"" + clean + "\" already exists.");
            }
        }
        Category c = categoryRepository.findByCode(code).orElse(null);
        if (c == null) {
            c = new Category();
            c.setCode(code);
            c.setSortOrder(nextCategoryOrder());
        }
        c.setName(clean);
        c.setActive(true);
        c = categoryRepository.save(c);
        audit("CATEGORY_ADD", code + " \"" + clean + "\"", actor);
        return c;
    }

    @PreAuthorize("hasAuthority('ROLE_SYSTEM_ADMINISTRATOR')")
    @Transactional
    public int renameCategory(Long id, String name, ItsmUserPrincipal actor) {
        Category c = categoryRepository.findById(id).orElseThrow(() -> notFound("Category"));
        String clean = cleanName(name);
        if (clean.equals(c.getName())) {
            return 0;
        }
        if (LOCKED_CATEGORIES.contains(c.getCode())) {
            throw locked(c.getName());
        }
        for (Category o : categoryRepository.findAll()) {
            if (o.isActive() && !o.getCategoryId().equals(id) && o.getName().equalsIgnoreCase(clean)) {
                throw new ItsmException("DUPLICATE", "Category \"" + clean + "\" already exists.");
            }
        }
        String old = c.getName();
        c.setName(clean);
        categoryRepository.save(c);
        int rules = renameInRules("category", old, clean);
        audit("CATEGORY_RENAME", c.getCode() + " \"" + old + "\" -> \"" + clean + "\"", actor);
        return rules;
    }

    /** Hides the category and its sub-categories from Raise Request. */
    @PreAuthorize("hasAuthority('ROLE_SYSTEM_ADMINISTRATOR')")
    @Transactional
    public void deleteCategory(Long id, ItsmUserPrincipal actor) {
        Category c = categoryRepository.findById(id).orElseThrow(() -> notFound("Category"));
        if (LOCKED_CATEGORIES.contains(c.getCode())) {
            throw locked(c.getName());
        }
        c.setActive(false);
        categoryRepository.save(c);
        for (SubCategory s : subCategoryRepository.findByCategory(c)) {
            if (s.isActive()) {
                s.setActive(false);
                subCategoryRepository.save(s);
            }
        }
        audit("CATEGORY_DELETE", c.getCode() + " \"" + c.getName() + "\" (and its sub-categories)", actor);
    }

    // ------------------------------------------------------------------ sub-categories

    @PreAuthorize("hasAuthority('ROLE_SYSTEM_ADMINISTRATOR')")
    @Transactional
    public SubCategory addSubCategory(Long categoryId, String name, ItsmUserPrincipal actor) {
        Category c = categoryRepository.findById(categoryId).orElseThrow(() -> notFound("Category"));
        if (!c.isActive()) {
            throw new ItsmException("VALIDATION", "Choose an active category.");
        }
        String clean = cleanName(name);
        String code = codeOf(clean);
        for (SubCategory s : subCategoryRepository.findByCategory(c)) {
            if (s.isActive() && s.getName().equalsIgnoreCase(clean)) {
                throw new ItsmException("DUPLICATE", "\"" + clean + "\" already exists under " + c.getName() + ".");
            }
        }
        SubCategory s = subCategoryRepository.findByCategoryAndCode(c, code).orElse(null);
        if (s == null) {
            s = new SubCategory();
            s.setCategory(c);
            s.setCode(code);
            s.setSortOrder(nextSubOrder(c));
        }
        s.setName(clean);
        s.setActive(true);
        s = subCategoryRepository.save(s);
        audit("SUB_CATEGORY_ADD", c.getCode() + "/" + code + " \"" + clean + "\"", actor);
        return s;
    }

    @PreAuthorize("hasAuthority('ROLE_SYSTEM_ADMINISTRATOR')")
    @Transactional
    public int renameSubCategory(Long id, String name, ItsmUserPrincipal actor) {
        SubCategory s = subCategoryRepository.findById(id).orElseThrow(() -> notFound("Sub-category"));
        String clean = cleanName(name);
        if (clean.equals(s.getName())) {
            return 0;
        }
        for (SubCategory o : subCategoryRepository.findByCategory(s.getCategory())) {
            if (o.isActive() && !o.getSubCategoryId().equals(id) && o.getName().equalsIgnoreCase(clean)) {
                throw new ItsmException("DUPLICATE", "\"" + clean + "\" already exists under " + s.getCategory().getName() + ".");
            }
        }
        String old = s.getName();
        s.setName(clean);
        subCategoryRepository.save(s);
        int rules = renameInRules("sub_category", old, clean);
        audit("SUB_CATEGORY_RENAME", s.getCategory().getCode() + "/" + s.getCode() + " \"" + old + "\" -> \"" + clean + "\"", actor);
        return rules;
    }

    @PreAuthorize("hasAuthority('ROLE_SYSTEM_ADMINISTRATOR')")
    @Transactional
    public void deleteSubCategory(Long id, ItsmUserPrincipal actor) {
        SubCategory s = subCategoryRepository.findById(id).orElseThrow(() -> notFound("Sub-category"));
        s.setActive(false);
        subCategoryRepository.save(s);
        audit("SUB_CATEGORY_DELETE", s.getCategory().getCode() + "/" + s.getCode() + " \"" + s.getName() + "\"", actor);
    }

    // ------------------------------------------------------------------ helpers

    /** Replaces an old name by the new one in every workflow rule condition list for this key. */
    private int renameInRules(String key, String oldName, String newName) {
        int changed = 0;
        for (WorkflowRule r : ruleRepository.findAll()) {
            String json = r.getConditionJson();
            if (json == null || !json.contains(oldName)) {
                continue;
            }
            try {
                JsonNode root = objectMapper.readTree(json);
                JsonNode list = root.get(key);
                if (!(root instanceof ObjectNode) || list == null || !list.isArray()) {
                    continue;
                }
                ArrayNode updated = objectMapper.createArrayNode();
                boolean hit = false;
                for (JsonNode v : list) {
                    if (v.isTextual() && v.asText().equals(oldName)) {
                        updated.add(newName);
                        hit = true;
                    } else {
                        updated.add(v);
                    }
                }
                if (hit) {
                    ((ObjectNode) root).set(key, updated);
                    r.setConditionJson(objectMapper.writeValueAsString(root));
                    ruleRepository.save(r);
                    changed++;
                }
            } catch (java.io.IOException ex) {
                // An unreadable condition is left as it is; Workflow Config shows it for manual repair.
            }
        }
        return changed;
    }

    static String cleanName(String name) {
        String s = name == null ? "" : name.trim().replaceAll("\\s+", " ");
        if (s.length() < 2 || s.length() > 100) {
            throw new ItsmException("VALIDATION", "Name must be 2 to 100 characters.");
        }
        if (!s.matches("[\\p{L}\\p{N} &/().,'+-]+")) {
            throw new ItsmException("VALIDATION", "Name may contain letters, numbers, spaces and & / ( ) . , ' + - only.");
        }
        return s;
    }

    /** "Cloud Services" -> CLOUD_SERVICES (max 60 characters). */
    static String codeOf(String name) {
        String code = name.toUpperCase(Locale.ROOT).replaceAll("[^A-Z0-9]+", "_").replaceAll("^_+|_+$", "");
        if (code.isEmpty()) {
            code = "ITEM_" + Math.abs(name.hashCode());
        }
        return code.length() > 60 ? code.substring(0, 60) : code;
    }

    private int nextTypeOrder() {
        int max = 0;
        for (TicketType t : typeRepository.findAll()) {
            max = Math.max(max, t.getSortOrder());
        }
        return max + 10;
    }

    private int nextCategoryOrder() {
        int max = 0;
        for (Category c : categoryRepository.findAll()) {
            max = Math.max(max, c.getSortOrder());
        }
        return max + 10;
    }

    private int nextSubOrder(Category c) {
        int max = 0;
        List<SubCategory> subs = subCategoryRepository.findByCategory(c);
        for (SubCategory s : subs) {
            max = Math.max(max, s.getSortOrder());
        }
        return max + 10;
    }

    private static ItsmException notFound(String what) {
        return new ItsmException("NOT_FOUND", what + " not found.");
    }

    private static ItsmException locked(String name) {
        return new ItsmException("LOCKED", "\"" + name + "\" is used by reports and security views, so it cannot be renamed or deleted.");
    }

    private void audit(String action, String detail, ItsmUserPrincipal actor) {
        auditRecorder.record("ADMIN", action, detail + " (by " + (actor == null ? "?" : actor.getEmployeeNo()) + ")", "SUCCESS");
    }
}
