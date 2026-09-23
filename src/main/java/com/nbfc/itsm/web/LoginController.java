package com.nbfc.itsm.web;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

@Controller
public class LoginController {

    @GetMapping("/login")
    public String login(@RequestParam(value = "error", required = false) String error,
                        @RequestParam(value = "logout", required = false) String logout,
                        @RequestParam(value = "expired", required = false) String expired,
                        Model model) {
        if ("denied".equals(error)) {
            model.addAttribute("loginDenied", Boolean.TRUE);
        } else if (error != null) {
            model.addAttribute("loginError", Boolean.TRUE);
        }
        if (logout != null) {
            model.addAttribute("loggedOut", Boolean.TRUE);
        }
        if (expired != null) {
            model.addAttribute("sessionExpired", Boolean.TRUE);
        }
        return "login";
    }
}
