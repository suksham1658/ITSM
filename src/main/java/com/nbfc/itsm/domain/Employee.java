package com.nbfc.itsm.domain;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.FetchType;
import javax.persistence.GeneratedValue;
import javax.persistence.GenerationType;
import javax.persistence.Id;
import javax.persistence.JoinColumn;
import javax.persistence.ManyToOne;
import javax.persistence.OneToMany;
import javax.persistence.Table;
import java.time.Instant;
import java.util.HashSet;
import java.util.Set;

@Entity
@Table(name = "employee")
public class Employee extends BaseAuditableEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "employee_id")
    private Long employeeId;

    @Column(name = "employee_no", nullable = false, unique = true, length = 20)
    private String employeeNo;

    @Column(name = "upn", length = 256)
    private String upn;

    @Column(name = "sam_account_name", length = 64)
    private String samAccountName;

    @Column(name = "display_name", nullable = false, length = 128)
    private String displayName;

    @Column(name = "designation", length = 128)
    private String designation;

    /** From AD telephoneNumber (or mobile). Shown read-only to the people working on this person's tickets. */
    @Column(name = "phone_number", length = 64)
    private String phoneNumber;

    /** From AD physicalDeliveryOfficeName, else street address and city. */
    @Column(name = "office_location", length = 256)
    private String officeLocation;

    @Column(name = "email", length = 256)
    private String email;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "department_id")
    private Department department;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "manager_id")
    private Employee manager;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "hod_id")
    private Employee hod;

    /** Backup approver: may act on approvals resolved to this employee (Admin &gt; Users &gt; Delegate). */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "delegate_id")
    private Employee delegate;

    @Column(name = "portal_active", nullable = false)
    private boolean portalActive;

    @Column(name = "last_ldap_sync_utc")
    private Instant lastLdapSyncUtc;

    @Column(name = "notes", length = 512)
    private String notes;

    @OneToMany(mappedBy = "employee")
    private Set<EmployeeRoleAssignment> roleAssignments = new HashSet<EmployeeRoleAssignment>();

    public Long getEmployeeId() {
        return employeeId;
    }

    public void setEmployeeId(Long employeeId) {
        this.employeeId = employeeId;
    }

    public String getEmployeeNo() {
        return employeeNo;
    }

    public void setEmployeeNo(String employeeNo) {
        this.employeeNo = employeeNo;
    }

    public String getUpn() {
        return upn;
    }

    public void setUpn(String upn) {
        this.upn = upn;
    }

    public String getSamAccountName() {
        return samAccountName;
    }

    public void setSamAccountName(String samAccountName) {
        this.samAccountName = samAccountName;
    }

    public String getDisplayName() {
        return displayName;
    }

    public void setDisplayName(String displayName) {
        this.displayName = displayName;
    }

    public String getDesignation() {
        return designation;
    }

    public String getPhoneNumber() {
        return phoneNumber;
    }

    public void setPhoneNumber(String phoneNumber) {
        this.phoneNumber = phoneNumber;
    }

    public String getOfficeLocation() {
        return officeLocation;
    }

    public void setOfficeLocation(String officeLocation) {
        this.officeLocation = officeLocation;
    }

    public void setDesignation(String designation) {
        this.designation = designation;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public Department getDepartment() {
        return department;
    }

    public void setDepartment(Department department) {
        this.department = department;
    }

    public Employee getManager() {
        return manager;
    }

    public void setManager(Employee manager) {
        this.manager = manager;
    }

    public Employee getHod() {
        return hod;
    }

    public void setHod(Employee hod) {
        this.hod = hod;
    }

    public Employee getDelegate() {
        return delegate;
    }

    public void setDelegate(Employee delegate) {
        this.delegate = delegate;
    }

    public boolean isPortalActive() {
        return portalActive;
    }

    public void setPortalActive(boolean portalActive) {
        this.portalActive = portalActive;
    }

    public Instant getLastLdapSyncUtc() {
        return lastLdapSyncUtc;
    }

    public void setLastLdapSyncUtc(Instant lastLdapSyncUtc) {
        this.lastLdapSyncUtc = lastLdapSyncUtc;
    }

    public String getNotes() {
        return notes;
    }

    public void setNotes(String notes) {
        this.notes = notes;
    }

    public Set<EmployeeRoleAssignment> getRoleAssignments() {
        return roleAssignments;
    }

    public void setRoleAssignments(Set<EmployeeRoleAssignment> roleAssignments) {
        this.roleAssignments = roleAssignments;
    }

    /** Roles assigned by IT Admin / SysAdmin only (never from LDAP). */
    public Set<Role> getRoles() {
        Set<Role> roles = new HashSet<Role>();
        if (roleAssignments == null) {
            return roles;
        }
        for (EmployeeRoleAssignment assignment : roleAssignments) {
            if (assignment.getRole() != null) {
                roles.add(assignment.getRole());
            }
        }
        return roles;
    }
}
