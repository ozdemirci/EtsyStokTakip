package dev.oasis.stockify.service;

import dev.oasis.stockify.config.tenant.TenantContext;
import dev.oasis.stockify.dto.UserCreateDTO;
import dev.oasis.stockify.model.AppUser;
import dev.oasis.stockify.model.Product;
import dev.oasis.stockify.repository.AppUserRepository;
import dev.oasis.stockify.repository.ProductRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Super Admin Service - Manages cross-tenant operations for SUPER_ADMIN users
 * Provides comprehensive tenant management, user management, and data access across all tenants
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SuperAdminService {

    private final AppUserRepository appUserRepository;
    private final ProductRepository productRepository;
    private final AppUserService appUserService;

    private static final Set<String> ALL_TENANTS = Set.of(
        "public", "stockify", "acme_corp", "global_trade", "artisan_crafts", "tech_solutions"
    );

    /**
     * Get all users across all tenants (SUPER_ADMIN only)
     */
    @Transactional(readOnly = true)
    public Map<String, List<AppUser>> getAllUsersAcrossAllTenants() {
        log.info("🔍 Super Admin: Fetching all users across all tenants");
        
        Map<String, List<AppUser>> tenantUsers = new HashMap<>();
        
        for (String tenant : ALL_TENANTS) {
            try {
                TenantContext.setCurrentTenant(tenant);
                List<AppUser> users = appUserRepository.findAll();
                tenantUsers.put(tenant, users);
                log.debug("📊 Tenant '{}': Found {} users", tenant, users.size());
            } catch (Exception e) {
                log.warn("⚠️ Failed to fetch users for tenant '{}': {}", tenant, e.getMessage());
                tenantUsers.put(tenant, new ArrayList<>());
            } finally {
                TenantContext.clear();
            }
        }
        
        log.info("✅ Successfully retrieved users from {} tenants", tenantUsers.size());
        return tenantUsers;
    }

    /**
     * Get all products across all tenants (SUPER_ADMIN only)
     */
    @Transactional(readOnly = true)
    public Map<String, List<Product>> getAllProductsAcrossAllTenants() {
        log.info("🔍 Super Admin: Fetching all products across all tenants");
        
        Map<String, List<Product>> tenantProducts = new HashMap<>();
        
        for (String tenant : ALL_TENANTS) {
            try {
                TenantContext.setCurrentTenant(tenant);
                List<Product> products = productRepository.findAll();
                tenantProducts.put(tenant, products);
                log.debug("📊 Tenant '{}': Found {} products", tenant, products.size());
            } catch (Exception e) {
                log.warn("⚠️ Failed to fetch products for tenant '{}': {}", tenant, e.getMessage());
                tenantProducts.put(tenant, new ArrayList<>());
            } finally {
                TenantContext.clear();
            }
        }
        
        log.info("✅ Successfully retrieved products from {} tenants", tenantProducts.size());
        return tenantProducts;
    }

    /**
     * Create a user in a specific tenant (SUPER_ADMIN only)
     */
    @Transactional
    public AppUser createUserInTenant(String targetTenant, UserCreateDTO userDto) {
        log.info("👤 Super Admin: Creating user '{}' in tenant '{}'", userDto.getUsername(), targetTenant);
        
        try {
            TenantContext.setCurrentTenant(targetTenant);
            AppUser createdUser = appUserService.createUser(userDto);
            log.info("✅ Successfully created user '{}' in tenant '{}'", createdUser.getUsername(), targetTenant);
            return createdUser;
        } catch (Exception e) {
            log.error("❌ Failed to create user '{}' in tenant '{}': {}", userDto.getUsername(), targetTenant, e.getMessage());
            throw new RuntimeException("Failed to create user in tenant " + targetTenant, e);
        } finally {
            TenantContext.clear();
        }
    }

    /**
     * Delete a user from a specific tenant (SUPER_ADMIN only)
     */
    @Transactional
    public void deleteUserFromTenant(String targetTenant, Long userId) {
        log.info("🗑️ Super Admin: Deleting user '{}' from tenant '{}'", userId, targetTenant);
        
        try {
            TenantContext.setCurrentTenant(targetTenant);
            
            Optional<AppUser> userOptional = appUserRepository.findById(userId);
            if (userOptional.isPresent()) {
                AppUser user = userOptional.get();
                
                // Prevent deleting SUPER_ADMIN users
                if ("SUPER_ADMIN".equals(user.getRole())) {
                    throw new IllegalArgumentException("Cannot delete SUPER_ADMIN users");
                }
                
                appUserRepository.deleteById(userId);
                log.info("✅ Successfully deleted user '{}' from tenant '{}'", user.getUsername(), targetTenant);
            } else {
                throw new IllegalArgumentException("User not found with ID: " + userId);
            }
        } catch (Exception e) {
            log.error("❌ Failed to delete user '{}' from tenant '{}': {}", userId, targetTenant, e.getMessage());
            throw new RuntimeException("Failed to delete user from tenant " + targetTenant, e);
        } finally {
            TenantContext.clear();
        }
    }

    /**
     * Switch to a specific tenant context for operations (SUPER_ADMIN only)
     */
    public void switchToTenant(String targetTenant) {
        if (!ALL_TENANTS.contains(targetTenant)) {
            throw new IllegalArgumentException("Invalid tenant: " + targetTenant);
        }
        
        log.info("🔄 Super Admin: Switching to tenant context '{}'", targetTenant);
        TenantContext.setCurrentTenant(targetTenant);
    }

    /**
     * Get tenant statistics (SUPER_ADMIN only)
     */
    @Transactional(readOnly = true)
    public Map<String, Map<String, Object>> getTenantStatistics() {
        log.info("📊 Super Admin: Generating tenant statistics");
        
        Map<String, Map<String, Object>> tenantStats = new HashMap<>();
        
        for (String tenant : ALL_TENANTS) {
            try {
                TenantContext.setCurrentTenant(tenant);
                
                Map<String, Object> stats = new HashMap<>();
                stats.put("userCount", appUserRepository.count());
                stats.put("productCount", productRepository.count());
                stats.put("activeUserCount", appUserRepository.countByIsActive(true));
                stats.put("totalStockValue", calculateTotalStockValue());
                stats.put("lowStockProductCount", productRepository.countLowStockProducts());
                
                tenantStats.put(tenant, stats);
                log.debug("📈 Tenant '{}' stats: {} users, {} products", tenant, stats.get("userCount"), stats.get("productCount"));
                
            } catch (Exception e) {
                log.warn("⚠️ Failed to calculate stats for tenant '{}': {}", tenant, e.getMessage());
                Map<String, Object> errorStats = new HashMap<>();
                errorStats.put("error", "Failed to calculate statistics");
                tenantStats.put(tenant, errorStats);
            } finally {
                TenantContext.clear();
            }
        }
        
        log.info("✅ Generated statistics for {} tenants", tenantStats.size());
        return tenantStats;
    }

    /**
     * Get users by role across all tenants (SUPER_ADMIN only)
     */
    @Transactional(readOnly = true)
    public Map<String, Map<String, List<AppUser>>> getUsersByRoleAcrossAllTenants() {
        log.info("👥 Super Admin: Fetching users by role across all tenants");
        
        Map<String, Map<String, List<AppUser>>> result = new HashMap<>();
        
        for (String tenant : ALL_TENANTS) {
            try {
                TenantContext.setCurrentTenant(tenant);
                
                List<AppUser> allUsers = appUserRepository.findAll();
                Map<String, List<AppUser>> usersByRole = allUsers.stream()
                    .collect(Collectors.groupingBy(AppUser::getRole));
                
                result.put(tenant, usersByRole);
                
            } catch (Exception e) {
                log.warn("⚠️ Failed to fetch users by role for tenant '{}': {}", tenant, e.getMessage());
                result.put(tenant, new HashMap<>());
            } finally {
                TenantContext.clear();
            }
        }
        
        return result;
    }

    /**
     * Activate/Deactivate a user in a specific tenant (SUPER_ADMIN only)
     */
    @Transactional
    public void toggleUserStatus(String targetTenant, Long userId, boolean isActive) {
        log.info("🔄 Super Admin: {} user '{}' in tenant '{}'", 
                isActive ? "Activating" : "Deactivating", userId, targetTenant);
        
        try {
            TenantContext.setCurrentTenant(targetTenant);
            
            AppUser user = appUserRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("User not found with ID: " + userId));
            
            // Prevent deactivating SUPER_ADMIN users
            if ("SUPER_ADMIN".equals(user.getRole()) && !isActive) {
                throw new IllegalArgumentException("Cannot deactivate SUPER_ADMIN users");
            }
            
            user.setIsActive(isActive);
            appUserRepository.save(user);
            
            log.info("✅ Successfully {} user '{}' in tenant '{}'", 
                    isActive ? "activated" : "deactivated", user.getUsername(), targetTenant);
            
        } catch (Exception e) {
            log.error("❌ Failed to toggle status for user '{}' in tenant '{}': {}", userId, targetTenant, e.getMessage());
            throw new RuntimeException("Failed to toggle user status in tenant " + targetTenant, e);
        } finally {
            TenantContext.clear();
        }
    }

    /**
     * Get available tenants for the super admin
     */
    public Set<String> getAvailableTenants() {
        return new HashSet<>(ALL_TENANTS);
    }

    /**
     * Calculate total stock value for current tenant
     */
    private double calculateTotalStockValue() {
        try {
            return productRepository.findAll().stream()
                .mapToDouble(product -> product.getPrice().doubleValue() * product.getStockLevel())
                .sum();
        } catch (Exception e) {
            log.warn("Failed to calculate total stock value: {}", e.getMessage());
            return 0.0;
        }
    }

    /**
     * Clear tenant context
     */
    public void clearTenantContext() {
        TenantContext.clear();
        log.debug("🧹 Cleared tenant context");
    }
}
