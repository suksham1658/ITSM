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
    private final com.nbfc.itsm.admin.CatalogAdminService catalogAdmin;
    private final com.nbfc.itsm.domain.TicketTypeRepository ticketTypeRepository;
    private final com.nbfc.itsm.admin.SlaAdminService slaAdmin;

    public AdminCatalogController(CategoryRepository categoryRepository,
                                  SubCategoryRepository subCategoryRepository,
                                  SystemSettingsService systemSettings,
                                  com.nbfc.itsm.admin.CategoryImplementorService categoryImplementors,
                                  com.nbfc.itsm.admin.CatalogAdminService catalogAdmin,
                                  com.nbfc.itsm.domain.TicketTypeRepository ticketTypeRepository,
                                  com.nbfc.itsm.admin.SlaAdminService slaAdmin) {
        this.slaAdmin = slaAdmin;
        this.categoryImplementors = categoryImplementors;
        this.catalogAdmin = catalogAdmin;
        this.ticketTypeRepository = ticketTypeRepository;
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
        model.addAttribute("ticketTypes", ticketTypeRepository.findByActiveTrueOrderBySortOrderAsc());
        model.addAttribute("lockedTypes", com.nbfc.itsm.admin.CatalogAdminService.LOCKED_TYPES);
        model.addAttribute("lockedCategories", com.nbfc.itsm.admin.CatalogAdminService.LOCKED_CATEGORIES);
        return "admin/categories";
    }

    // ------------------------------------------------------------------ catalog: add / edit / delete

    @PostMapping("/catalog/types")
    @PreAuthorize("hasAuthority('ROLE_SYSTEM_ADMINISTRATOR')")
    public String addType(@RequestParam("name") String name, @AuthenticationPrincipal ItsmUserPrincipal actor,
                          RedirectAttributes ra) {
        return run(ra, "types", () -> "Ticket type \"" + catalogAdmin.addType(name, actor).getName() + "\" added.");
    }

    @PostMapping("/catalog/types/{id}/rename")
    @PreAuthorize("hasAuthority('ROLE_SYSTEM_ADMINISTRATOR')")
    public String renameType(@org.springframework.web.bind.annotation.PathVariable("id") Long id,
                             @RequestParam("name") String name, @AuthenticationPrincipal ItsmUserPrincipal actor,
                             RedirectAttributes ra) {
        return run(ra, "types", () -> renamed("Ticket type", catalogAdmin.renameType(id, name, actor)));
    }

    @PostMapping("/catalog/types/{id}/delete")
    @PreAuthorize("hasAuthority('ROLE_SYSTEM_ADMINISTRATOR')")
    public String deleteType(@org.springframework.web.bind.annotation.PathVariable("id") Long id,
                             @AuthenticationPrincipal ItsmUserPrincipal actor, RedirectAttributes ra) {
        return run(ra, "types", () -> {
            catalogAdmin.deleteType(id, actor);
            return "Ticket type removed from Raise Request (existing tickets keep it).";
        });
    }

    @PostMapping("/catalog/categories")
    @PreAuthorize("hasAuthority('ROLE_SYSTEM_ADMINISTRATOR')")
    public String addCategory(@RequestParam("name") String name, @AuthenticationPrincipal ItsmUserPrincipal actor,
                              RedirectAttributes ra) {
        return run(ra, "categories", () -> "Category \"" + catalogAdmin.addCategory(name, actor).getName()
                + "\" added. Add its sub-categories below.");
    }

    @PostMapping("/catalog/categories/{id}/rename")
    @PreAuthorize("hasAuthority('ROLE_SYSTEM_ADMINISTRATOR')")
    public String renameCategory(@org.springframework.web.bind.annotation.PathVariable("id") Long id,
                                 @RequestParam("name") String name, @AuthenticationPrincipal ItsmUserPrincipal actor,
                                 RedirectAttributes ra) {
        return run(ra, "categories", () -> renamed("Category", catalogAdmin.renameCategory(id, name, actor)));
    }

    @PostMapping("/catalog/categories/{id}/delete")
    @PreAuthorize("hasAuthority('ROLE_SYSTEM_ADMINISTRATOR')")
    public String deleteCategory(@org.springframework.web.bind.annotation.PathVariable("id") Long id,
                                 @AuthenticationPrincipal ItsmUserPrincipal actor, RedirectAttributes ra) {
        return run(ra, "categories", () -> {
            catalogAdmin.deleteCategory(id, actor);
            return "Category and its sub-categories removed from Raise Request (existing tickets keep them).";
        });
    }

    @PostMapping("/catalog/sub-categories")
    @PreAuthorize("hasAuthority('ROLE_SYSTEM_ADMINISTRATOR')")
    public String addSubCategory(@RequestParam("categoryId") Long categoryId, @RequestParam("name") String name,
                                 @AuthenticationPrincipal ItsmUserPrincipal actor, RedirectAttributes ra) {
        return run(ra, "subcategories", () -> "Sub-category \"" + catalogAdmin.addSubCategory(categoryId, name, actor).getName() + "\" added.");
    }

    @PostMapping("/catalog/sub-categories/{id}/rename")
    @PreAuthorize("hasAuthority('ROLE_SYSTEM_ADMINISTRATOR')")
    public String renameSubCategory(@org.springframework.web.bind.annotation.PathVariable("id") Long id,
                                    @RequestParam("name") String name, @AuthenticationPrincipal ItsmUserPrincipal actor,
                                    RedirectAttributes ra) {
        return run(ra, "subcategories", () -> renamed("Sub-category", catalogAdmin.renameSubCategory(id, name, actor)));
    }

    @PostMapping("/catalog/sub-categories/{id}/delete")
    @PreAuthorize("hasAuthority('ROLE_SYSTEM_ADMINISTRATOR')")
    public String deleteSubCategory(@org.springframework.web.bind.annotation.PathVariable("id") Long id,
                                    @AuthenticationPrincipal ItsmUserPrincipal actor, RedirectAttributes ra) {
        return run(ra, "subcategories", () -> {
            catalogAdmin.deleteSubCategory(id, actor);
            return "Sub-category removed from Raise Request (existing tickets keep it).";
        });
    }

    private static String renamed(String what, int rules) {
        return what + " renamed." + (rules > 0 ? " " + rules + " workflow rule(s) updated to the new name." : "");
    }

    private static String run(RedirectAttributes ra, String anchor, java.util.function.Supplier<String> action) {
        try {
            ra.addFlashAttribute("message", action.get());
        } catch (ItsmException ex) {
            ra.addFlashAttribute("errorMessage", ex.getMessage());
        }
        return "redirect:/admin/categories#" + anchor;
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
        model.addAttribute("pageTitle", "SLA Config");
        model.addAttribute("policies", slaAdmin.policies());
        model.addAttribute("calendar", slaAdmin.calendar());
        model.addAttribute("holidays", slaAdmin.holidays());
        model.addAttribute("today", java.time.LocalDate.now(java.time.ZoneId.of("Asia/Kolkata")));
        return "admin/sla";
    }

    @PostMapping("/sla/policies/{id}")
    @PreAuthorize("hasAuthority('ROLE_SYSTEM_ADMINISTRATOR')")
    public String saveSlaPolicy(@org.springframework.web.bind.annotation.PathVariable("id") Long id,
                                @RequestParam(value = "responseMinutes", required = false) Integer responseMinutes,
                                @RequestParam(value = "resolutionMinutes", required = false) Integer resolutionMinutes,
                                @RequestParam(value = "allHours", defaultValue = "false") boolean allHours,
                                @AuthenticationPrincipal ItsmUserPrincipal actor, RedirectAttributes ra) {
        return slaRun(ra, "targets", () -> "SLA for " + slaAdmin.updatePolicy(id, responseMinutes, resolutionMinutes, allHours, actor)
                .getPriorityCode() + " saved. It applies to tickets submitted from now on.");
    }

    @PostMapping("/sla/calendar")
    @PreAuthorize("hasAuthority('ROLE_SYSTEM_ADMINISTRATOR')")
    public String saveSlaCalendar(@RequestParam(value = "workingDays", required = false) List<Integer> workingDays,
                                  @RequestParam(value = "start", required = false) List<String> start,
                                  @RequestParam(value = "end", required = false) List<String> end,
                                  @AuthenticationPrincipal ItsmUserPrincipal actor, RedirectAttributes ra) {
        return slaRun(ra, "hours", () -> {
            slaAdmin.updateCalendar(workingDays, start, end, actor);
            return "Working hours saved.";
        });
    }

    @PostMapping("/sla/holidays")
    @PreAuthorize("hasAuthority('ROLE_SYSTEM_ADMINISTRATOR')")
    public String addSlaHoliday(@RequestParam(value = "date", required = false)
                                @org.springframework.format.annotation.DateTimeFormat(iso = org.springframework.format.annotation.DateTimeFormat.ISO.DATE)
                                        java.time.LocalDate date,
                                @RequestParam(value = "name", required = false) String name,
                                @AuthenticationPrincipal ItsmUserPrincipal actor, RedirectAttributes ra) {
        return slaRun(ra, "holidays", () -> "Holiday " + slaAdmin.addHoliday(date, name, actor).getHolidayDate() + " added.");
    }

    @PostMapping("/sla/holidays/{id}/delete")
    @PreAuthorize("hasAuthority('ROLE_SYSTEM_ADMINISTRATOR')")
    public String deleteSlaHoliday(@org.springframework.web.bind.annotation.PathVariable("id") Long id,
                                   @AuthenticationPrincipal ItsmUserPrincipal actor, RedirectAttributes ra) {
        return slaRun(ra, "holidays", () -> {
            slaAdmin.deleteHoliday(id, actor);
            return "Holiday removed.";
        });
    }

    private static String slaRun(RedirectAttributes ra, String anchor, java.util.function.Supplier<String> action) {
        try {
            ra.addFlashAttribute("message", action.get());
        } catch (ItsmException ex) {
            ra.addFlashAttribute("errorMessage", ex.getMessage());
        }
        return "redirect:/admin/sla#" + anchor;
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
            com.nbfc.itsm.domain.ConfigChangeRequest ccr = systemSettings.propose(key, value == null ? "false" : value, maker);
            ra.addFlashAttribute("message", "Applied".equals(ccr.getStatusCode()) ? key + " saved and in effect now." : "Change to " + key + " submitted. It takes effect once a System Administrator "
                    + "approves it in Config approvals.");
        } catch (ItsmException ex) {
            ra.addFlashAttribute("errorMessage", ex.getMessage());
        }
        return "redirect:/admin/config";
    }
}
