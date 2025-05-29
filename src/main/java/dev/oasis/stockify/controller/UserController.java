package dev.oasis.stockify.controller;

import dev.oasis.stockify.dto.UserCreateDTO;
import dev.oasis.stockify.dto.UserResponseDTO;
import dev.oasis.stockify.service.AppUserService;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * Controller for user management operations
 */
@Controller
@RequestMapping("/users")
public class UserController {
    private final AppUserService appUserService;

    public UserController(AppUserService appUserService) {
        this.appUserService = appUserService;
    }

    /**
     * Displays the form for adding a new user
     */
    @GetMapping("/add")
    public String showAddUserForm(Model model) {
        model.addAttribute("user", new UserCreateDTO());
        model.addAttribute("roles", List.of("ADMIN", "DEPO", "USER"));
        return "user-form";
    }

    /**
     * Processes the form submission to add a new user
     */
    @PostMapping("/add")
    public String addUser(@ModelAttribute UserCreateDTO userCreateDTO) {
        appUserService.saveUser(userCreateDTO);
        return "redirect:/users";
    }

    /**
     * Displays a paginated list of users
     * @param page the page number (0-based)
     * @param size the page size
     * @param model the model to add attributes to
     * @return the view name
     */
    @GetMapping()
    public String listUsers(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            Model model) {
        Pageable pageable = PageRequest.of(page, size, Sort.by("id").ascending());
        Page<UserResponseDTO> userPage = appUserService.getUsersPage(pageable);

        model.addAttribute("users", userPage.getContent());
        model.addAttribute("currentPage", userPage.getNumber());
        model.addAttribute("totalPages", userPage.getTotalPages());
        model.addAttribute("totalItems", userPage.getTotalElements());
        model.addAttribute("pageSize", size);

        return "user-list";
    }
}
