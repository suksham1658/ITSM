package com.nbfc.itsm.web;

import com.nbfc.itsm.asset.AssetForm;
import com.nbfc.itsm.asset.AssetService;
import com.nbfc.itsm.exception.ItsmException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import javax.validation.Valid;

/** Asset Management: list/search + add/edit/delete IT assets (ASSET_MANAGE). */
@Controller
@PreAuthorize("hasAuthority('ASSET_MANAGE')")
public class AssetController {

    private final AssetService assetService;

    public AssetController(AssetService assetService) {
        this.assetService = assetService;
    }

    @GetMapping("/assets")
    public String list(@RequestParam(value = "text", required = false) String text,
                       @RequestParam(value = "type", required = false) String type,
                       @RequestParam(value = "status", required = false) String status,
                       @RequestParam(value = "page", defaultValue = "0") int page,
                       Model model) {
        AssetService.Filter f = new AssetService.Filter();
        f.setText(SearchText.clean(text));
        f.setType(type);
        f.setStatus(status);
        model.addAttribute("nav", "assets");
        model.addAttribute("pageTitle", "Asset Management");
        model.addAttribute("f", f);
        model.addAttribute("assets", assetService.search(f, page));
        model.addAttribute("types", assetService.types());
        model.addAttribute("statuses", assetService.statuses());
        return "assets";
    }

    @GetMapping("/assets/new")
    public String newForm(Model model) {
        if (!model.containsAttribute("form")) {
            AssetForm form = new AssetForm();
            form.setStatusCode("IN_STOCK");
            model.addAttribute("form", form);
        }
        return formPage(model, false);
    }

    @PostMapping("/assets")
    public String create(@Valid @ModelAttribute("form") AssetForm form, BindingResult binding,
                         Model model, RedirectAttributes ra) {
        if (binding.hasErrors()) {
            return formPage(model, false);
        }
        try {
            String tag = assetService.create(form).getAssetTag();
            ra.addFlashAttribute("message", "Asset \"" + tag + "\" added.");
            return "redirect:/assets";
        } catch (ItsmException ex) {
            model.addAttribute("errorMessage", ex.getMessage());
            return formPage(model, false);
        }
    }

    @GetMapping("/assets/{id}/edit")
    public String editForm(@PathVariable Long id, Model model) {
        if (!model.containsAttribute("form")) {
            model.addAttribute("form", assetService.toForm(assetService.get(id)));
        }
        return formPage(model, true);
    }

    @PostMapping("/assets/{id}")
    public String update(@PathVariable Long id, @Valid @ModelAttribute("form") AssetForm form,
                         BindingResult binding, Model model, RedirectAttributes ra) {
        if (binding.hasErrors()) {
            form.setAssetId(id);
            return formPage(model, true);
        }
        try {
            String tag = assetService.update(id, form).getAssetTag();
            ra.addFlashAttribute("message", "Asset \"" + tag + "\" updated.");
            return "redirect:/assets";
        } catch (ItsmException ex) {
            form.setAssetId(id);
            model.addAttribute("errorMessage", ex.getMessage());
            return formPage(model, true);
        }
    }

    @PostMapping("/assets/{id}/delete")
    public String delete(@PathVariable Long id, RedirectAttributes ra) {
        try {
            assetService.delete(id);
            ra.addFlashAttribute("message", "Asset deleted.");
        } catch (ItsmException ex) {
            ra.addFlashAttribute("errorMessage", ex.getMessage());
        }
        return "redirect:/assets";
    }

    private String formPage(Model model, boolean editing) {
        model.addAttribute("nav", "assets");
        model.addAttribute("pageTitle", editing ? "Edit asset" : "Add asset");
        model.addAttribute("editing", editing);
        model.addAttribute("employees", assetService.activeEmployees());
        model.addAttribute("departments", assetService.departments());
        model.addAttribute("statuses", assetService.statuses());
        model.addAttribute("types", assetService.types());
        return "asset-form";
    }
}
