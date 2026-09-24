package com.nbfc.itsm.web;

import com.nbfc.itsm.audit.AuditRecorder;
import com.nbfc.itsm.identity.PortalUserService;
import com.nbfc.itsm.security.ItsmUserPrincipal;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import javax.servlet.http.HttpServletRequest;

/**
 * "View as" role switcher. The list offered to the user is their own assigned roles only, and the
 * choice is re-validated against {@code employee_role} here, so a crafted request cannot select
 * a role the employee does not hold.
 */
@Controller
public class RoleSwitchController {

    private final PortalUserService portalUserService;
    private final AuditRecorder auditRecorder;

    public RoleSwitchController(PortalUserService portalUserService, AuditRecorder auditRecorder) {
        this.portalUserService = portalUserService;
        this.auditRecorder = auditRecorder;
    }

    @PostMapping("/session/active-role")
    public String switchRole(@RequestParam(value = "role", required = false) String role,
                             @AuthenticationPrincipal ItsmUserPrincipal current,
                             HttpServletRequest request,
                             RedirectAttributes ra) {
        ItsmUserPrincipal next = portalUserService.switchActiveRole(current.getEmployeeId(), role);

        Authentication previous = SecurityContextHolder.getContext().getAuthentication();
        UsernamePasswordAuthenticationToken auth =
                new UsernamePasswordAuthenticationToken(next, null, next.getAuthorities());
        auth.setDetails(previous == null ? null : previous.getDetails());
        SecurityContext context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(auth);
        // SecurityContextPersistenceFilter stores the new context in the session on redirect.
        SecurityContextHolder.setContext(context);

        auditRecorder.record("AUTH", "ROLE_SWITCH",
                next.getUsername() + " -> " + (next.getActiveRoleCode() == null ? "ALL" : next.getActiveRoleCode()),
                "SUCCESS", request);
        ra.addFlashAttribute("message", "Viewing the portal as " + next.getPrimaryRoleLabel() + ".");
        return "redirect:/";
    }
}
