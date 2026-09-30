package com.nbfc.itsm.web;

import com.nbfc.itsm.domain.Category;
import com.nbfc.itsm.domain.CategoryRepository;
import com.nbfc.itsm.domain.SubCategory;
import com.nbfc.itsm.domain.SubCategoryRepository;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;

import java.util.List;

/** Categories, SLA and configuration pages. Workflow Config lives in {@link AdminWorkflowController}. */
@Controller
@RequestMapping("/admin")
public class AdminCatalogController {

    private final CategoryRepository categoryRepository;
    private final SubCategoryRepository subCategoryRepository;

    public AdminCatalogController(CategoryRepository categoryRepository,
                                  SubCategoryRepository subCategoryRepository) {
        this.categoryRepository = categoryRepository;
        this.subCategoryRepository = subCategoryRepository;
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
        return "admin/categories";
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
        model.addAttribute("pageTitle", "Configuration");
        model.addAttribute("emptyMessage",
                "");
        return "admin/stub";
    }
}
