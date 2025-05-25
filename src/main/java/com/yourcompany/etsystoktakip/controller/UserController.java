package com.yourcompany.etsystoktakip.controller;

import com.yourcompany.etsystoktakip.service.AppUserService;
import com.yourcompany.etsystoktakip.model.AppUser;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;

import java.util.List;

@Controller
@RequestMapping("/users")
public class UserController {
    @Autowired
    private AppUserService appUserService;

    @GetMapping("/add")
    public String showAddUserForm(Model model) {
        model.addAttribute("user", new AppUser());
        model.addAttribute("roles", List.of("ADMIN", "DEPO", "USER"));
        return "user-form";
    }

    @PostMapping("/add")
    public String addUser(@ModelAttribute AppUser user) {
        appUserService.saveUser(user);
        return "redirect:/users/list";
    }

    @GetMapping("/list")
    public String listUsers(Model model) {
        model.addAttribute("users", appUserService.getAllUsers());
        return "user-list";
    }
}