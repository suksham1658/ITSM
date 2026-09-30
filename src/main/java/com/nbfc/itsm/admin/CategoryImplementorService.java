package com.nbfc.itsm.admin;

import com.nbfc.itsm.audit.AuditRecorder;
import com.nbfc.itsm.domain.AssignmentGroup;
import com.nbfc.itsm.domain.AssignmentGroupRepository;
import com.nbfc.itsm.domain.Category;
import com.nbfc.itsm.domain.CategoryRepository;
import com.nbfc.itsm.domain.Employee;
import com.nbfc.itsm.exception.ItsmException;
import com.nbfc.itsm.security.ItsmUserPrincipal;
import com.nbfc.itsm.workflow.GroupMembershipService;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Admin &gt; Categories: which implementors handle each category. The IT Service Desk then chooses from
 * that list (one, several or all); with none set it chooses from the whole IT Implementors group.
 */
@Service
public class CategoryImplementorService {

    static final String IMPLEMENTOR_GROUP = "IT_IMPLEMENTORS";

    private final CategoryRepository categoryRepository;
    private final AssignmentGroupRepository groupRepository;
    private final GroupMembershipService groupMembership;
    private final AuditRecorder auditRecorder;

    public CategoryImplementorService(CategoryRepository categoryRepository, AssignmentGroupRepository groupRepository,
                                      GroupMembershipService groupMembership, AuditRecorder auditRecorder) {
        this.categoryRepository = categoryRepository;
        this.groupRepository = groupRepository;
        this.groupMembership = groupMembership;
        this.auditRecorder = auditRecorder;
    }

    /** People who can be made implementors: members and role holders of IT Implementors, active only. */
    @Transactional(readOnly = true)
    public List<Employee> candidates() {
        AssignmentGroup g = groupRepository.findByCode(IMPLEMENTOR_GROUP).orElse(null);
        List<Employee> list = g == null ? new ArrayList<Employee>() : new ArrayList<Employee>(groupMembership.activeMembers(g));
        list.sort(java.util.Comparator.comparing(Employee::getDisplayName, String.CASE_INSENSITIVE_ORDER));
        return list;
    }

    /** Category id to the implementor ids assigned to it. */
    @Transactional(readOnly = true)
    public Map<Long, Set<Long>> assignments(List<Category> categories) {
        Map<Long, Set<Long>> out = new LinkedHashMap<Long, Set<Long>>();
        for (Category c : categories) {
            Category loaded = categoryRepository.findById(c.getCategoryId()).orElse(c);
            out.put(c.getCategoryId(), new LinkedHashSet<Long>(loaded.getImplementorIds()));
        }
        return out;
    }

    /** Replaces the category's implementors (each person once; only people from {@link #candidates()}). */
    @PreAuthorize("hasAuthority('ROLE_SYSTEM_ADMINISTRATOR')")
    @Transactional
    public int save(Long categoryId, List<Long> employeeIds, ItsmUserPrincipal actor) {
        Category c = categoryRepository.findById(categoryId)
                .orElseThrow(() -> new ItsmException("CATEGORY_NOT_FOUND", "Category not found."));
        Set<Long> allowed = new LinkedHashSet<Long>();
        for (Employee e : candidates()) {
            allowed.add(e.getEmployeeId());
        }
        Set<Long> chosen = new LinkedHashSet<Long>();
        if (employeeIds != null) {
            for (Long id : employeeIds) {
                if (id == null) {
                    continue;
                }
                if (!allowed.contains(id)) {
                    throw new ItsmException("VALIDATION", "Only IT implementors can be assigned to a category.");
                }
                chosen.add(id);
            }
        }
        String before = c.getImplementorIds().toString();
        c.getImplementorIds().clear();
        c.getImplementorIds().addAll(chosen);
        categoryRepository.save(c);
        auditRecorder.record("ADMIN", "CATEGORY_IMPLEMENTORS", c.getCode() + ": " + before + " -> " + chosen
                + " (by " + actor.getEmployeeNo() + ")", "SUCCESS");
        return chosen.size();
    }
}
