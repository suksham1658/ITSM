package com.nbfc.itsm.web;

import com.nbfc.itsm.audit.AuditTrailService;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.time.LocalDate;

/** Audit Trail: read-only, filterable, newest first (AUDIT_VIEW). */
@Controller
@PreAuthorize("hasAuthority('AUDIT_VIEW')")
public class AuditController {

    private final AuditTrailService auditTrailService;

    public AuditController(AuditTrailService auditTrailService) {
        this.auditTrailService = auditTrailService;
    }

    @GetMapping("/audit")
    public String audit(@RequestParam(value = "user", required = false) String user,
                        @RequestParam(value = "ticket", required = false) String ticket,
                        @RequestParam(value = "module", required = false) String module,
                        @RequestParam(value = "action", required = false) String action,
                        @RequestParam(value = "result", required = false) String result,
                        @RequestParam(value = "text", required = false) String text,
                        @RequestParam(value = "from", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
                        @RequestParam(value = "to", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
                        @RequestParam(value = "page", defaultValue = "0") int page,
                        Model model) {
        AuditTrailService.Filter f = new AuditTrailService.Filter();
        f.setUser(SearchText.clean(user));
        f.setTicket(SearchText.clean(ticket));
        f.setModule(module);
        f.setAction(action);
        f.setResult(result);
        f.setText(SearchText.clean(text));
        f.setFrom(from);
        f.setTo(to);
        model.addAttribute("nav", "auditTrail");
        model.addAttribute("pageTitle", "Audit Trail");
        model.addAttribute("f", f);
        model.addAttribute("result", auditTrailService.search(f, page));
        model.addAttribute("modules", auditTrailService.modules());
        model.addAttribute("actions", auditTrailService.actions());
        return "audit";
    }
}
