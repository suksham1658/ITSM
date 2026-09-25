package com.nbfc.itsm.identity;

/**
 * Directory attributes used to refresh the portal {@code employee} row.
 * Roles are never taken from LDAP.
 */
public class LdapPerson {

    private String dn;
    private String samAccountName;
    private String employeeNo;
    private String displayName;
    private String email;
    private String designation;
    private String upn;
    private String managerDn;
    private String department;
    /** Managers above this person, immediate manager first (filled at login from the directory). */
    private java.util.List<LdapPerson> managerChain = new java.util.ArrayList<LdapPerson>();

    public String getManagerDn() {
        return managerDn;
    }

    public void setManagerDn(String managerDn) {
        this.managerDn = managerDn;
    }

    public String getDepartment() {
        return department;
    }

    public void setDepartment(String department) {
        this.department = department;
    }

    public java.util.List<LdapPerson> getManagerChain() {
        return managerChain;
    }

    public void setManagerChain(java.util.List<LdapPerson> managerChain) {
        this.managerChain = managerChain == null ? new java.util.ArrayList<LdapPerson>() : managerChain;
    }

    public String getDn() {
        return dn;
    }

    public void setDn(String dn) {
        this.dn = dn;
    }

    public String getSamAccountName() {
        return samAccountName;
    }

    public void setSamAccountName(String samAccountName) {
        this.samAccountName = samAccountName;
    }

    public String getEmployeeNo() {
        return employeeNo;
    }

    public void setEmployeeNo(String employeeNo) {
        this.employeeNo = employeeNo;
    }

    public String getDisplayName() {
        return displayName;
    }

    public void setDisplayName(String displayName) {
        this.displayName = displayName;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public String getDesignation() {
        return designation;
    }

    public void setDesignation(String designation) {
        this.designation = designation;
    }

    public String getUpn() {
        return upn;
    }

    public void setUpn(String upn) {
        this.upn = upn;
    }
}
