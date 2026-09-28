package com.nbfc.itsm.workflow;

import com.nbfc.itsm.domain.AssignmentGroup;
import com.nbfc.itsm.domain.AssignmentGroupMember;
import com.nbfc.itsm.domain.AssignmentGroupMemberRepository;
import com.nbfc.itsm.domain.Employee;
import com.nbfc.itsm.domain.EmployeeRoleAssignment;
import com.nbfc.itsm.domain.EmployeeRoleAssignmentRepository;
import com.nbfc.itsm.domain.Role;
import com.nbfc.itsm.domain.RoleRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Who belongs to a work group (Service Desk, Implementors, ...). A person is a member when they
 * were added to the group on Admin &gt; Users, <b>or</b> when they hold the role that goes with the
 * group, so giving someone the IT Service Desk role is enough for them to work the desk queue.
 */
@Service
public class GroupMembershipService {

    /** Work group code → role whose active holders count as members. */
    static final Map<String, String> ROLE_FOR_GROUP;

    static {
        Map<String, String> m = new HashMap<String, String>();
        m.put("IT_SERVICE_DESK", "IT_SERVICE_DESK");
        m.put("IT_IMPLEMENTORS", "IT_IMPLEMENTOR");
        ROLE_FOR_GROUP = Collections.unmodifiableMap(m);
    }

    private final AssignmentGroupMemberRepository memberRepository;
    private final EmployeeRoleAssignmentRepository assignmentRepository;
    private final RoleRepository roleRepository;

    public GroupMembershipService(AssignmentGroupMemberRepository memberRepository,
                                  EmployeeRoleAssignmentRepository assignmentRepository,
                                  RoleRepository roleRepository) {
        this.memberRepository = memberRepository;
        this.assignmentRepository = assignmentRepository;
        this.roleRepository = roleRepository;
    }

    @Transactional(readOnly = true)
    public boolean isMember(AssignmentGroup group, Employee employee) {
        if (group == null || employee == null) {
            return false;
        }
        if (memberRepository.existsByAssignmentGroupAndEmployee(group, employee)) {
            return true;
        }
        String roleCode = ROLE_FOR_GROUP.get(group.getCode());
        if (roleCode == null) {
            return false;
        }
        for (EmployeeRoleAssignment a : assignmentRepository.findByEmployee(employee)) {
            Role r = a.getRole();
            if (r != null && r.isActive() && roleCode.equals(r.getCode())) {
                return true;
            }
        }
        return false;
    }

    /** Portal-active members (explicit and by role), sorted by name. */
    @Transactional(readOnly = true)
    public List<Employee> activeMembers(AssignmentGroup group) {
        Map<Long, Employee> byId = new LinkedHashMap<Long, Employee>();
        if (group == null) {
            return new ArrayList<Employee>();
        }
        for (AssignmentGroupMember m : memberRepository.findByAssignmentGroup(group)) {
            byId.put(m.getEmployee().getEmployeeId(), m.getEmployee());
        }
        String roleCode = ROLE_FOR_GROUP.get(group.getCode());
        Role role = roleCode == null ? null : roleRepository.findByCode(roleCode).orElse(null);
        if (role != null && role.isActive()) {
            for (EmployeeRoleAssignment a : assignmentRepository.findByRole(role)) {
                byId.put(a.getEmployee().getEmployeeId(), a.getEmployee());
            }
        }
        List<Employee> out = new ArrayList<Employee>();
        for (Employee e : byId.values()) {
            if (e.isPortalActive()) {
                out.add(e);
            }
        }
        out.sort((a, b) -> String.CASE_INSENSITIVE_ORDER.compare(a.getDisplayName(), b.getDisplayName()));
        return out;
    }

    /** The role that counts as membership of {@code groupCode}, or null. */
    public static String linkedRole(String groupCode) {
        return ROLE_FOR_GROUP.get(groupCode);
    }
}
