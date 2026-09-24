package com.nbfc.itsm.security;

import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Signed-in employee. Holds every active role assigned in {@code employee_role}; authorities are
 * computed from the active role only when the user has switched to one ("View as"), otherwise
 * from all assigned roles combined. The active role can only ever be one of the assigned roles.
 */
public class ItsmUserPrincipal implements UserDetails {

    private static final long serialVersionUID = 2L;

    private final Long employeeId;
    private final String employeeNo;
    private final String username;
    private final String displayName;
    private final List<AssignedRole> assignedRoles;
    private final String activeRoleCode;
    private final List<GrantedAuthority> authorities;
    private final List<String> roleCodes;

    public ItsmUserPrincipal(Long employeeId, String employeeNo, String username, String displayName,
                             List<AssignedRole> assignedRoles, String activeRoleCode) {
        this.employeeId = employeeId;
        this.employeeNo = employeeNo;
        this.username = username;
        this.displayName = displayName;
        this.assignedRoles = Collections.unmodifiableList(new ArrayList<AssignedRole>(assignedRoles));
        AssignedRole active = activeRoleCode == null ? null : find(this.assignedRoles, activeRoleCode);
        if (activeRoleCode != null && active == null) {
            throw new IllegalArgumentException("Active role is not assigned to this employee");
        }
        this.activeRoleCode = active == null ? null : active.getCode();

        List<String> codes = new ArrayList<String>();
        Set<GrantedAuthority> granted = new LinkedHashSet<GrantedAuthority>();
        for (AssignedRole role : this.assignedRoles) {
            if (active != null && active != role) {
                continue;
            }
            codes.add(role.getCode());
            for (String p : role.getPermissionCodes()) {
                granted.add(new SimpleGrantedAuthority(p));
            }
        }
        for (String r : codes) {
            granted.add(new SimpleGrantedAuthority("ROLE_" + r));
        }
        this.roleCodes = Collections.unmodifiableList(codes);
        this.authorities = Collections.unmodifiableList(new ArrayList<GrantedAuthority>(granted));
    }

    private static AssignedRole find(List<AssignedRole> roles, String code) {
        for (AssignedRole role : roles) {
            if (role.getCode().equals(code)) {
                return role;
            }
        }
        return null;
    }

    public Long getEmployeeId() {
        return employeeId;
    }

    public String getEmployeeNo() {
        return employeeNo;
    }

    public String getDisplayName() {
        return displayName;
    }

    /** Role codes currently in effect: the active role, or every assigned role when none is selected. */
    public List<String> getRoleCodes() {
        return roleCodes;
    }

    /** Every active role assigned to the employee, regardless of the current "View as" selection. */
    public List<AssignedRole> getAssignedRoles() {
        return assignedRoles;
    }

    /** Code of the role selected in the role switcher, or {@code null} when all roles are combined. */
    public String getActiveRoleCode() {
        return activeRoleCode;
    }

    /** Identity of everything that drives access and the UI; used to detect role/permission changes. */
    public String fingerprint() {
        StringBuilder sb = new StringBuilder();
        sb.append(employeeId).append('|').append(displayName).append('|').append(activeRoleCode);
        for (AssignedRole r : assignedRoles) {
            sb.append('|').append(r.getCode()).append('=').append(r.getName()).append(r.getPermissionCodes());
        }
        return sb.toString();
    }

    public boolean isRoleSwitchAvailable() {
        return assignedRoles.size() > 1;
    }

    public boolean has(String authority) {
        if (authority == null) {
            return false;
        }
        for (GrantedAuthority a : authorities) {
            if (authority.equals(a.getAuthority())) {
                return true;
            }
        }
        return false;
    }

    public String getPrimaryRoleLabel() {
        if (assignedRoles.isEmpty()) {
            return "Employee";
        }
        if (activeRoleCode != null) {
            return find(assignedRoles, activeRoleCode).getName();
        }
        if (assignedRoles.size() == 1) {
            return assignedRoles.get(0).getName();
        }
        return "All roles (" + assignedRoles.size() + ")";
    }

    public String getInitials() {
        if (displayName != null && displayName.trim().length() > 0) {
            String[] parts = displayName.trim().split("\\s+");
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < parts.length && sb.length() < 2; i++) {
                if (parts[i].length() > 0) {
                    sb.append(Character.toUpperCase(parts[i].charAt(0)));
                }
            }
            if (sb.length() > 0) {
                return sb.toString();
            }
        }
        String u = username == null ? "U" : username;
        return u.substring(0, Math.min(2, u.length())).toUpperCase();
    }

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return authorities;
    }

    @Override
    public String getPassword() {
        return "";
    }

    @Override
    public String getUsername() {
        return username;
    }

    @Override
    public boolean isAccountNonExpired() {
        return true;
    }

    @Override
    public boolean isAccountNonLocked() {
        return true;
    }

    @Override
    public boolean isCredentialsNonExpired() {
        return true;
    }

    @Override
    public boolean isEnabled() {
        return true;
    }

    /** Snapshot of one assigned role, kept in the session with the principal. */
    public static class AssignedRole implements Serializable {

        private static final long serialVersionUID = 1L;

        private final String code;
        private final String name;
        private final List<String> permissionCodes;

        public AssignedRole(String code, String name, Collection<String> permissionCodes) {
            this.code = code;
            this.name = name;
            this.permissionCodes = Collections.unmodifiableList(new ArrayList<String>(permissionCodes));
        }

        public String getCode() {
            return code;
        }

        public String getName() {
            return name;
        }

        public List<String> getPermissionCodes() {
            return permissionCodes;
        }
    }
}
