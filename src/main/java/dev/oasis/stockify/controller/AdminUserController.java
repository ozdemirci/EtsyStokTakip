package dev.oasis.stockify.controller;

import dev.oasis.stockify.dto.UserCreateDTO;
import dev.oasis.stockify.dto.UserResponseDTO;
import dev.oasis.stockify.model.Role;
import dev.oasis.stockify.service.AppUserService;
import dev.oasis.stockify.util.TenantResolutionUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import jakarta.validation.Valid;
import jakarta.servlet.http.HttpServletRequest;
import java.util.List;

/**
 * Admin controller for comprehensive user management operations
 */
@Controller
@RequestMapping("/admin/users")
@PreAuthorize("hasAnyRole('ADMIN', 'SUPER_ADMIN')")
@Slf4j
@RequiredArgsConstructor
public class AdminUserController {
    
    private final AppUserService appUserService;
    private final TenantResolutionUtil tenantResolutionUtil;    

    /**
     * Ensure tenant context is set for all requests in this controller
     */
    @ModelAttribute
    public void setupTenantContext(HttpServletRequest request) {
        tenantResolutionUtil.setupTenantContext(request);
    }

    /**
     * Main users management page with comprehensive tenant user information
     */    
    @GetMapping
    public String listUsers(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "15") int size,
            @RequestParam(defaultValue = "") String search,
            @RequestParam(defaultValue = "username") String sortBy,
            @RequestParam(defaultValue = "asc") String sortDir,
            Model model, 
            HttpServletRequest request,
            Authentication authentication) {
        
        // Get current tenant info
        String currentTenantId = tenantResolutionUtil.resolveTenantId(request, authentication, false);
        
        // Create pageable with sorting
        Sort sort = sortDir.equalsIgnoreCase("desc") 
            ? Sort.by(sortBy).descending() 
            : Sort.by(sortBy).ascending();
        Pageable pageable = PageRequest.of(page, size, sort);
        
        // Get users page
        Page<UserResponseDTO> userPage = Page.empty();
        if (search.isEmpty()) {
            userPage = appUserService.getUsersPage(pageable);
        } else {
            userPage = appUserService.searchUsers(search, pageable);
        }
        
        // Debug log
        log.debug("🔍 Users page - Total: {}, Content size: {}, Page: {}/{}", 
                userPage.getTotalElements(), userPage.getContent().size(), 
                userPage.getNumber(), userPage.getTotalPages());
                
        // Get all roles for filtering (excluding SUPER_ADMIN for regular admins)
        List<Role> availableRoles = List.of(Role.ADMIN, Role.USER);
        
        // Add model attributes
        model.addAttribute("users", userPage);
        model.addAttribute("currentPage", page);
        model.addAttribute("totalPages", userPage.getTotalPages());
        model.addAttribute("totalItems", userPage.getTotalElements());
        model.addAttribute("pageSize", size);
        model.addAttribute("sortBy", sortBy);
        model.addAttribute("sortDir", sortDir);
        model.addAttribute("search", search);
        model.addAttribute("availableRoles", availableRoles);
        model.addAttribute("currentTenantId", currentTenantId);
        
        return "admin/users";
    }
    
    /**
     * Show form for adding a new user
     */
    @GetMapping("/add")
    public String showAddUserForm(Model model, 
                                 HttpServletRequest request,
                                 Authentication authentication) {
        String currentTenantId = tenantResolutionUtil.resolveTenantId(request, authentication, true);
        log.info("➕ Showing form to add a new user for tenant: {}", currentTenantId);
        
        model.addAttribute("user", new UserCreateDTO());
        model.addAttribute("currentTenantId", currentTenantId);
        model.addAttribute("availableRoles", List.of(Role.ADMIN, Role.USER));
        model.addAttribute("formAction", "/admin/users");
        return "admin/user-form";
    }
    
    /**
     * Handle user creation
     */
    @PostMapping
    public String createUser(@ModelAttribute("user") @Valid UserCreateDTO userCreateDTO,
                           BindingResult bindingResult,
                           RedirectAttributes redirectAttributes,
                           HttpServletRequest request,
                           Authentication authentication,
                           Model model) {
        String currentTenantId = tenantResolutionUtil.resolveTenantId(request, authentication, true);
        log.info("💾 Creating a new user for tenant: {}", currentTenantId);
        
        // Check for validation errors
        if (bindingResult.hasErrors()) {
            log.warn("❌ Validation errors while creating user: {}", bindingResult.getAllErrors());
            model.addAttribute("currentTenantId", currentTenantId);
            model.addAttribute("availableRoles", List.of(Role.ADMIN, Role.USER));
            model.addAttribute("formAction", "/admin/users");
            return "admin/user-form";
        }
        
        try {
            // Create the user
            appUserService.createUser(userCreateDTO);
            redirectAttributes.addFlashAttribute("successMessage", 
                    "User " + userCreateDTO.getUsername() + " created successfully!");
            log.info("✅ Successfully created user: {} for tenant: {}", 
                    userCreateDTO.getUsername(), currentTenantId);
            
        } catch (Exception e) {
            log.error("❌ Error creating user: {}", e.getMessage(), e);
            redirectAttributes.addFlashAttribute("errorMessage", 
                    "Error creating user: " + e.getMessage());
            
            // If username already exists, add field error
            if (e.getMessage().contains("already exists")) {
                bindingResult.rejectValue("username", "duplicate", "Username already exists");
                model.addAttribute("currentTenantId", currentTenantId);
                model.addAttribute("availableRoles", List.of(Role.ADMIN, Role.USER));
                model.addAttribute("formAction", "/admin/users");
                return "admin/user-form";
            }
        }
        
        return "redirect:/admin/users";
    }
    
    /**
     * Show form for editing an existing user
     */
    @GetMapping("/{id}/edit")
    public String showEditUserForm(@PathVariable Long id,
                                 Model model,
                                 RedirectAttributes redirectAttributes,
                                 HttpServletRequest request,
                                 Authentication authentication) {
        String currentTenantId = tenantResolutionUtil.resolveTenantId(request, authentication, true);
        log.info("✏️ Showing edit form for user ID: {} for tenant: {}", id, currentTenantId);
        
        try {
            // Get user by ID
            UserResponseDTO user = appUserService.getUserById(id);
            
            // Create DTO for form binding
            UserCreateDTO userEditDTO = new UserCreateDTO();
            userEditDTO.setUsername(user.getUsername());
            userEditDTO.setEmail(user.getEmail());
            userEditDTO.setRole(user.getRole());
            
            // Set model attributes
            model.addAttribute("user", userEditDTO);
            model.addAttribute("userId", id);
            model.addAttribute("currentTenantId", currentTenantId);
            model.addAttribute("availableRoles", List.of(Role.ADMIN, Role.USER));
            model.addAttribute("formAction", "/admin/users/" + id);
            model.addAttribute("isEditMode", true);
            
            return "admin/user-form";
            
        } catch (Exception e) {
            log.error("❌ Error getting user with ID: {}: {}", id, e.getMessage(), e);
            redirectAttributes.addFlashAttribute("errorMessage", 
                    "Error loading user: " + e.getMessage());
            return "redirect:/admin/users";
        }
    }
    
    /**
     * Handle user update
     */
    @PostMapping("/{id}")
    public String updateUser(@PathVariable Long id,
                           @ModelAttribute("user") @Valid UserCreateDTO userUpdateDTO,
                           BindingResult bindingResult,
                           RedirectAttributes redirectAttributes,
                           HttpServletRequest request,
                           Authentication authentication,
                           Model model) {
        String currentTenantId = tenantResolutionUtil.resolveTenantId(request, authentication, true);
        log.info("🔄 Updating user ID: {} for tenant: {}", id, currentTenantId);
        
        // Check for validation errors
        if (bindingResult.hasErrors()) {
            log.warn("❌ Validation errors while updating user: {}", bindingResult.getAllErrors());
            model.addAttribute("userId", id);
            model.addAttribute("currentTenantId", currentTenantId);
            model.addAttribute("availableRoles", List.of(Role.ADMIN, Role.USER));
            model.addAttribute("formAction", "/admin/users/" + id);
            model.addAttribute("isEditMode", true);
            return "admin/user-form";
        }
        
        try {
            // Update the user
            UserResponseDTO updatedUser = appUserService.updateUser(id, userUpdateDTO);
            redirectAttributes.addFlashAttribute("successMessage", 
                    "User " + updatedUser.getUsername() + " updated successfully!");
            log.info("✅ Successfully updated user: {} for tenant: {}", 
                    updatedUser.getUsername(), currentTenantId);
            
        } catch (Exception e) {
            log.error("❌ Error updating user: {}", e.getMessage(), e);
            redirectAttributes.addFlashAttribute("errorMessage", 
                    "Error updating user: " + e.getMessage());
        }
        
        return "redirect:/admin/users";
    }
    
    /**
     * Delete user
     */
    @PostMapping("/{id}/delete")
    public String deleteUser(@PathVariable Long id,
                           RedirectAttributes redirectAttributes,
                           HttpServletRequest request,
                           Authentication authentication) {
        String currentTenantId = tenantResolutionUtil.resolveTenantId(request, authentication, true);
        log.info("🗑️ Deleting user ID: {} for tenant: {}", id, currentTenantId);
        
        try {
            // Delete the user
            appUserService.deleteUser(id);
            redirectAttributes.addFlashAttribute("successMessage", "User deleted successfully!");
            log.info("✅ Successfully deleted user ID: {} for tenant: {}", id, currentTenantId);
            
        } catch (Exception e) {
            log.error("❌ Error deleting user: {}", e.getMessage(), e);
            redirectAttributes.addFlashAttribute("errorMessage", 
                    "Error deleting user: " + e.getMessage());
        }
        
        return "redirect:/admin/users";
    }
    
    /**
     * Bulk delete users
     */
    @PostMapping("/bulk-delete")
    public String bulkDeleteUsers(@RequestParam("selectedUsers") List<Long> selectedUserIds,
                                RedirectAttributes redirectAttributes,
                                HttpServletRequest request,
                                Authentication authentication) {
        String currentTenantId = tenantResolutionUtil.resolveTenantId(request, authentication, true);
        log.info("🗑️ Bulk deleting {} users for tenant: {}", selectedUserIds.size(), currentTenantId);
        
        try {
            // Delete the users in bulk
            appUserService.bulkDeleteUsers(selectedUserIds);
            redirectAttributes.addFlashAttribute("successMessage", 
                    "Successfully deleted selected users");
            log.info("✅ Successfully deleted users for tenant: {}", currentTenantId);
            
        } catch (Exception e) {
            log.error("❌ Error during bulk delete: {}", e.getMessage(), e);
            redirectAttributes.addFlashAttribute("errorMessage", 
                "Toplu işlem sırasında hata oluştu: " + e.getMessage());
        }
        
        return "redirect:/admin/users";
    }
}
