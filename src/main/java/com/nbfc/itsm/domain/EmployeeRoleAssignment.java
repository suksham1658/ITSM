package com.nbfc.itsm.domain;

import javax.persistence.Column;
import javax.persistence.EmbeddedId;
import javax.persistence.Entity;
import javax.persistence.FetchType;
import javax.persistence.JoinColumn;
import javax.persistence.ManyToOne;
import javax.persistence.MapsId;
import javax.persistence.Table;
import java.time.Instant;

@Entity
@Table(name = "employee_role")
public class EmployeeRoleAssignment {

    @EmbeddedId
    private EmployeeRoleId id = new EmployeeRoleId();

    @ManyToOne(fetch = FetchType.LAZY)
    @MapsId("employeeId")
    @JoinColumn(name = "employee_id")
    private Employee employee;

    @ManyToOne(fetch = FetchType.LAZY)
    @MapsId("roleId")
    @JoinColumn(name = "role_id")
    private Role role;

    @Column(name = "assigned_at_utc", nullable = false)
    private Instant assignedAtUtc = Instant.now();

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "assigned_by_id")
    private Employee assignedBy;

    public EmployeeRoleId getId() {
        return id;
    }

    public void setId(EmployeeRoleId id) {
        this.id = id;
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

    public Role getRole() {
        return role;
    }

    public void setRole(Role role) {
        this.role = role;
        if (id != null && role != null) {
            id.setRoleId(role.getRoleId());
        }
    }

    public Instant getAssignedAtUtc() {
        return assignedAtUtc;
    }

    public void setAssignedAtUtc(Instant assignedAtUtc) {
        this.assignedAtUtc = assignedAtUtc;
    }

    public Employee getAssignedBy() {
        return assignedBy;
    }

    public void setAssignedBy(Employee assignedBy) {
        this.assignedBy = assignedBy;
    }
}
