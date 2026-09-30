package com.nbfc.itsm.web;

import com.nbfc.itsm.admin.AdminUserService;
import com.nbfc.itsm.admin.EmployeeSetupService;
import com.nbfc.itsm.admin.UserAccountService;
import com.nbfc.itsm.domain.ConfigChangeRequest;
import com.nbfc.itsm.domain.Employee;
import com.nbfc.itsm.domain.Role;
import com.nbfc.itsm.exception.ItsmException;
import com.nbfc.itsm.security.ItsmUserPrincipal;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import javax.servlet.http.HttpServletRequest;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Controller
@RequestMapping("/admin")
public class AdminUserController {

    private final AdminUserService adminUserService;
    private final EmployeeSetupService employeeSetupService;
    private final UserAccountService userAccountService;

    public AdminUserController(AdminUserService adminUserService, EmployeeSetupService employeeSetupService,
                               UserAccountService userAccountService) {
        this.adminUserService = adminUserService;
        this.employeeSetupService = employeeSetupService;
        this.userAccountService = userAccountService;
    }

    @GetMapping("/users")
    @PreAuthorize("hasAuthority('ADMIN_USER_MANAGE')")
    public String users(@RequestParam(value = "q", required = false) String q,
                        @RequestParam(value = "status", required = false) String status, Model model) {
        q = SearchText.clean(q);
        String s = "active".equals(status) || "inactive".equals(status) ? status : null;
        model.addAttribute("employees", adminUserService.list(q, s));
        model.addAttribute("q", q);
        model.addAttribute("status", s);
        model.addAttribute("nav", "adminUsers");
        model.addAttribute("pageTitle", "Portal users");
        return "admin/users";
    }

    @GetMapping("/users/{id}")
    @PreAuthorize("hasAuthority('ADMIN_USER_MANAGE')")
    public String user(@PathVariable("id") Long id, Model model, HttpServletRequest request) {
        Employee employee = adminUserService.get(id);
        BackLinks.addTo(model, request, "/admin/users", "Back to Users", "itsm.back.user");
        Set<Long> assignedRoleIds = new HashSet<Long>();
        for (Role role : employee.getRoles()) {
            assignedRoleIds.add(role.getRoleId());
        }
        EmployeeSetupService.SetupView setup = employeeSetupService.view(id);
        List<ConfigChangeRequest> pending = adminUserService.pendingFor(id);
        Set<String> pendingRoleKeys = new HashSet<String>();
        boolean reactivationPending = false;
        for (ConfigChangeRequest c : pending) {
            if (c.getChangeType() != null && c.getChangeType().startsWith("USER_ROLE_")) {
                pendingRoleKeys.add(c.getEntityKey());
            } else if ("USER_PORTAL_ACTIVE".equals(c.getChangeType())) {
                reactivationPending = true;
            }
        }
        model.addAttribute("employee", employee);
        model.addAttribute("setup", setup);
        model.addAttribute("delegateCandidates", setup.getCandidates());
        model.addAttribute("currentDelegateId", employee.getDelegate() == null ? null : employee.getDelegate().getEmployeeId());
        model.addAttribute("currentDelegateName", employee.getDelegate() == null ? null : employee.getDelegate().getDisplayName());
        model.addAttribute("pendingChanges", pending);
        model.addAttribute("pendingRoleKeys", pendingRoleKeys);
        model.addAttribute("reactivationPending", reactivationPending);
        model.addAttribute("allRoles", adminUserService.roles());
        model.addAttribute("assignedRoleIds", assignedRoleIds);
        model.addAttribute("nav", "adminUsers");
        model.addAttribute("pageTitle", employee.getDisplayName());
        return "admin/user-detail";
    }

    /** Re-activation only: goes to a second administrator. Deactivation is immediate ({@link #deactivate}). */
    @PostMapping("/users/{id}/propose-active")
    @PreAuthorize("hasAuthority('ADMIN_MASTERDATA_PROPOSE')")
    public String proposeActive(@PathVariable("id") Long id,
                                @RequestParam("active") boolean active,
                                @AuthenticationPrincipal ItsmUserPrincipal maker,
                                RedirectAttributes ra) {
        if (!active) {
            return deactivate(id, maker, ra);
        }
        try {
            adminUserService.proposePortalActive(id, true, maker);
            ra.addFlashAttribute("message", "Re-activation submitted. A different administrator must approve it in Config approvals.");
        } catch (ItsmException ex) {
            ra.addFlashAttribute("errorMessage", ex.getMessage());
        }
        return "redirect:/admin/users/" + id;
    }

    @PostMapping("/users/{id}/deactivate")
    @PreAuthorize("hasAuthority('ADMIN_USER_MANAGE')")
    public String deactivate(@PathVariable("id") Long id, @AuthenticationPrincipal ItsmUserPrincipal actor,
                             RedirectAttributes ra) {
        try {
            userAccountService.deactivate(id, actor);
            ra.addFlashAttribute("message", "Portal access deactivated immediately. The user is signed out on their next click.");
        } catch (ItsmException ex) {
            ra.addFlashAttribute("errorMessage", ex.getMessage());
        }
        return "redirect:/admin/users/" + id;
    }

    @PostMapping("/users/{id}/basic")
    @PreAuthorize("hasAuthority('ADMIN_USER_MANAGE')")
    public String basicInfo(@PathVariable("id") Long id, @RequestParam("displayName") String name,
                            @RequestParam(value = "email", required = false) String email,
                            @RequestParam(value = "designation", required = false) String designation,
                            @AuthenticationPrincipal ItsmUserPrincipal actor, RedirectAttributes ra) {
        try {
            userAccountService.updateBasicInfo(id, name, email, designation, actor);
            ra.addFlashAttribute("message", "Basic information saved.");
        } catch (ItsmException ex) {
            ra.addFlashAttribute("errorMessage", ex.getMessage());
        }
        return "redirect:/admin/users/" + id;
    }

    @PostMapping("/users/{id}/delegate")
    @PreAuthorize("hasAuthority('ADMIN_USER_MANAGE')")
    public String delegate(@PathVariable("id") Long id,
                           @RequestParam(value = "delegateId", required = false) Long delegateId,
                           @AuthenticationPrincipal ItsmUserPrincipal actor, RedirectAttributes ra) {
        try {
            userAccountService.setDelegate(id, delegateId, actor);
            ra.addFlashAttribute("message", delegateId == null ? "Delegate removed." : "Delegate set. It applies immediately.");
        } catch (ItsmException ex) {
            ra.addFlashAttribute("errorMessage", ex.getMessage());
        }
        return "redirect:/admin/users/" + id;
    }

    @PostMapping("/users/{id}/resync")
    @PreAuthorize("hasAuthority('ADMIN_USER_MANAGE')")
    public String resync(@PathVariable("id") Long id, @AuthenticationPrincipal ItsmUserPrincipal actor,
                         RedirectAttributes ra) {
        try {
            Employee e = userAccountService.resync(id, actor);
            ra.addFlashAttribute("message", e.getDisplayName() + " re-synced from LDAP: name, e-mail, title, department and "
                    + "manager refreshed. Roles, delegate and portal access were kept.");
        } catch (ItsmException ex) {
            ra.addFlashAttribute("errorMessage", ex.getMessage());
        }
        return "redirect:/admin/users/" + id;
    }

    @PostMapping("/users/{id}/propose-role")
    @PreAuthorize("hasAuthority('ADMIN_MASTERDATA_PROPOSE')")
    public String proposeRole(@PathVariable("id") Long id,
                              @RequestParam("roleId") Long roleId,
                              @RequestParam("assign") boolean assign,
                              @AuthenticationPrincipal ItsmUserPrincipal maker,
                              RedirectAttributes ra) {
        try {
            ConfigChangeRequest ccr = adminUserService.proposeRole(id, roleId, assign, maker);
            if ("Applied".equals(ccr.getStatusCode())) {
                ra.addFlashAttribute("message", (assign ? "Role assigned. " : "Role removed. ")
                        + "The change is in effect now; the user gets it on their next page load.");
            } else {
                ra.addFlashAttribute("message", "Role change submitted. A different administrator must approve it in Config approvals.");
            }
        } catch (ItsmException ex) {
            ra.addFlashAttribute("errorMessage", ex.getMessage());
        }
        return "redirect:/admin/users/" + id;
    }

    @PostMapping("/users/{id}/reporting-line")
    @PreAuthorize("hasAuthority('ROLE_SYSTEM_ADMINISTRATOR')")
    public String reportingLine(@PathVariable("id") Long id,
                                @RequestParam(value = "managerId", required = false) Long managerId,
                                @RequestParam(value = "hodId", required = false) Long hodId,
                                @RequestParam(value = "departmentId", required = false) Long departmentId,
                                @AuthenticationPrincipal ItsmUserPrincipal actor,
                                RedirectAttributes ra) {
        try {
            employeeSetupService.updateReportingLine(id, managerId, hodId, departmentId, actor);
            ra.addFlashAttribute("message", "Reporting line saved. New Service Requests follow it.");
        } catch (ItsmException ex) {
            ra.addFlashAttribute("errorMessage", ex.getMessage());
        }
        return "redirect:/admin/users/" + id;
    }

    @PostMapping("/users/{id}/groups")
    @PreAuthorize("hasAuthority('ROLE_SYSTEM_ADMINISTRATOR')")
    public String groups(@PathVariable("id") Long id,
                         @RequestParam(value = "groupIds", required = false) List<Long> groupIds,
                         @AuthenticationPrincipal ItsmUserPrincipal actor,
                         RedirectAttributes ra) {
        try {
            employeeSetupService.updateGroups(id, groupIds, actor);
            ra.addFlashAttribute("message", "Group membership saved.");
        } catch (ItsmException ex) {
            ra.addFlashAttribute("errorMessage", ex.getMessage());
        }
        return "redirect:/admin/users/" + id;
    }

    @GetMapping("/change-requests")
    @PreAuthorize("hasAuthority('ADMIN_MASTERDATA_APPROVE')")
    public String queue(Model model) {
        model.addAttribute("pending", adminUserService.pending());
        model.addAttribute("history", adminUserService.allChanges());
        model.addAttribute("nav", "adminChangeQueue");
        model.addAttribute("pageTitle", "Config approvals");
        return "admin/change-queue";
    }

    @PostMapping("/change-requests/{id}/approve")
    @PreAuthorize("hasAuthority('ADMIN_MASTERDATA_APPROVE')")
    public String approve(@PathVariable("id") Long id,
                          @AuthenticationPrincipal ItsmUserPrincipal checker,
                          RedirectAttributes ra) {
        try {
            adminUserService.approve(id, checker);
            ra.addFlashAttribute("message", "Change request #" + id + " approved and applied.");
        } catch (ItsmException ex) {
            ra.addFlashAttribute("errorMessage", ex.getMessage());
        }
        return "redirect:/admin/change-requests";
    }

    @PostMapping("/change-requests/{id}/reject")
    @PreAuthorize("hasAuthority('ADMIN_MASTERDATA_APPROVE')")
    public String reject(@PathVariable("id") Long id,
                         @RequestParam("reason") String reason,
                         @AuthenticationPrincipal ItsmUserPrincipal checker,
                         RedirectAttributes ra) {
        try {
            adminUserService.reject(id, reason, checker);
            ra.addFlashAttribute("message", "Change request #" + id + " rejected.");
        } catch (ItsmException ex) {
            ra.addFlashAttribute("errorMessage", ex.getMessage());
        }
        return "redirect:/admin/change-requests";
    }
}
