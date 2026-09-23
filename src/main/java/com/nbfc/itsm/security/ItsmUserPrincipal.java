package com.nbfc.itsm.security;

import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

public class ItsmUserPrincipal implements UserDetails {

    private static final long serialVersionUID = 1L;

    private final Long employeeId;
    private final String employeeNo;
    private final String username;
    private final String displayName;
    private final List<GrantedAuthority> authorities;
    private final List<String> roleCodes;

    public ItsmUserPrincipal(Long employeeId, String employeeNo, String username, String displayName,
                             Collection<String> permissionCodes, Collection<String> roleCodes) {
        this.employeeId = employeeId;
        this.employeeNo = employeeNo;
        this.username = username;
        this.displayName = displayName;
        this.roleCodes = Collections.unmodifiableList(new ArrayList<String>(roleCodes));
        Set<GrantedAuthority> granted = new LinkedHashSet<GrantedAuthority>();
        for (String p : permissionCodes) {
            granted.add(new SimpleGrantedAuthority(p));
        }
        for (String r : this.roleCodes) {
            granted.add(new SimpleGrantedAuthority("ROLE_" + r));
        }
        this.authorities = Collections.unmodifiableList(new ArrayList<GrantedAuthority>(granted));
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

    public List<String> getRoleCodes() {
        return roleCodes;
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
        if (roleCodes.isEmpty()) {
            return "Employee";
        }
        return roleCodes.get(0).replace('_', ' ');
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
}
