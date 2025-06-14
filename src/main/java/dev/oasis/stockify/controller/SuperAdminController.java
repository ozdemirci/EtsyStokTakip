package dev.oasis.stockify.controller;

import dev.oasis.stockify.dto.UserCreateDTO;
import dev.oasis.stockify.model.AppUser;
import dev.oasis.stockify.model.Product;
import dev.oasis.stockify.service.SuperAdminService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

import java.security.Principal;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Super Admin Controller - Handles cross-tenant management operations
 * Only accessible to users with SUPER_ADMIN role
 */
@Slf4j
@Controller
@RequestMapping("/superadmin")
@PreAuthorize("hasRole('SUPER_ADMIN')")
@RequiredArgsConstructor
public class SuperAdminController {

    private final SuperAdminService superAdminService;

    /**
     * Super Admin Dashboard - Overview of all tenants
     */
    @GetMapping("/dashboard")
    public String dashboard(Model model, Principal principal) {
        log.info("🎛️ Super Admin '{}' accessing dashboard", principal.getName());
        
        try {
            // Get tenant statistics
            Map<String, Map<String, Object>> tenantStats = superAdminService.getTenantStatistics();
            
            // Get available tenants
            Set<String> availableTenants = superAdminService.getAvailableTenants();
            
            model.addAttribute("tenantStats", tenantStats);
            model.addAttribute("availableTenants", availableTenants);
            model.addAttribute("currentUser", principal.getName());
            
            log.info("✅ Super Admin dashboard loaded successfully");
            return "superadmin/dashboard";
            
        } catch (Exception e) {
            log.error("❌ Error loading super admin dashboard: {}", e.getMessage(), e);
            model.addAttribute("error", "Failed to load dashboard data");
            return "superadmin/dashboard";
        } finally {
            superAdminService.clearTenantContext();
        }
    }

    /**
     * All Users Management - View users across all tenants
     */
    @GetMapping("/users")
    public String allUsers(Model model, Principal principal) {
        log.info("👥 Super Admin '{}' accessing all users", principal.getName());
        
        try {
            Map<String, List<AppUser>> tenantUsers = superAdminService.getAllUsersAcrossAllTenants();
            Map<String, Map<String, List<AppUser>>> usersByRole = superAdminService.getUsersByRoleAcrossAllTenants();
            
            model.addAttribute("tenantUsers", tenantUsers);
            model.addAttribute("usersByRole", usersByRole);
            model.addAttribute("availableTenants", superAdminService.getAvailableTenants());
            
            return "superadmin/users";
            
        } catch (Exception e) {
            log.error("❌ Error loading all users: {}", e.getMessage(), e);
            model.addAttribute("error", "Failed to load users data");
            return "superadmin/users";
        } finally {
            superAdminService.clearTenantContext();
        }
    }

    /**
     * All Products Management - View products across all tenants
     */
    @GetMapping("/products")
    public String allProducts(Model model, Principal principal) {
        log.info("📦 Super Admin '{}' accessing all products", principal.getName());
        
        try {
            Map<String, List<Product>> tenantProducts = superAdminService.getAllProductsAcrossAllTenants();
            
            model.addAttribute("tenantProducts", tenantProducts);
            model.addAttribute("availableTenants", superAdminService.getAvailableTenants());
            
            return "superadmin/products";
            
        } catch (Exception e) {
            log.error("❌ Error loading all products: {}", e.getMessage(), e);
            model.addAttribute("error", "Failed to load products data");
            return "superadmin/products";
        } finally {
            superAdminService.clearTenantContext();
        }
    }

    /**
     * Tenant Management - Switch between tenants
     */
    @GetMapping("/tenants")
    public String tenantManagement(Model model, Principal principal) {
        log.info("🏢 Super Admin '{}' accessing tenant management", principal.getName());
        
        try {
            Map<String, Map<String, Object>> tenantStats = superAdminService.getTenantStatistics();
            Set<String> availableTenants = superAdminService.getAvailableTenants();
            
            model.addAttribute("tenantStats", tenantStats);
            model.addAttribute("availableTenants", availableTenants);
            
            return "superadmin/tenants";
            
        } catch (Exception e) {
            log.error("❌ Error loading tenant management: {}", e.getMessage(), e);
            model.addAttribute("error", "Failed to load tenant data");
            return "superadmin/tenants";
        }
    }

    /**
     * Switch to specific tenant
     */
    @PostMapping("/switch-tenant")
    @ResponseBody
    public ResponseEntity<?> switchTenant(@RequestParam String tenantName, Principal principal) {
        log.info("🔄 Super Admin '{}' switching to tenant '{}'", principal.getName(), tenantName);
        
        try {
            superAdminService.switchToTenant(tenantName);
            
            return ResponseEntity.ok()
                .body(Map.of(
                    "success", true,
                    "message", "Successfully switched to tenant: " + tenantName,
                    "currentTenant", tenantName
                ));
                
        } catch (Exception e) {
            log.error("❌ Error switching to tenant '{}': {}", tenantName, e.getMessage());
            return ResponseEntity.badRequest()
                .body(Map.of(
                    "success", false,
                    "message", "Failed to switch to tenant: " + e.getMessage()
                ));
        }
    }

    /**
     * Create user in specific tenant
     */
    @PostMapping("/users/create")
    @ResponseBody
    public ResponseEntity<?> createUser(
            @RequestParam String targetTenant,
            @RequestBody UserCreateDTO userDto,
            Principal principal) {
        
        log.info("👤 Super Admin '{}' creating user '{}' in tenant '{}'", 
                principal.getName(), userDto.getUsername(), targetTenant);
        
        try {
            AppUser createdUser = superAdminService.createUserInTenant(targetTenant, userDto);
            
            return ResponseEntity.ok()
                .body(Map.of(
                    "success", true,
                    "message", "User created successfully in tenant: " + targetTenant,
                    "user", Map.of(
                        "id", createdUser.getId(),
                        "username", createdUser.getUsername(),
                        "role", createdUser.getRole(),
                        "tenant", targetTenant
                    )
                ));
                
        } catch (Exception e) {
            log.error("❌ Error creating user '{}' in tenant '{}': {}", 
                    userDto.getUsername(), targetTenant, e.getMessage());
            return ResponseEntity.badRequest()
                .body(Map.of(
                    "success", false,
                    "message", "Failed to create user: " + e.getMessage()
                ));
        } finally {
            superAdminService.clearTenantContext();
        }
    }

    /**
     * Delete user from specific tenant
     */
    @DeleteMapping("/users/{userId}/tenant/{tenantName}")
    @ResponseBody
    public ResponseEntity<?> deleteUser(
            @PathVariable Long userId,
            @PathVariable String tenantName,
            Principal principal) {
        
        log.info("🗑️ Super Admin '{}' deleting user '{}' from tenant '{}'", 
                principal.getName(), userId, tenantName);
        
        try {
            superAdminService.deleteUserFromTenant(tenantName, userId);
            
            return ResponseEntity.ok()
                .body(Map.of(
                    "success", true,
                    "message", "User deleted successfully from tenant: " + tenantName
                ));
                
        } catch (Exception e) {
            log.error("❌ Error deleting user '{}' from tenant '{}': {}", 
                    userId, tenantName, e.getMessage());
            return ResponseEntity.badRequest()
                .body(Map.of(
                    "success", false,
                    "message", "Failed to delete user: " + e.getMessage()
                ));
        } finally {
            superAdminService.clearTenantContext();
        }
    }

    /**
     * Toggle user status (activate/deactivate)
     */
    @PutMapping("/users/{userId}/tenant/{tenantName}/toggle-status")
    @ResponseBody
    public ResponseEntity<?> toggleUserStatus(
            @PathVariable Long userId,
            @PathVariable String tenantName,
            @RequestParam boolean isActive,
            Principal principal) {
        
        log.info("🔄 Super Admin '{}' {} user '{}' in tenant '{}'", 
                principal.getName(), isActive ? "activating" : "deactivating", userId, tenantName);
        
        try {
            superAdminService.toggleUserStatus(tenantName, userId, isActive);
            
            return ResponseEntity.ok()
                .body(Map.of(
                    "success", true,
                    "message", "User " + (isActive ? "activated" : "deactivated") + " successfully"
                ));
                
        } catch (Exception e) {
            log.error("❌ Error toggling status for user '{}' in tenant '{}': {}", 
                    userId, tenantName, e.getMessage());
            return ResponseEntity.badRequest()
                .body(Map.of(
                    "success", false,
                    "message", "Failed to toggle user status: " + e.getMessage()
                ));
        } finally {
            superAdminService.clearTenantContext();
        }
    }

    /**
     * Get tenant statistics API
     */
    @GetMapping("/api/statistics")
    @ResponseBody
    public ResponseEntity<?> getTenantStatistics(Principal principal) {
        log.info("📊 Super Admin '{}' requesting tenant statistics", principal.getName());
        
        try {
            Map<String, Map<String, Object>> stats = superAdminService.getTenantStatistics();
            
            return ResponseEntity.ok(Map.of(
                "success", true,
                "data", stats
            ));
            
        } catch (Exception e) {
            log.error("❌ Error getting tenant statistics: {}", e.getMessage());
            return ResponseEntity.badRequest()
                .body(Map.of(
                    "success", false,
                    "message", "Failed to get statistics: " + e.getMessage()
                ));
        } finally {
            superAdminService.clearTenantContext();
        }
    }
}
