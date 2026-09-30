package com.nbfc.itsm.web;

import com.nbfc.itsm.admin.SystemSettingsService;
import com.nbfc.itsm.domain.Category;
import com.nbfc.itsm.domain.CategoryRepository;
import com.nbfc.itsm.domain.SubCategory;
import com.nbfc.itsm.domain.SubCategoryRepository;
import com.nbfc.itsm.exception.ItsmException;
import com.nbfc.itsm.security.ItsmUserPrincipal;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import org.springframework.web.bind.annotation.RequestMapping;

import java.util.List;

/** Categories, SLA and System Configuration pages. Workflow Config lives in {@link AdminWorkflowController}. */
@Controller
@RequestMapping("/admin")
public class AdminCatalogController {

    private final CategoryRepository categoryRepository;
    private final SubCategoryRepository subCategoryRepository;
    private final SystemSettingsService systemSettings;
    private final com.nbfc.itsm.admin.CategoryImplementorService categoryImplementors;

    public AdminCatalogController(CategoryRepository categoryRepository,
                                  SubCategoryRepository subCategoryRepository,
                                  SystemSettingsService systemSettings,
                                  com.nbfc.itsm.admin.CategoryImplementorService categoryImplementors) {
        this.categoryImplementors = categoryImplementors;
        this.categoryRepository = categoryRepository;
        this.subCategoryRepository = subCategoryRepository;
        this.systemSettings = systemSettings;
    }

    @GetMapping("/categories")
    @PreAuthorize("hasAuthority('ADMIN_MASTERDATA_PROPOSE')")
    @Transactional(readOnly = true)
    public String categories(Model model) {
        List<Category> cats = categoryRepository.findByActiveTrueOrderBySortOrderAsc();
        List<SubCategory> subs = subCategoryRepository.findByActiveTrueOrderBySortOrderAsc();
        for (SubCategory s : subs) {
            s.getCategory().getName();
        }
        model.addAttribute("nav", "adminCategories");
        model.addAttribute("pageTitle", "Categories");
        model.addAttribute("categories", cats);
        model.addAttribute("subCategories", subs);
        List<com.nbfc.itsm.domain.Employee> candidates = categoryImplementors.candidates();
        java.util.Map<Long, String> names = new java.util.HashMap<Long, String>();
        for (com.nbfc.itsm.domain.Employee e : candidates) {
            names.put(e.getEmployeeId(), e.getDisplayName());
        }
        model.addAttribute("implementorCandidates", candidates);
        model.addAttribute("implementorNames", names);
        model.addAttribute("categoryImplementors", categoryImplementors.assignments(cats));
        return "admin/categories";
    }

    /** Implementors who handle a category (the IT Service Desk chooses from them). System Administrator. */
    @PostMapping("/categories/{id}/implementors")
    @PreAuthorize("hasAuthority('ROLE_SYSTEM_ADMINISTRATOR')")
    public String saveImplementors(@org.springframework.web.bind.annotation.PathVariable("id") Long id,
                                   @RequestParam(value = "employeeIds", required = false) List<Long> employeeIds,
                                   @AuthenticationPrincipal ItsmUserPrincipal actor, RedirectAttributes ra) {
        try {
            int n = categoryImplementors.save(id, employeeIds, actor);
            ra.addFlashAttribute("message", n == 0
                    ? "No implementors set: the service desk will choose from the whole IT Implementors group."
                    : n + " implementor(s) saved for this category.");
        } catch (ItsmException ex) {
            ra.addFlashAttribute("errorMessage", ex.getMessage());
        }
        return "redirect:/admin/categories";
    }

    @GetMapping("/sla")
    @PreAuthorize("hasAuthority('ADMIN_MASTERDATA_PROPOSE')")
    public String sla(Model model) {
        model.addAttribute("nav", "adminSLA");
        model.addAttribute("pageTitle", "SLA configuration");
        model.addAttribute("emptyMessage",
                "To see live SLA clocks for tickets, open SLA Monitoring.");
        return "admin/stub";
    }

    @GetMapping("/config")
    @PreAuthorize("hasAuthority('ADMIN_MASTERDATA_PROPOSE')")
    public String config(Model model) {
        model.addAttribute("nav", "adminConfig");
        model.addAttribute("pageTitle", "System Configuration");
        model.addAttribute("categories", SystemSettingsService.CATEGORIES);
        model.addAttribute("rows", systemSettings.rows());
        model.addAttribute("securityFacts", systemSettings.securityFacts());
        return "admin/config";
    }

    /** Proposes a setting change; a different administrator approves it in Config approvals. */
    @PostMapping("/config")
    @PreAuthorize("hasAuthority('ADMIN_MASTERDATA_PROPOSE')")
    public String proposeSetting(@RequestParam("key") String key, @RequestParam(value = "value", required = false) String value,
                                 @AuthenticationPrincipal ItsmUserPrincipal maker, RedirectAttributes ra) {
        try {
            systemSettings.propose(key, value == null ? "false" : value, maker);
            ra.addFlashAttribute("message", "Change to " + key + " submitted. It takes effect once a different administrator "
                    + "approves it in Config approvals.");
        } catch (ItsmException ex) {
            ra.addFlashAttribute("errorMessage", ex.getMessage());
        }
        return "redirect:/admin/config";
    }
}
