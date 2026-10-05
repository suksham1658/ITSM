package com.nbfc.itsm.web;

import com.nbfc.itsm.admin.RoleAdminService;
import com.nbfc.itsm.admin.RoleForm;
import com.nbfc.itsm.domain.ConfigChangeRequest;
import com.nbfc.itsm.domain.Permission;
import com.nbfc.itsm.domain.Role;
import com.nbfc.itsm.exception.ItsmException;
import com.nbfc.itsm.security.ItsmUserPrincipal;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

/**
 * Roles &amp; Permissions: list, read, and propose create/edit. Proposals land in
 * Config approvals and take effect only after a different administrator approves them.
 */
@Controller
@RequestMapping("/admin/roles")
public class AdminRoleController {

    private static final String CAN_PROPOSE =
            "hasAuthority('ADMIN_USER_MANAGE') and hasAuthority('ADMIN_MASTERDATA_PROPOSE')";
    /** Deleting roles is reserved for the System Administrator role (while it is the active role). */
    private static final String CAN_DELETE = "hasAuthority('ROLE_SYSTEM_ADMINISTRATOR')";

    private final RoleAdminService roleAdminService;

    public AdminRoleController(RoleAdminService roleAdminService) {
        this.roleAdminService = roleAdminService;
    }

    @GetMapping
    @PreAuthorize("hasAuthority('ADMIN_USER_MANAGE')")
    public String list(Model model) {
        model.addAttribute("roles", roleAdminService.list());
        model.addAttribute("pendingRoleChanges", roleAdminService.pendingChanges());
        model.addAttribute("nav", "adminRoles");
        model.addAttribute("pageTitle", "Roles & Permissions");
        return "admin/roles";
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('ADMIN_USER_MANAGE')")
    public String detail(@PathVariable("id") Long id, Model model) {
        RoleAdminService.RoleDetail detail = roleAdminService.get(id);
        model.addAttribute("detail", detail);
        model.addAttribute("nav", "adminRoles");
        model.addAttribute("pageTitle", detail.getRole().getName());
        return "admin/role-detail";
    }

    @GetMapping("/new")
    @PreAuthorize(CAN_PROPOSE)
    public String newRole(Model model) {
        return form(model, null, new RoleForm(), null);
    }

    @PostMapping
    @PreAuthorize(CAN_PROPOSE)
    public String create(@ModelAttribute("roleForm") RoleForm form,
                         @AuthenticationPrincipal ItsmUserPrincipal maker,
                         Model model,
                         RedirectAttributes ra) {
        try {
            if (isSystemAdministrator(maker)) {
                Role r = roleAdminService.createNow(form, maker);
                ra.addFlashAttribute("message", "Role '" + r.getName() + "' created and applied immediately.");
                return "redirect:/admin/roles/" + r.getRoleId();
            }
            ConfigChangeRequest ccr = roleAdminService.proposeCreate(form, maker);
            ra.addFlashAttribute("message", "Role proposed as change request #" + ccr.getConfigChangeRequestId()
                    + ". A different administrator must approve it in Config approvals before it can be assigned.");
            return "redirect:/admin/roles";
        } catch (ItsmException ex) {
            return form(model, null, form, ex.getMessage());
        }
    }

    @GetMapping("/{id}/edit")
    @PreAuthorize(CAN_PROPOSE)
    public String edit(@PathVariable("id") Long id, Model model) {
        return form(model, roleAdminService.role(id), roleAdminService.formFor(id), null);
    }

    @PostMapping("/{id}")
    @PreAuthorize(CAN_PROPOSE)
    public String update(@PathVariable("id") Long id,
                         @ModelAttribute("roleForm") RoleForm form,
                         @AuthenticationPrincipal ItsmUserPrincipal maker,
                         Model model,
                         RedirectAttributes ra) {
        try {
            if (isSystemAdministrator(maker)) {
                roleAdminService.updateNow(id, form, maker);
                ra.addFlashAttribute("message", "Role updated and applied immediately.");
                return "redirect:/admin/roles/" + id;
            }
            ConfigChangeRequest ccr = roleAdminService.proposeUpdate(id, form, maker);
            ra.addFlashAttribute("message", "Change proposed as request #" + ccr.getConfigChangeRequestId()
                    + ". It takes effect after a different administrator approves it.");
            return "redirect:/admin/roles/" + id;
        } catch (ItsmException ex) {
            return form(model, roleAdminService.role(id), form, ex.getMessage());
        }
    }

    /** System Administrator: add a brand-new permission (applied immediately, assignable to roles). */
    @PostMapping("/permissions")
    @PreAuthorize(CAN_DELETE)
    public String addPermission(@RequestParam("code") String code,
                                @RequestParam(value = "description", required = false) String description,
                                @AuthenticationPrincipal ItsmUserPrincipal actor, RedirectAttributes ra) {
        try {
            Permission p = roleAdminService.addPermission(code, description, actor);
            ra.addFlashAttribute("message", "Permission '" + p.getCode() + "' added. It can now be assigned to any role.");
        } catch (ItsmException ex) {
            ra.addFlashAttribute("errorMessage", ex.getMessage());
        }
        return "redirect:/admin/roles";
    }

    private static boolean isSystemAdministrator(ItsmUserPrincipal user) {
        return user != null && user.getRoleCodes() != null && user.getRoleCodes().contains("SYSTEM_ADMINISTRATOR");
    }

    /** Confirmation page: either "Delete role?" or "Cannot delete role" with the reasons. */
    @GetMapping("/{id}/delete")
    @PreAuthorize(CAN_DELETE)
    public String confirmDelete(@PathVariable("id") Long id, Model model) {
        Role role = roleAdminService.role(id);
        model.addAttribute("role", role);
        model.addAttribute("blockers", roleAdminService.deleteBlockers(id));
        model.addAttribute("permissionGroups",
                roleAdminService.permissionGroups(roleAdminService.formFor(id).getPermissionCodes(), true));
        model.addAttribute("nav", "adminRoles");
        model.addAttribute("pageTitle", "Delete role: " + role.getName());
        return "admin/role-delete";
    }

    @PostMapping("/{id}/delete")
    @PreAuthorize(CAN_DELETE)
    public String delete(@PathVariable("id") Long id,
                         @AuthenticationPrincipal ItsmUserPrincipal actor,
                         RedirectAttributes ra) {
        Role role = roleAdminService.role(id);
        String name = role.getName();
        try {
            roleAdminService.delete(id, actor);
            ra.addFlashAttribute("message", "Role '" + name + "' and its permissions were deleted.");
            return "redirect:/admin/roles";
        } catch (ItsmException ex) {
            ra.addFlashAttribute("errorMessage", ex.getMessage());
            return "redirect:/admin/roles/" + id + "/delete";
        }
    }

    private String form(Model model, Role role, RoleForm form, String error) {
        model.addAttribute("role", role);
        model.addAttribute("roleForm", form);
        model.addAttribute("permissionGroups", roleAdminService.permissionGroups(form.getPermissionCodes(), false));
        model.addAttribute("errorMessage", error);
        model.addAttribute("nav", "adminRoles");
        model.addAttribute("pageTitle", role == null ? "New role" : "Edit role — " + role.getName());
        return "admin/role-form";
    }
}
