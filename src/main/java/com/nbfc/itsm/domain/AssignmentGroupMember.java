package com.nbfc.itsm.domain;

import javax.persistence.EmbeddedId;
import javax.persistence.Entity;
import javax.persistence.FetchType;
import javax.persistence.JoinColumn;
import javax.persistence.ManyToOne;
import javax.persistence.MapsId;
import javax.persistence.Table;

@Entity
@Table(name = "assignment_group_member")
public class AssignmentGroupMember {

    @EmbeddedId
    private AssignmentGroupMemberId id = new AssignmentGroupMemberId();

    @ManyToOne(fetch = FetchType.LAZY)
    @MapsId("assignmentGroupId")
    @JoinColumn(name = "assignment_group_id")
    private AssignmentGroup assignmentGroup;

    @ManyToOne(fetch = FetchType.LAZY)
    @MapsId("employeeId")
    @JoinColumn(name = "employee_id")
    private Employee employee;

    public AssignmentGroupMemberId getId() {
        return id;
    }

    public void setId(AssignmentGroupMemberId id) {
        this.id = id;
    }

    public AssignmentGroup getAssignmentGroup() {
        return assignmentGroup;
    }

    public void setAssignmentGroup(AssignmentGroup assignmentGroup) {
        this.assignmentGroup = assignmentGroup;
        if (id != null && assignmentGroup != null) {
            id.setAssignmentGroupId(assignmentGroup.getAssignmentGroupId());
        }
    }

    public Employee getEmployee() {
        return employee;
    }

    public void setEmployee(Employee employee) {
        this.employee = employee;
        if (id != null && employee != null) {
            id.setEmployeeId(employee.getEmployeeId());
        }
    }
}
