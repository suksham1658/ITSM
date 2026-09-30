package com.nbfc.itsm.admin;

import com.nbfc.itsm.audit.AuditRecorder;
import com.nbfc.itsm.domain.AssignmentGroup;
import com.nbfc.itsm.domain.AssignmentGroupMember;
import com.nbfc.itsm.domain.AssignmentGroupMemberRepository;
import com.nbfc.itsm.domain.AssignmentGroupRepository;
import com.nbfc.itsm.domain.Department;
import com.nbfc.itsm.domain.DepartmentRepository;
import com.nbfc.itsm.domain.Employee;
import com.nbfc.itsm.domain.EmployeeRepository;
import com.nbfc.itsm.exception.ItsmException;
import com.nbfc.itsm.security.ItsmUserPrincipal;
import com.nbfc.itsm.validation.Validation;
import com.nbfc.itsm.workflow.WorkflowEngine;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Workflow set-up for one employee, done by the System Administrator: reporting line (manager,
 * HOD, department) that Service Requests climb, and the assignment groups (Service Desk,
 * Implementors, IT Security) that pick up Incidents and fulfilment. Applied directly and audited.
 * The manager is normally synced from Active Directory at login; this is the override.
 */
@Service
public class EmployeeSetupService {

    private static final String SYSADMIN = "hasAuthority('ROLE_SYSTEM_ADMINISTRATOR')";

    private final EmployeeRepository employeeRepository;
    private final DepartmentRepository departmentRepository;
    private final AssignmentGroupRepository groupRepository;
    private final AssignmentGroupMemberRepository memberRepository;
    private final AuditRecorder auditRecorder;
    private final WorkflowEngine workflowEngine;

    public EmployeeSetupService(EmployeeRepository employeeRepository,
                                DepartmentRepository departmentRepository,
                                AssignmentGroupRepository groupRepository,
                                AssignmentGroupMemberRepository memberRepository,
                                AuditRecorder auditRecorder,
                                WorkflowEngine workflowEngine) {
        this.employeeRepository = employeeRepository;
        this.departmentRepository = departmentRepository;
        this.groupRepository = groupRepository;
        this.memberRepository = memberRepository;
        this.auditRecorder = auditRecorder;
        this.workflowEngine = workflowEngine;
    }

    /** Everything the user page needs to show and edit the set-up. */
    @PreAuthorize("hasAuthority('ADMIN_USER_MANAGE')")
    @Transactional(readOnly = true)
    public SetupView view(Long employeeId) {
        Employee e = require(employeeId);
        List<Employee> candidates = new ArrayList<Employee>();
        for (Employee c : employeeRepository.findAll()) {
            if (c.isPortalActive() && !c.getEmployeeId().equals(employeeId)) {
                candidates.add(c);
            }
        }
        candidates.sort(Comparator.comparing(Employee::getDisplayName, String.CASE_INSENSITIVE_ORDER));
        Set<Long> memberOf = new HashSet<Long>();
        for (AssignmentGroupMember m : memberRepository.findByEmployee(e)) {
            memberOf.add(m.getAssignmentGroup().getAssignmentGroupId());
        }
        // Same routing a new Service Request would get (WorkflowEngine.managerHops), so the preview never disagrees.
        List<String> chain = new ArrayList<String>();
        String chainProblem = null;
        if (e.getManager() != null) {
            try {
                for (Employee hop : workflowEngine.managerHops(e)) {
                    chain.add(hop.getDisplayName());
                }
            } catch (ItsmException ex) {
                chainProblem = ex.getMessage();
            }
        }
        return new SetupView(chainProblem,
                e.getManager() == null ? null : e.getManager().getEmployeeId(),
                e.getHod() == null ? null : e.getHod().getEmployeeId(),
                e.getDepartment() == null ? null : e.getDepartment().getDepartmentId(),
                e.getDepartment() == null ? null : e.getDepartment().getName(),
                chain, candidates, departmentRepository.findAll(), groupRepository.findByActiveTrueOrderByNameAsc(),
                memberOf);
    }

    /**
     * Sets manager, HOD and department ({@code null} clears). Refuses self-reporting, inactive
     * people and reporting loops, since a loop would make the approval chain go round in circles.
     */
    @PreAuthorize(SYSADMIN)
    @Transactional
    public void updateReportingLine(Long employeeId, Long managerId, Long hodId, Long departmentId,
                                    ItsmUserPrincipal actor) {
        Employee e = require(employeeId);
        Validation v = new Validation();
        Employee manager = managerId == null ? null : employeeRepository.findById(managerId).orElse(null);
        Employee hod = hodId == null ? null : employeeRepository.findById(hodId).orElse(null);
        Department dept = departmentId == null ? null : departmentRepository.findById(departmentId).orElse(null);
        v.check(managerId == null || manager != null, "Selected manager does not exist.");
        v.check(hodId == null || hod != null, "Selected HOD does not exist.");
        v.check(departmentId == null || dept != null, "Selected department does not exist.");
        if (manager != null) {
            v.check(!manager.getEmployeeId().equals(employeeId), "An employee cannot be their own manager.");
            v.check(manager.isPortalActive(), "The manager must have active portal access to approve requests.");
            v.check(!reportsTo(manager, e), manager.getDisplayName() + " already reports to "
                    + e.getDisplayName() + " (directly or indirectly); that would create a loop.");
        }
        if (hod != null && !hod.getEmployeeId().equals(employeeId)) {
            v.check(hod.isPortalActive(), "The HOD must have active portal access to approve requests.");
        }
        v.throwIfInvalid("SETUP_INVALID");

        e.setManager(manager);
        e.setHod(hod);
        e.setDepartment(dept);
        employeeRepository.save(e);
        auditRecorder.record("ADMIN", "REPORTING_LINE", e.getEmployeeNo() + ": manager="
                + (manager == null ? "-" : manager.getEmployeeNo()) + " hod=" + (hod == null ? "-" : hod.getEmployeeNo())
                + " dept=" + (dept == null ? "-" : dept.getCode()) + " (by " + actor.getUsername() + ")", "SUCCESS");
    }

    /** Makes the employee a member of exactly {@code groupIds} (Service Desk, Implementors, ...). */
    @PreAuthorize(SYSADMIN)
    @Transactional
    public void updateGroups(Long employeeId, Collection<Long> groupIds, ItsmUserPrincipal actor) {
        Employee e = require(employeeId);
        Set<Long> wanted = groupIds == null ? new HashSet<Long>() : new HashSet<Long>(groupIds);
        if (!wanted.isEmpty() && !e.isPortalActive()) {
            throw new ItsmException("SETUP_INVALID", "Enable portal access before adding the employee to groups.");
        }
        List<String> added = new ArrayList<String>();
        List<String> removed = new ArrayList<String>();
        for (AssignmentGroupMember m : memberRepository.findByEmployee(e)) {
            if (!wanted.remove(m.getAssignmentGroup().getAssignmentGroupId())) {
                removed.add(m.getAssignmentGroup().getCode());
                memberRepository.delete(m);
            }
        }
        for (Long id : wanted) {
            AssignmentGroup g = groupRepository.findById(id)
                    .orElseThrow(() -> new ItsmException("SETUP_INVALID", "Selected group does not exist."));
            AssignmentGroupMember m = new AssignmentGroupMember();
            m.setAssignmentGroup(g);
            m.setEmployee(e);
            memberRepository.save(m);
            added.add(g.getCode());
        }
        auditRecorder.record("ADMIN", "GROUPS", e.getEmployeeNo() + ": +" + added + " -" + removed
                + " (by " + actor.getUsername() + ")", "SUCCESS");
    }

    /** True when {@code person}'s reporting line reaches {@code boss}. */
    private static boolean reportsTo(Employee person, Employee boss) {
        Set<Long> seen = new HashSet<Long>();
        Employee up = person.getManager();
        while (up != null && seen.add(up.getEmployeeId())) {
            if (up.getEmployeeId().equals(boss.getEmployeeId())) {
                return true;
            }
            up = up.getManager();
        }
        return false;
    }

    private Employee require(Long id) {
        return employeeRepository.findById(id)
                .orElseThrow(() -> new ItsmException("EMPLOYEE_NOT_FOUND", "Employee not found"));
    }

    public static class SetupView {
        private final Long managerId;
        private final Long hodId;
        private final Long departmentId;
        private final String departmentName;
        private final List<String> managerChain;
        private final List<Employee> candidates;
        private final List<Department> departments;
        private final List<AssignmentGroup> groups;
        private final Set<Long> memberOf;
        private final String chainProblem;

        SetupView(String chainProblem, Long managerId, Long hodId, Long departmentId, String departmentName, List<String> managerChain,
                  List<Employee> candidates, List<Department> departments, List<AssignmentGroup> groups,
                  Set<Long> memberOf) {
            this.managerId = managerId;
            this.hodId = hodId;
            this.departmentId = departmentId;
            this.departmentName = departmentName;
            this.managerChain = managerChain;
            this.candidates = candidates;
            this.departments = departments;
            this.groups = groups;
            this.memberOf = memberOf;
            this.chainProblem = chainProblem;
        }

        /** Why a Service Request from this employee could not be routed right now, or null. */
        public String getChainProblem() { return chainProblem; }

        public Long getManagerId() { return managerId; }
        public Long getHodId() { return hodId; }
        public Long getDepartmentId() { return departmentId; }
        public String getDepartmentName() { return departmentName; }
        public List<String> getManagerChain() { return managerChain; }
        public List<Employee> getCandidates() { return candidates; }
        public List<Department> getDepartments() { return departments; }
        public List<AssignmentGroup> getGroups() { return groups; }
        public Set<Long> getMemberOf() { return memberOf; }
    }
}
