package com.nbfc.itsm.web;

import com.nbfc.itsm.audit.AuditRecorder;
import com.nbfc.itsm.license.LicenseException;
import com.nbfc.itsm.license.LicenseService;
import com.nbfc.itsm.security.ItsmUserPrincipal;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

/** License status, renewal upload (System Administrator only) and the "unavailable" holding page. */
@Controller
public class LicenseController {

    private final LicenseService licenseService;
    private final AuditRecorder auditRecorder;

    public LicenseController(LicenseService licenseService, AuditRecorder auditRecorder) {
        this.licenseService = licenseService;
        this.auditRecorder = auditRecorder;
    }

    @GetMapping("/admin/license")
    @PreAuthorize("hasAuthority('ROLE_SYSTEM_ADMINISTRATOR')")
    public String page(Model model) {
        model.addAttribute("nav", "adminLicense");
        model.addAttribute("pageTitle", "License");
        model.addAttribute("license", licenseService.refresh());
        return "admin/license";
    }

    @PostMapping("/admin/license/upload")
    @PreAuthorize("hasAuthority('ROLE_SYSTEM_ADMINISTRATOR')")
    public String upload(@RequestParam("file") MultipartFile file,
                         @AuthenticationPrincipal ItsmUserPrincipal actor, RedirectAttributes ra) {
        if (file == null || file.isEmpty()) {
            ra.addFlashAttribute("errorMessage", "Choose a license file to upload.");
            return "redirect:/admin/license";
        }
        try {
            licenseService.install(file.getBytes());
            auditRecorder.record("ADMIN", "LICENSE_INSTALL", "License installed by "
                    + (actor == null ? "?" : actor.getEmployeeNo()), "SUCCESS");
            ra.addFlashAttribute("message", "License installed. The portal is licensed again for everyone.");
        } catch (LicenseException ex) {
            ra.addFlashAttribute("errorMessage", "That file is not a valid license: " + ex.getMessage());
        } catch (Exception ex) {
            ra.addFlashAttribute("errorMessage", "Could not read the uploaded file.");
        }
        return "redirect:/admin/license";
    }

    /** Shown to non-administrators once the grace period is over. Reachable without a valid license. */
    @GetMapping("/license-unavailable")
    public String unavailable(Model model) {
        model.addAttribute("pageTitle", "Temporarily unavailable");
        return "license-unavailable";
    }
}
