package dev.oasis.stockify.service;

import dev.oasis.stockify.config.tenant.TenantContext;
import dev.oasis.stockify.dto.UserCreateDTO;
import dev.oasis.stockify.model.AppUser;
import dev.oasis.stockify.model.ContactMessage;
import dev.oasis.stockify.model.Product;
import dev.oasis.stockify.model.Role;
import dev.oasis.stockify.repository.AppUserRepository;
import dev.oasis.stockify.repository.ContactMessageRepository;
import dev.oasis.stockify.repository.ProductRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
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
    private final ContactMessageRepository contactMessageRepository;
    private final AppUserService appUserService;

    @Value("${spring.flyway.locations}")
    private String[] migrationLocations;
    
    private Set<String> getAllTenants() {
        Set<String> tenants = new HashSet<>();
        for (String location : migrationLocations) {
            // Extract tenant name from location path like "classpath:db/migration/stockify"
            String[] parts = location.split("/");
            if (parts.length > 0) {
                String tenantName = parts[parts.length - 1];
                if (!tenantName.isEmpty() && !tenantName.equals("migration")) {
                    tenants.add(tenantName);
                }
            }
        }
        // Always include public tenant
        tenants.add("public");
        return tenants;
    }    
    
    /**
     * Get all users across all tenants (SUPER_ADMIN only)
     * Returns both active and inactive users for comprehensive management
     * Note: SUPER_ADMIN users are only shown for the 'public' tenant
     */
    @Transactional(readOnly = true)
    public Map<String, List<AppUser>> getAllUsersAcrossAllTenants() {
        log.info("🔍 Super Admin: Fetching all users (active and inactive) across all tenants");
        
        Map<String, List<AppUser>> tenantUsers = new HashMap<>();
          for (String tenant : getAllTenants()) {
            try {
                TenantContext.setCurrentTenant(tenant);
                // SuperAdmin can see both active and inactive users
                List<AppUser> users = appUserRepository.findAll();
                  // Filter out SUPER_ADMIN users from non-public tenants
                if (!"public".equals(tenant)) {
                    users = users.stream()
                            .filter(user -> !Role.SUPER_ADMIN.equals(user.getRole()))
                            .collect(Collectors.toList());
                    log.debug("📊 Tenant '{}': Filtered out SUPER_ADMIN users, showing {} users (active and inactive)", tenant, users.size());
                } else {
                    log.debug("📊 Tenant '{}' (public): Showing all {} users including SUPER_ADMIN (active and inactive)", tenant, users.size());
                }
                
                tenantUsers.put(tenant, users);
            } catch (Exception e) {
                log.warn("⚠️ Failed to fetch users for tenant '{}': {}", tenant, e.getMessage());
                tenantUsers.put(tenant, new ArrayList<>());
            } finally {
                TenantContext.clear();
            }
        }
        
        log.info("✅ Successfully retrieved users from {} tenants (SUPER_ADMIN only in public, including inactive users)", tenantUsers.size());
        return tenantUsers;
    }

    /**
     * Get all products across all tenants (SUPER_ADMIN only)
     */
    @Transactional(readOnly = true)
    public Map<String, List<Product>> getAllProductsAcrossAllTenants() {
        log.info("🔍 Super Admin: Fetching all products across all tenants");
        
        Map<String, List<Product>> tenantProducts = new HashMap<>();
        
        for (String tenant : getAllTenants()) {
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
                if (Role.SUPER_ADMIN.equals(user.getRole())) {
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
        if (!getAllTenants().contains(targetTenant)) {
            throw new IllegalArgumentException("Invalid tenant: " + targetTenant);
        }
        
        log.info("🔄 Super Admin: Switching to tenant context '{}'", targetTenant);
        TenantContext.setCurrentTenant(targetTenant);
    }    /**
     * Get tenant statistics (SUPER_ADMIN only)
     * Note: SUPER_ADMIN users are only counted for the 'public' tenant
     */
    @Transactional(readOnly = true)
    public Map<String, Map<String, Object>> getTenantStatistics() {
        log.info("📊 Super Admin: Generating tenant statistics");
        
        Map<String, Map<String, Object>> tenantStats = new HashMap<>();
        
        for (String tenant : getAllTenants()) {
            try {
                TenantContext.setCurrentTenant(tenant);
                
                Map<String, Object> stats = new HashMap<>();
                  // Calculate user count - only show SUPER_ADMIN for public tenant
                long userCount;
                long activeUserCount;
                
                if ("public".equals(tenant)) {
                    // For public tenant, include all users including SUPER_ADMIN
                    userCount = appUserRepository.count();
                    activeUserCount = appUserRepository.countByIsActive(true);
                    log.debug("📊 Tenant '{}' (public): All user count {} (including SUPER_ADMIN)", tenant, userCount);                } else {
                    // For other tenants, exclude SUPER_ADMIN users from count
                    List<AppUser> users = appUserRepository.findAll();
                    userCount = users.stream()
                            .filter(user -> !Role.SUPER_ADMIN.equals(user.getRole()))
                            .count();
                    activeUserCount = users.stream()
                            .filter(user -> !Role.SUPER_ADMIN.equals(user.getRole()) && Boolean.TRUE.equals(user.getIsActive()))
                            .count();
                    log.debug("📊 Tenant '{}': Filtered user count {} (excluding SUPER_ADMIN)", tenant, userCount);
                }
                
                stats.put("userCount", userCount);
                stats.put("productCount", productRepository.count());
                stats.put("activeUserCount", activeUserCount);
                stats.put("totalStockValue", calculateTotalStockValue());
                stats.put("lowStockProductCount", productRepository.countLowStockProducts());
                
                // Add contact message statistics
                long totalContactMessages = contactMessageRepository.count();
                long unreadContactMessages = contactMessageRepository.countByIsReadFalse();
                stats.put("totalContactMessages", totalContactMessages);
                stats.put("unreadContactMessages", unreadContactMessages);
                
                tenantStats.put(tenant, stats);
                log.debug("📈 Tenant '{}' stats: {} users, {} products", tenant, userCount, stats.get("productCount"));
                
            } catch (Exception e) {
                log.warn("⚠️ Failed to calculate stats for tenant '{}': {}", tenant, e.getMessage());
                Map<String, Object> errorStats = new HashMap<>();
                errorStats.put("error", "Failed to calculate statistics");
                tenantStats.put(tenant, errorStats);
            } finally {
                TenantContext.clear();
            }
        }
        
        log.info("✅ Generated statistics for {} tenants (SUPER_ADMIN only counted in public)", tenantStats.size());
        return tenantStats;
    }    /**
     * Get users by role across all tenants (SUPER_ADMIN only)
     */
    @Transactional(readOnly = true)
    public Map<String, Map<Role, List<AppUser>>> getUsersByRoleAcrossAllTenants() {
        log.info("👥 Super Admin: Fetching users by role across all tenants");
        
        Map<String, Map<Role, List<AppUser>>> result = new HashMap<>();
        
        for (String tenant : getAllTenants()) {
            try {
                TenantContext.setCurrentTenant(tenant);
                
                List<AppUser> allUsers = appUserRepository.findAll();
                Map<Role, List<AppUser>> usersByRole = allUsers.stream()
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
            if (Role.SUPER_ADMIN.equals(user.getRole()) && !isActive) {
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
        return getAllTenants();
    }

    /**
     * Get all contact messages across all tenants (SUPER_ADMIN only)
     */
    @Transactional(readOnly = true)
    public Map<String, List<ContactMessage>> getAllContactMessagesAcrossAllTenants() {
        log.info("🔍 Super Admin: Fetching all contact messages across all tenants");
        
        Map<String, List<ContactMessage>> tenantContactMessages = new HashMap<>();
        
        for (String tenant : getAllTenants()) {
            try {
                TenantContext.setCurrentTenant(tenant);
                List<ContactMessage> messages = contactMessageRepository.findAllByOrderByCreatedAtDesc();
                tenantContactMessages.put(tenant, messages);
                log.debug("📊 Tenant '{}': Found {} contact messages", tenant, messages.size());
            } catch (Exception e) {
                log.warn("⚠️ Failed to fetch contact messages for tenant '{}': {}", tenant, e.getMessage());
                tenantContactMessages.put(tenant, new ArrayList<>());
            } finally {
                TenantContext.clear();
            }
        }
        
        log.info("✅ Successfully retrieved contact messages from {} tenants", tenantContactMessages.size());
        return tenantContactMessages;
    }

    /**
     * Get contact message statistics across all tenants (SUPER_ADMIN only)
     */
    @Transactional(readOnly = true)
    public Map<String, Object> getContactMessageStatistics() {
        log.info("📊 Super Admin: Generating contact message statistics");
        
        Map<String, Object> globalStats = new HashMap<>();
        long totalMessages = 0;
        long unreadMessages = 0;
        long respondedMessages = 0;
        
        for (String tenant : getAllTenants()) {
            try {
                TenantContext.setCurrentTenant(tenant);
                
                long tenantTotal = contactMessageRepository.count();
                long tenantUnread = contactMessageRepository.countByIsReadFalse();
                long tenantResponded = contactMessageRepository.countByIsRespondedTrue();
                
                totalMessages += tenantTotal;
                unreadMessages += tenantUnread;
                respondedMessages += tenantResponded;
                
                log.debug("📊 Tenant '{}': {} total, {} unread, {} responded", 
                         tenant, tenantTotal, tenantUnread, tenantResponded);
                
            } catch (Exception e) {
                log.warn("⚠️ Failed to get contact message stats for tenant '{}': {}", tenant, e.getMessage());
            } finally {
                TenantContext.clear();
            }
        }
        
        globalStats.put("totalMessages", totalMessages);
        globalStats.put("unreadMessages", unreadMessages);
        globalStats.put("respondedMessages", respondedMessages);
        globalStats.put("pendingMessages", totalMessages - respondedMessages);
        
        log.info("✅ Global contact message stats: {} total, {} unread, {} responded", 
                totalMessages, unreadMessages, respondedMessages);
        
        return globalStats;
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
