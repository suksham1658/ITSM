package com.nbfc.itsm.web;

import com.nbfc.itsm.admin.AdAccountUnlockService;
import com.nbfc.itsm.exception.ItsmException;
import com.nbfc.itsm.identity.AdAccountStatus;
import com.nbfc.itsm.security.ItsmUserPrincipal;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import org.springframework.web.util.UriComponentsBuilder;

import javax.servlet.http.HttpServletRequest;

/** AD Account Unlock: IT Service Desk and System Administrator look up a directory account and clear its lockout. */
@Controller
@PreAuthorize("hasAuthority('AD_ACCOUNT_UNLOCK')")
public class AdAccountController {

    private final AdAccountUnlockService unlockService;

    public AdAccountController(AdAccountUnlockService unlockService) {
        this.unlockService = unlockService;
    }

    @GetMapping("/ad-accounts")
    public String search(@RequestParam(value = "q", required = false) String q, Model model) {
        model.addAttribute("nav", "adUnlock");
        model.addAttribute("pageTitle", "AD Account Unlock");
        model.addAttribute("q", q);
        if (StringUtils.hasText(q)) {
            try {
                model.addAttribute("accounts", unlockService.search(q));
                model.addAttribute("searchLimit", AdAccountUnlockService.SEARCH_LIMIT);
            } catch (ItsmException ex) {
                model.addAttribute("errorMessage", ex.getMessage());
            }
        }
        return "ad-accounts/list";
    }

    @GetMapping("/ad-accounts/account")
    public String account(@RequestParam("id") String id, @RequestParam(value = "q", required = false) String q,
                          Model model, HttpServletRequest request, RedirectAttributes ra) {
        AdAccountStatus account;
        try {
            account = unlockService.status(id);
        } catch (ItsmException ex) {
            ra.addFlashAttribute("errorMessage", ex.getMessage());
            return "redirect:" + listUrl(q);
        }
        model.addAttribute("nav", "adUnlock");
        model.addAttribute("pageTitle", "AD Account Unlock");
        model.addAttribute("account", account);
        model.addAttribute("q", q);
        BackLinks.addTo(model, request, listUrl(q), "Back to AD Account Unlock");
        return "ad-accounts/account";
    }

    @PostMapping("/ad-accounts/unlock")
    public String unlock(@AuthenticationPrincipal ItsmUserPrincipal user,
                         @RequestParam("id") String id,
                         @RequestParam(value = "q", required = false) String q,
                         RedirectAttributes ra) {
        try {
            AdAccountStatus after = unlockService.unlock(user, id);
            ra.addFlashAttribute("message", after.getDisplayName() + " (" + after.getSamAccountName()
                    + ") is unlocked. The user can sign in now; other domain controllers pick it up within a few minutes.");
        } catch (ItsmException ex) {
            ra.addFlashAttribute("errorMessage", ex.getMessage());
        }
        return "redirect:" + UriComponentsBuilder.fromPath("/ad-accounts/account")
                .queryParam("id", id).queryParamIfPresent("q", java.util.Optional.ofNullable(q))
                .encode().build().toUriString();
    }

    private static String listUrl(String q) {
        return StringUtils.hasText(q)
                ? UriComponentsBuilder.fromPath("/ad-accounts").queryParam("q", q).encode().build().toUriString()
                : "/ad-accounts";
    }
}
