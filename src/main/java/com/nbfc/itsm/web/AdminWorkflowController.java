package com.nbfc.itsm.web;

import com.nbfc.itsm.admin.WorkflowDesignService;
import com.nbfc.itsm.admin.WorkflowDesignService.StageForm;
import com.nbfc.itsm.domain.WorkflowDefinition;
import com.nbfc.itsm.domain.WorkflowStage;
import com.nbfc.itsm.exception.ItsmException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.List;

/**
 * Admin &gt; Workflow Config: rules (which workflow a ticket follows) and the workflow designer
 * (stages). Anyone with ADMIN_MASTERDATA_PROPOSE can look; changes are System Administrator only
 * (enforced in {@link WorkflowDesignService}).
 */
@Controller
@RequestMapping("/admin/workflow")
@PreAuthorize("hasAuthority('ADMIN_MASTERDATA_PROPOSE')")
public class AdminWorkflowController {

    private final WorkflowDesignService design;

    public AdminWorkflowController(WorkflowDesignService design) {
        this.design = design;
    }

    @GetMapping
    public String list(@RequestParam(value = "retired", defaultValue = "false") boolean retired, Model model) {
        model.addAttribute("nav", "adminWorkflow");
        model.addAttribute("pageTitle", "Workflow configuration");
        model.addAttribute("workflows", design.workflows(retired));
        model.addAttribute("showRetired", retired);
        model.addAttribute("rules", design.rules());
        model.addAttribute("activeDefinitions", design.activeDefinitions());
        model.addAttribute("activeRuleCount", design.activeRuleCount());
        return "admin/workflow";
    }

    @GetMapping("/{id}")
    public String detail(@PathVariable("id") Long id, Model model) {
        WorkflowDefinition d = design.definition(id);
        List<WorkflowStage> stages = design.stages(id);
        model.addAttribute("nav", "adminWorkflow");
        model.addAttribute("pageTitle", d.getName());
        model.addAttribute("definition", d);
        model.addAttribute("stages", stages);
        model.addAttribute("flow", WorkflowDesignService.flowText(stages));
        model.addAttribute("isDraft", WorkflowDesignService.DRAFT.equals(d.getStatusCode()));
        model.addAttribute("draft", design.draftOf(d));
        model.addAttribute("problems", design.problems(id));
        model.addAttribute("stageTypes", WorkflowDesignService.STAGE_TYPES);
        model.addAttribute("strategies", WorkflowDesignService.STRATEGIES);
        model.addAttribute("typesByStrategy", WorkflowDesignService.TYPES_BY_STRATEGY);
        model.addAttribute("roles", design.roles());
        model.addAttribute("groups", design.groups());
        return "admin/workflow-detail";
    }

    // ------------------------------------------------------------------ workflows

    @PostMapping("/{id}/draft")
    public String startDraft(@PathVariable("id") Long id, RedirectAttributes ra) {
        try {
            WorkflowDefinition draft = design.startDraft(id);
            ra.addFlashAttribute("message", "Draft v" + draft.getVersionNo() + " is open. Tickets keep using the active "
                    + "version until you publish.");
            return "redirect:/admin/workflow/" + draft.getWorkflowDefinitionId();
        } catch (ItsmException | AccessDeniedException ex) {
            ra.addFlashAttribute("errorMessage", message(ex));
            return "redirect:/admin/workflow/" + id;
        }
    }

    @PostMapping("/new")
    public String create(@RequestParam("code") String code, @RequestParam("name") String name,
                         @RequestParam(value = "description", required = false) String description,
                         @RequestParam(value = "copyFromId", required = false) Long copyFromId,
                         RedirectAttributes ra) {
        try {
            WorkflowDefinition d = design.createWorkflow(code, name, description, copyFromId);
            ra.addFlashAttribute("message", "Workflow " + d.getCode() + " created as a draft. Add its stages, then publish.");
            return "redirect:/admin/workflow/" + d.getWorkflowDefinitionId();
        } catch (ItsmException | AccessDeniedException ex) {
            ra.addFlashAttribute("errorMessage", message(ex));
            return "redirect:/admin/workflow";
        }
    }

    @PostMapping("/{id}/details")
    public String details(@PathVariable("id") Long id, @RequestParam("name") String name,
                          @RequestParam(value = "description", required = false) String description,
                          RedirectAttributes ra) {
        return run(id, ra, "Name and description saved.", () -> design.updateDetails(id, name, description));
    }

    @PostMapping("/{id}/publish")
    public String publish(@PathVariable("id") Long id, RedirectAttributes ra) {
        try {
            WorkflowDefinition d = design.publish(id);
            ra.addFlashAttribute("message", d.getCode() + " v" + d.getVersionNo() + " is now active; its rules use it for "
                    + "new tickets. Tickets already raised keep their old version.");
        } catch (ItsmException | AccessDeniedException ex) {
            ra.addFlashAttribute("errorMessage", message(ex));
        }
        return "redirect:/admin/workflow/" + id;
    }

    @PostMapping("/{id}/discard")
    public String discard(@PathVariable("id") Long id, RedirectAttributes ra) {
        try {
            WorkflowDefinition active = design.discard(id);
            ra.addFlashAttribute("message", "Draft deleted.");
            return active == null ? "redirect:/admin/workflow" : "redirect:/admin/workflow/" + active.getWorkflowDefinitionId();
        } catch (ItsmException | AccessDeniedException ex) {
            ra.addFlashAttribute("errorMessage", message(ex));
            return "redirect:/admin/workflow/" + id;
        }
    }

    // ------------------------------------------------------------------ stages

    @PostMapping("/{id}/stages")
    public String addStage(@PathVariable("id") Long id, @ModelAttribute StageForm form, RedirectAttributes ra) {
        return run(id, ra, "Stage added before 'Closed'. Use the arrows to move it.", () -> design.addStage(id, form));
    }

    @PostMapping("/{id}/stages/{stageId}")
    public String updateStage(@PathVariable("id") Long id, @PathVariable("stageId") Long stageId,
                              @ModelAttribute StageForm form, RedirectAttributes ra) {
        return run(id, ra, "Stage saved.", () -> design.updateStage(id, stageId, form));
    }

    @PostMapping("/{id}/stages/{stageId}/delete")
    public String deleteStage(@PathVariable("id") Long id, @PathVariable("stageId") Long stageId, RedirectAttributes ra) {
        return run(id, ra, "Stage removed.", () -> design.deleteStage(id, stageId));
    }

    @PostMapping("/{id}/stages/{stageId}/move")
    public String moveStage(@PathVariable("id") Long id, @PathVariable("stageId") Long stageId,
                            @RequestParam("dir") String dir, RedirectAttributes ra) {
        return run(id, ra, null, () -> design.moveStage(id, stageId, "up".equals(dir)));
    }

    // ------------------------------------------------------------------ rules

    @PostMapping("/rules")
    public String createRule(@RequestParam("name") String name,
                             @RequestParam(value = "priority", required = false) Integer priority,
                             @RequestParam("statusCode") String statusCode,
                             @RequestParam(value = "conditionJson", required = false) String conditionJson,
                             @RequestParam(value = "workflowDefinitionId", required = false) Long definitionId,
                             RedirectAttributes ra) {
        try {
            design.saveRule(null, name, priority, statusCode, conditionJson, definitionId);
            ra.addFlashAttribute("message", "Rule '" + name.trim() + "' added. It applies to the next submitted request.");
        } catch (ItsmException | AccessDeniedException ex) {
            ra.addFlashAttribute("errorMessage", message(ex));
        }
        return "redirect:/admin/workflow#rules";
    }

    @PostMapping("/rules/{ruleId}")
    public String updateRule(@PathVariable("ruleId") Long ruleId, @RequestParam("name") String name,
                             @RequestParam(value = "priority", required = false) Integer priority,
                             @RequestParam("statusCode") String statusCode,
                             @RequestParam(value = "conditionJson", required = false) String conditionJson,
                             @RequestParam(value = "workflowDefinitionId", required = false) Long definitionId,
                             RedirectAttributes ra) {
        try {
            design.saveRule(ruleId, name, priority, statusCode, conditionJson, definitionId);
            ra.addFlashAttribute("message", "Rule '" + name.trim() + "' saved. The next submitted request uses it.");
        } catch (ItsmException | AccessDeniedException ex) {
            ra.addFlashAttribute("errorMessage", message(ex));
        }
        return "redirect:/admin/workflow#rules";
    }

    @PostMapping("/rules/{ruleId}/delete")
    public String deleteRule(@PathVariable("ruleId") Long ruleId, RedirectAttributes ra) {
        try {
            design.deleteRule(ruleId);
            ra.addFlashAttribute("message", "Rule deleted.");
        } catch (ItsmException | AccessDeniedException ex) {
            ra.addFlashAttribute("errorMessage", message(ex));
        }
        return "redirect:/admin/workflow#rules";
    }

    // ------------------------------------------------------------------ helpers

    private String run(Long id, RedirectAttributes ra, String ok, Runnable action) {
        try {
            action.run();
            if (ok != null) {
                ra.addFlashAttribute("message", ok);
            }
        } catch (ItsmException | AccessDeniedException ex) {
            ra.addFlashAttribute("errorMessage", message(ex));
        }
        return "redirect:/admin/workflow/" + id;
    }

    private static String message(RuntimeException ex) {
        return ex instanceof AccessDeniedException
                ? "Only the System Administrator can change workflows." : ex.getMessage();
    }
}
