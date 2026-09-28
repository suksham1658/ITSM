package com.nbfc.itsm.identity;

import java.time.Instant;

/**
 * Lockout-related state of one directory account, as shown on the AD Account Unlock page.
 * Read-only snapshot; the portal never changes passwords or enables/disables accounts.
 */
public class AdAccountStatus {

    private String dn;
    private String samAccountName;
    private String employeeNo;
    private String displayName;
    private String email;
    private String department;
    private String designation;
    private boolean locked;
    private Instant lockedAtUtc;
    private Integer badPasswordCount;
    private Instant lastBadPasswordUtc;
    private boolean disabled;
    private boolean passwordExpired;
    private Instant passwordLastSetUtc;

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

    public String getDepartment() {
        return department;
    }

    public void setDepartment(String department) {
        this.department = department;
    }

    public String getDesignation() {
        return designation;
    }

    public void setDesignation(String designation) {
        this.designation = designation;
    }

    public boolean isLocked() {
        return locked;
    }

    public void setLocked(boolean locked) {
        this.locked = locked;
    }

    public Instant getLockedAtUtc() {
        return lockedAtUtc;
    }

    public void setLockedAtUtc(Instant lockedAtUtc) {
        this.lockedAtUtc = lockedAtUtc;
    }

    public Integer getBadPasswordCount() {
        return badPasswordCount;
    }

    public void setBadPasswordCount(Integer badPasswordCount) {
        this.badPasswordCount = badPasswordCount;
    }

    public Instant getLastBadPasswordUtc() {
        return lastBadPasswordUtc;
    }

    public void setLastBadPasswordUtc(Instant lastBadPasswordUtc) {
        this.lastBadPasswordUtc = lastBadPasswordUtc;
    }

    public boolean isDisabled() {
        return disabled;
    }

    public void setDisabled(boolean disabled) {
        this.disabled = disabled;
    }

    public boolean isPasswordExpired() {
        return passwordExpired;
    }

    public void setPasswordExpired(boolean passwordExpired) {
        this.passwordExpired = passwordExpired;
    }

    public Instant getPasswordLastSetUtc() {
        return passwordLastSetUtc;
    }

    public void setPasswordLastSetUtc(Instant passwordLastSetUtc) {
        this.passwordLastSetUtc = passwordLastSetUtc;
    }
}
