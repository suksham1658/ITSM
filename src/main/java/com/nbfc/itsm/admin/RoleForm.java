package com.nbfc.itsm.admin;

import java.util.ArrayList;
import java.util.List;

/** Role editor form: bound from {@code admin/role-form.html}. */
public class RoleForm {

    private String name;
    private boolean active = true;
    private List<String> permissionCodes = new ArrayList<String>();

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public boolean isActive() {
        return active;
    }

    public void setActive(boolean active) {
        this.active = active;
    }

    public List<String> getPermissionCodes() {
        return permissionCodes;
    }

    public void setPermissionCodes(List<String> permissionCodes) {
        this.permissionCodes = permissionCodes == null ? new ArrayList<String>() : permissionCodes;
    }
}
