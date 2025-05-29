package dev.oasis.stockify.controller;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;


@Controller
public class LoginController {
    /**
     * Displays the login page
     */
    @GetMapping("/login")
    public String login() {
        return "login"; // templates/login.html
    }

    /**
     * Handles access denied errors
     * This page is shown when a user tries to access a resource they don't have permission for
     */
    @GetMapping("/access-denied")
    public String accessDenied(Model model) {
        model.addAttribute("errorMessage", "Erişim reddedildi. Bu sayfaya erişim yetkiniz bulunmamaktadır.");
        return "access-denied"; // templates/access-denied.html
    }
}
