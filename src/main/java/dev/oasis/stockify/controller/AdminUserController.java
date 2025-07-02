package dev.oasis.stockify.controller;

import dev.oasis.stockify.config.tenant.TenantContext;
import dev.oasis.stockify.dto.UserCreateDTO;
import dev.oasis.stockify.dto.UserResponseDTO;
import dev.oasis.stockify.model.Role;
import dev.oasis.stockify.service.AppUserService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import jakarta.validation.Valid;
import jakarta.servlet.http.HttpServletRequest;
import java.util.List;
import java.util.Map;

/**
 * Admin controller for comprehensive user management operations
 */
@Controller
@RequestMapping("/admin/users")
@PreAuthorize("hasAnyRole('ADMIN', 'SUPER_ADMIN')")
@RequiredArgsConstructor
@Slf4j
public class AdminUserController {
    
    private final AppUserService appUserService;

    /**
     * Ensure tenant context is set for all requests in this controller
     */
    @ModelAttribute
    public void setupTenantContext(HttpServletRequest request) {
        String currentTenantId = getCurrentTenantId(request);
        TenantContext.setCurrentTenant(currentTenantId);
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
            Model model, HttpServletRequest request) {
        
        // Get current tenant info
        String currentTenantId = getCurrentTenantId(request);
        
        // Create pageable with sorting
        Sort sort = sortDir.equalsIgnoreCase("desc") 
            ? Sort.by(sortBy).descending() 
            : Sort.by(sortBy).ascending();
        Pageable pageable = PageRequest.of(page, size, sort);
          // Get users page
        Page<UserResponseDTO> userPage;
        if (search.isEmpty()) {
            userPage = appUserService.getUsersPage(pageable);
        } else {
            userPage = appUserService.searchUsers(search, pageable);
        }
        
        // Debug log
        log.debug("🔍 Users page - Total: {}, Content size: {}, Page: {}/{}", 
                userPage.getTotalElements(), userPage.getContent().size(), 
                userPage.getNumber(), userPage.getTotalPages());        // Get all roles for filtering (excluding SUPER_ADMIN for regular admins)
        List<Role> availableRoles = List.of(Role.ADMIN, Role.USER);
        
        // Calculate user statistics from all users (not just current page)
        List<UserResponseDTO> allUsersForStats = appUserService.getAllUsers();        long activeUsersCount = allUsersForStats.stream()
                .filter(user -> user.getActive() != null && user.getActive())
                .count();
        long adminUsersCount = allUsersForStats.stream()
                .filter(user -> user.getRole() != null && user.getRole() == Role.ADMIN)
                .count();
        long regularUsersCount = allUsersForStats.stream()
                .filter(user -> user.getRole() != null && user.getRole() == Role.USER)
                .count();
        
        // Add model attributes
        model.addAttribute("users", userPage.getContent());
        model.addAttribute("currentPage", userPage.getNumber());
        model.addAttribute("totalPages", userPage.getTotalPages());
        model.addAttribute("totalItems", userPage.getTotalElements());
        model.addAttribute("pageSize", size);
        model.addAttribute("search", search);
        model.addAttribute("sortBy", sortBy);
        model.addAttribute("sortDir", sortDir);
        model.addAttribute("availableRoles", availableRoles);
        model.addAttribute("currentTenantId", currentTenantId);
        
        // Add user statistics
        model.addAttribute("activeUsersCount", activeUsersCount);
        model.addAttribute("adminUsersCount", adminUsersCount);
        model.addAttribute("regularUsersCount", regularUsersCount);
        
        return "admin/users";
    }

    /**
     * Show form for adding a new user
     */
    @GetMapping("/add")
    public String showAddUserForm(Model model) {
        model.addAttribute("user", new UserCreateDTO());
        model.addAttribute("availableRoles", List.of(Role.ADMIN, Role.USER));
        model.addAttribute("isEdit", false);
        return "admin/user-form";
    }    /**
     * Process form submission to add a new user
     */
    @PostMapping("/add")
    public String addUser(@Valid @ModelAttribute("user") UserCreateDTO userCreateDTO,
                          BindingResult bindingResult,
                          RedirectAttributes redirectAttributes,
                          Model model) {
        
        if (bindingResult.hasErrors()) {
            model.addAttribute("availableRoles", List.of(Role.ADMIN, Role.USER));
            model.addAttribute("isEdit", false);
            return "admin/user-form";
        }
        
        try {
            UserResponseDTO createdUser = appUserService.saveUser(userCreateDTO);
            redirectAttributes.addFlashAttribute("successMessage", 
                "Kullanıcı başarıyla oluşturuldu: " + createdUser.getUsername());
        } catch (RuntimeException e) {
            log.error("❌ Error creating user: {}", e.getMessage());
            String errorMessage = e.getMessage();
            if (errorMessage.contains("zaten mevcut")) {
                redirectAttributes.addFlashAttribute("errorMessage", 
                    "Bu kullanıcı adı zaten kullanılıyor. Lütfen farklı bir kullanıcı adı seçin.");
            } else {
                redirectAttributes.addFlashAttribute("errorMessage", 
                    "Kullanıcı oluşturulurken hata oluştu: " + e.getMessage());
            }
        }
        
        return "redirect:/admin/users";
    }

    /**
     * Show form for editing an existing user
     */
    @GetMapping("/edit/{id}")
    public String showEditUserForm(@PathVariable Long id, Model model, RedirectAttributes redirectAttributes) {
        try {
            UserResponseDTO user = appUserService.getUserById(id);
            UserCreateDTO userDTO = new UserCreateDTO();            userDTO.setUsername(user.getUsername());
            userDTO.setEmail(user.getEmail());
            userDTO.setRole(user.getRole());
            userDTO.setActive(user.getActive());
            
            model.addAttribute("user", userDTO);
            model.addAttribute("userId", id);
            model.addAttribute("availableRoles", List.of(Role.ADMIN, Role.USER));
            model.addAttribute("isEdit", true);
            return "admin/user-form";
        } catch (RuntimeException e) {
            redirectAttributes.addFlashAttribute("errorMessage", 
                "Kullanıcı bulunamadı: " + e.getMessage());
            return "redirect:/admin/users";
        }
    }    /**
     * Process form submission to update an existing user
     */
    @PostMapping("/edit/{id}")
    public String updateUser(@PathVariable Long id,
                            @Valid @ModelAttribute("user") UserCreateDTO userCreateDTO,
                            BindingResult bindingResult,
                            RedirectAttributes redirectAttributes,
                            Model model) {
        
        if (bindingResult.hasErrors()) {
            model.addAttribute("userId", id);
            model.addAttribute("availableRoles", List.of(Role.ADMIN, Role.USER));
            model.addAttribute("isEdit", true);
            return "admin/user-form";
        }
        
        try {
            UserResponseDTO updatedUser = appUserService.updateUser(id, userCreateDTO);
            redirectAttributes.addFlashAttribute("successMessage", 
                "Kullanıcı başarıyla güncellendi: " + updatedUser.getUsername());
        } catch (RuntimeException e) {
            log.error("❌ Error updating user: {}", e.getMessage());
            String errorMessage = e.getMessage();
            if (errorMessage.contains("zaten mevcut")) {
                redirectAttributes.addFlashAttribute("errorMessage", 
                    "Bu kullanıcı adı zaten kullanılıyor. Lütfen farklı bir kullanıcı adı seçin.");
            } else {
                redirectAttributes.addFlashAttribute("errorMessage", 
                    "Kullanıcı güncellenirken hata oluştu: " + e.getMessage());
            }
        }
        
        return "redirect:/admin/users";
    }

    /**
     * Toggle user active status
     */
    @PostMapping("/toggle-status/{id}")
    public String toggleUserStatus(@PathVariable Long id, RedirectAttributes redirectAttributes) {
        try {
            appUserService.toggleUserStatus(id);
            redirectAttributes.addFlashAttribute("successMessage", "Kullanıcı durumu başarıyla değiştirildi.");
        } catch (RuntimeException e) {
            redirectAttributes.addFlashAttribute("errorMessage", 
                "Kullanıcı durumu değiştirilirken hata oluştu: " + e.getMessage());
        }
        
        return "redirect:/admin/users";
    }

    /**
     * Delete user (soft delete)
     */
    @PostMapping("/delete/{id}")
    public String deleteUser(@PathVariable Long id, RedirectAttributes redirectAttributes) {
        try {
            appUserService.deleteUser(id);
            redirectAttributes.addFlashAttribute("successMessage", "Kullanıcı başarıyla silindi.");
        } catch (RuntimeException e) {
            redirectAttributes.addFlashAttribute("errorMessage", 
                "Kullanıcı silinirken hata oluştu: " + e.getMessage());
        }
        
        return "redirect:/admin/users";
    }

    /**
     * Get user details via AJAX
     */
    @GetMapping("/details/{id}")
    @ResponseBody
    public UserResponseDTO getUserDetails(@PathVariable Long id) {
        return appUserService.getUserById(id);
    }

    /**
     * Check if username exists (AJAX endpoint)
     */
    @GetMapping("/check-username")
    @ResponseBody
    public Map<String, Boolean> checkUsername(@RequestParam String username) {
        boolean exists = appUserService.existsByUsername(username);
        return Map.of("exists", exists);
    }

    /**
     * Bulk operations endpoint
     */
    @PostMapping("/bulk-action")
    public String bulkAction(@RequestParam String action,
                            @RequestParam(value = "selectedUsers", required = false) List<Long> userIds,
                            RedirectAttributes redirectAttributes) {
        
        if (userIds == null || userIds.isEmpty()) {
            redirectAttributes.addFlashAttribute("errorMessage", "Lütfen işlem yapılacak kullanıcıları seçin.");
            return "redirect:/admin/users";
        }
        
        try {
            switch (action) {
                case "activate":
                    appUserService.bulkActivateUsers(userIds);
                    redirectAttributes.addFlashAttribute("successMessage", 
                        userIds.size() + " kullanıcı aktif hale getirildi.");
                    break;
                case "deactivate":
                    appUserService.bulkDeactivateUsers(userIds);
                    redirectAttributes.addFlashAttribute("successMessage", 
                        userIds.size() + " kullanıcı pasif hale getirildi.");
                    break;
                case "delete":
                    appUserService.bulkDeleteUsers(userIds);
                    redirectAttributes.addFlashAttribute("successMessage", 
                        userIds.size() + " kullanıcı silindi.");
                    break;
                default:
                    redirectAttributes.addFlashAttribute("errorMessage", "Geçersiz işlem.");
            }
        } catch (RuntimeException e) {
            redirectAttributes.addFlashAttribute("errorMessage", 
                "Toplu işlem sırasında hata oluştu: " + e.getMessage());
        }
        
        return "redirect:/admin/users";
    }
      private String getCurrentTenantId(HttpServletRequest request) {
        // First, try to get from current tenant context
        String currentTenantId = TenantContext.getCurrentTenant();
        if (currentTenantId != null && !currentTenantId.isEmpty()) {
            return currentTenantId;
        }
        
        // Try to get from session (stored during login)
        currentTenantId = (String) request.getSession().getAttribute("tenantId");
        if (currentTenantId != null && !currentTenantId.isEmpty()) {
            return currentTenantId;
        }
        
        // Try to get from header
        currentTenantId = request.getHeader("X-TenantId");
        if (currentTenantId != null && !currentTenantId.isEmpty()) {
            return currentTenantId.toLowerCase();
        }
        
        // Try to get from parameter
        currentTenantId = request.getParameter("tenant_id");
        if (currentTenantId != null && !currentTenantId.isEmpty()) {
            return currentTenantId.toLowerCase();
        }
        
        // Default to public tenant
        return "public";
    }
}
