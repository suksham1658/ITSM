package com.nbfc.itsm.web;

import com.nbfc.itsm.domain.Category;
import com.nbfc.itsm.domain.CategoryRepository;
import com.nbfc.itsm.domain.SubCategory;
import com.nbfc.itsm.domain.SubCategoryRepository;
import com.nbfc.itsm.domain.WorkflowDefinition;
import com.nbfc.itsm.domain.WorkflowDefinitionRepository;
import com.nbfc.itsm.domain.WorkflowRule;
import com.nbfc.itsm.domain.WorkflowRuleRepository;
import com.nbfc.itsm.domain.WorkflowStage;
import com.nbfc.itsm.domain.WorkflowStageRepository;
import com.nbfc.itsm.exception.ItsmException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.List;

@Controller
@RequestMapping("/admin")
public class AdminCatalogController {

    private final WorkflowDefinitionRepository definitionRepository;
    private final WorkflowStageRepository stageRepository;
    private final WorkflowRuleRepository ruleRepository;
    private final CategoryRepository categoryRepository;
    private final SubCategoryRepository subCategoryRepository;

    public AdminCatalogController(WorkflowDefinitionRepository definitionRepository,
                                  WorkflowStageRepository stageRepository,
                                  WorkflowRuleRepository ruleRepository,
                                  CategoryRepository categoryRepository,
                                  SubCategoryRepository subCategoryRepository) {
        this.definitionRepository = definitionRepository;
        this.stageRepository = stageRepository;
        this.ruleRepository = ruleRepository;
        this.categoryRepository = categoryRepository;
        this.subCategoryRepository = subCategoryRepository;
    }

    @GetMapping("/roles")
    @PreAuthorize("hasAuthority('ADMIN_USER_MANAGE')")
    public String roles(Model model) {
        model.addAttribute("nav", "adminRoles");
        model.addAttribute("pageTitle", "Roles");
        model.addAttribute("emptyMessage",
                "Roles are seeded (Employee through System Administrator). Assignment is on Admin → Users. A visual role designer is not in this phase.");
        return "admin/stub";
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
                "SLA matrix is seeded (Critical/High/Medium/Low). Editing still goes through maker-checker; this page is a view stub. Live clocks are under SLA Monitoring.");
        return "admin/stub";
    }

    @GetMapping("/workflow")
    @PreAuthorize("hasAuthority('ADMIN_MASTERDATA_PROPOSE')")
    @Transactional(readOnly = true)
    public String workflow(Model model) {
        List<WorkflowDefinition> defs = definitionRepository.findAllByOrderByCodeAscVersionNoAsc();
        List<WorkflowRule> rules = ruleRepository.findAll();
        for (WorkflowRule r : rules) {
            r.getWorkflowDefinition().getCode();
        }
        model.addAttribute("nav", "adminWorkflow");
        model.addAttribute("pageTitle", "Workflow configuration");
        model.addAttribute("definitions", defs);
        model.addAttribute("rules", rules);
        model.addAttribute("designerNote",
                "There is no drag-and-drop designer in this phase. Templates and the rule matrix below are the live engine data. INCIDENT_DIRECT_IMPL stays Inactive with no rule.");
        return "admin/workflow";
    }

    @GetMapping("/workflow/{id}")
    @PreAuthorize("hasAuthority('ADMIN_MASTERDATA_PROPOSE')")
    @Transactional(readOnly = true)
    public String workflowDetail(@PathVariable("id") Long id, Model model) {
        WorkflowDefinition def = definitionRepository.findById(id)
                .orElseThrow(() -> new ItsmException("WF_NOT_FOUND", "Workflow not found."));
        List<WorkflowStage> stages = stageRepository.findByWorkflowDefinitionOrderByStageOrderAsc(def);
        for (WorkflowStage s : stages) {
            if (s.getRole() != null) {
                s.getRole().getCode();
            }
            if (s.getAssignmentGroup() != null) {
                s.getAssignmentGroup().getCode();
            }
        }
        model.addAttribute("nav", "adminWorkflow");
        model.addAttribute("pageTitle", def.getName());
        model.addAttribute("definition", def);
        model.addAttribute("stages", stages);
        return "admin/workflow-detail";
    }

    @PostMapping("/workflow/rules/{id}")
    @PreAuthorize("hasAuthority('ADMIN_MASTERDATA_PROPOSE')")
    @Transactional
    public String updateRule(@PathVariable("id") Long id,
                             @RequestParam("statusCode") String statusCode,
                             @RequestParam("conditionJson") String conditionJson,
                             @RequestParam("name") String name,
                             RedirectAttributes ra) {
        WorkflowRule rule = ruleRepository.findById(id)
                .orElseThrow(() -> new ItsmException("RULE_NOT_FOUND", "Rule not found."));
        if (!"Active".equals(statusCode) && !"Inactive".equals(statusCode)) {
            ra.addFlashAttribute("errorMessage", "Status must be Active or Inactive.");
            return "redirect:/admin/workflow";
        }
        rule.setStatusCode(statusCode);
        rule.setConditionJson(conditionJson == null ? "{}" : conditionJson.trim());
        rule.setName(name);
        ruleRepository.save(rule);
        ra.addFlashAttribute("message", "Rule '" + rule.getName() + "' saved. The matcher reads this on the next submit.");
        return "redirect:/admin/workflow";
    }

    @GetMapping("/config")
    @PreAuthorize("hasAuthority('ADMIN_MASTERDATA_PROPOSE')")
    public String config(Model model) {
        model.addAttribute("nav", "adminConfig");
        model.addAttribute("pageTitle", "Configuration");
        model.addAttribute("emptyMessage",
                "Non-secret system settings (remarks minimum, hop cap) are seeded. Database and LDAP bind settings stay in environment variables.");
        return "admin/stub";
    }
}
