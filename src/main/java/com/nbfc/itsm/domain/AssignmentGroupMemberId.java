package com.nbfc.itsm.domain;

import javax.persistence.Column;
import javax.persistence.Embeddable;
import java.io.Serializable;
import java.util.Objects;

@Embeddable
public class AssignmentGroupMemberId implements Serializable {

    private static final long serialVersionUID = 1L;

    @Column(name = "assignment_group_id")
    private Long assignmentGroupId;

    @Column(name = "employee_id")
    private Long employeeId;

    public AssignmentGroupMemberId() {
    }

    public AssignmentGroupMemberId(Long assignmentGroupId, Long employeeId) {
        this.assignmentGroupId = assignmentGroupId;
        this.employeeId = employeeId;
    }

    public Long getAssignmentGroupId() {
        return assignmentGroupId;
    }

    public void setAssignmentGroupId(Long assignmentGroupId) {
        this.assignmentGroupId = assignmentGroupId;
    }

    public Long getEmployeeId() {
        return employeeId;
    }

    public void setEmployeeId(Long employeeId) {
        this.employeeId = employeeId;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof AssignmentGroupMemberId)) {
            return false;
        }
        AssignmentGroupMemberId that = (AssignmentGroupMemberId) o;
        return Objects.equals(assignmentGroupId, that.assignmentGroupId)
                && Objects.equals(employeeId, that.employeeId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(assignmentGroupId, employeeId);
    }
}
