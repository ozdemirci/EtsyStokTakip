package dev.oasis.stockify.controller;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

@Controller
public class RegisterController {

    @GetMapping("/register")
    public String register(@RequestParam(required = false) String plan, Model model) {
        model.addAttribute("selectedPlan", plan != null ? plan : "starter");
        return "register";
    }
}
