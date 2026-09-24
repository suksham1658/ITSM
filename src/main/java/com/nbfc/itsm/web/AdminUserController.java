package com.nbfc.itsm.web;

import com.nbfc.itsm.admin.AdminUserService;
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
import java.util.Set;

@Controller
@RequestMapping("/admin")
public class AdminUserController {

    private final AdminUserService adminUserService;

    public AdminUserController(AdminUserService adminUserService) {
        this.adminUserService = adminUserService;
    }

    @GetMapping("/users")
    @PreAuthorize("hasAuthority('ADMIN_USER_MANAGE')")
    public String users(@RequestParam(value = "q", required = false) String q, Model model) {
        model.addAttribute("employees", adminUserService.list(q));
        model.addAttribute("q", q);
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
        model.addAttribute("employee", employee);
        model.addAttribute("allRoles", adminUserService.roles());
        model.addAttribute("assignedRoleIds", assignedRoleIds);
        model.addAttribute("nav", "adminUsers");
        model.addAttribute("pageTitle", employee.getDisplayName());
        return "admin/user-detail";
    }

    @PostMapping("/users/{id}/propose-active")
    @PreAuthorize("hasAuthority('ADMIN_MASTERDATA_PROPOSE')")
    public String proposeActive(@PathVariable("id") Long id,
                                @RequestParam("active") boolean active,
                                @AuthenticationPrincipal ItsmUserPrincipal maker,
                                RedirectAttributes ra) {
        adminUserService.proposePortalActive(id, active, maker);
        ra.addFlashAttribute("message", "Submitted for checker approval.");
        return "redirect:/admin/users/" + id;
    }

    @PostMapping("/users/{id}/propose-role")
    @PreAuthorize("hasAuthority('ADMIN_MASTERDATA_PROPOSE')")
    public String proposeRole(@PathVariable("id") Long id,
                              @RequestParam("roleId") Long roleId,
                              @RequestParam("assign") boolean assign,
                              @AuthenticationPrincipal ItsmUserPrincipal maker,
                              RedirectAttributes ra) {
        adminUserService.proposeRole(id, roleId, assign, maker);
        ra.addFlashAttribute("message", "Role change submitted for checker approval.");
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
