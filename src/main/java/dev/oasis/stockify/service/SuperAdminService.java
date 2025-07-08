package dev.oasis.stockify.service;

import dev.oasis.stockify.dto.UserCreateDTO;
import dev.oasis.stockify.dto.UserResponseDTO;
import dev.oasis.stockify.mapper.UserMapper;
import dev.oasis.stockify.model.AppUser;
import dev.oasis.stockify.model.ContactMessage;
import dev.oasis.stockify.model.Product;
import dev.oasis.stockify.model.Role;
import dev.oasis.stockify.repository.AppUserRepository;
import dev.oasis.stockify.repository.ContactMessageRepository;
import dev.oasis.stockify.repository.ProductRepository;
import dev.oasis.stockify.util.ServiceTenantUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.sql.DataSource;
import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
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
    private final DataSource dataSource;
    private final ServiceTenantUtil serviceTenantUtil;
    private final UserMapper userMapper;
    private final SubscriptionService subscriptionService;
    
    /**
     * Get all tenant schemas from database - includes dynamically created tenants
     */
    private Set<String> getAllTenants() {
        Set<String> tenants = new HashSet<>();
        
        try (Connection connection = dataSource.getConnection()) {
            // Get all schemas that contain tenant_config table (indicating a tenant schema)
            String sql = """
                SELECT schema_name 
                FROM information_schema.schemata 
                WHERE schema_name NOT IN ('information_schema', 'pg_catalog', 'pg_toast', 'pg_temp_1', 'pg_toast_temp_1')
                AND EXISTS (
                    SELECT 1 FROM information_schema.tables 
                    WHERE table_schema = schema_name 
                    AND table_name = 'tenant_config'
                )
                """;
            
            try (PreparedStatement stmt = connection.prepareStatement(sql);
                 ResultSet rs = stmt.executeQuery()) {
                
                while (rs.next()) {
                    String schemaName = rs.getString("schema_name");
                    tenants.add(schemaName);
                }
            }
            
            // Always include public if it has users (for super admin)
            try {
                connection.setSchema("public");
                try (PreparedStatement checkStmt = connection.prepareStatement("SELECT 1 FROM app_user LIMIT 1");
                     ResultSet rs = checkStmt.executeQuery()) {
                    if (rs.next()) {
                        tenants.add("public");
                    }
                }
            } catch (SQLException e) {
                log.debug("Public schema doesn't have app_user table, skipping");
            }
            
            log.debug("📊 Found {} tenant schemas: {}", tenants.size(), tenants);
            
        } catch (SQLException e) {
            log.error("❌ Failed to get tenant schemas from database: {}", e.getMessage());
            // Fallback to default tenants if database query fails
            tenants.add("public");
        }
        
        return tenants;
    }
    
    /**
     * Get all users across all tenants (SUPER_ADMIN only)
     * Returns both active and inactive users for comprehensive management
     * Note: SUPER_ADMIN users are only shown for the 'public' tenant
     */
    @Transactional(readOnly = true)
    /**
     * Get all users across all tenants (returns DTOs for templates)
     */
    public Map<String, List<UserResponseDTO>> getAllUsersAcrossAllTenantsAsDTO() {
        log.info("🔍 Super Admin: Fetching all users (active and inactive) across all tenants as DTOs");
        
        Map<String, List<UserResponseDTO>> tenantUsers = new HashMap<>();
        for (String tenant : getAllTenants()) {
            try {
                List<UserResponseDTO> users = serviceTenantUtil.executeInTenant(tenant, () -> {
                    // SuperAdmin can see both active and inactive users
                    List<AppUser> fetchedUsers = appUserRepository.findAll();
                    
                    // Filter out SUPER_ADMIN users from non-public tenants
                    List<AppUser> filteredUsers;
                    if (!"public".equals(tenant)) {
                        filteredUsers = fetchedUsers.stream()
                                .filter(user -> !Role.SUPER_ADMIN.equals(user.getRole()))
                                .collect(Collectors.toList());
                    } else {
                        log.debug("📊 Tenant '{}' (public): Showing all {} users including SUPER_ADMIN (active and inactive)", tenant, fetchedUsers.size());
                        filteredUsers = fetchedUsers;
                    }
                    
                    // Convert to DTOs
                    return filteredUsers.stream()
                            .map(userMapper::toDto)
                            .collect(Collectors.toList());
                });
                
                log.debug("📊 Tenant '{}': Fetched {} users (active and inactive) as DTOs", tenant, users.size());
                tenantUsers.put(tenant, users);
            } catch (Exception e) {
                log.warn("⚠️ Failed to fetch users for tenant '{}': {}", tenant, e.getMessage());
                tenantUsers.put(tenant, new ArrayList<>());
            }
        }
        
        log.info("✅ Successfully retrieved users from {} tenants as DTOs (SUPER_ADMIN only in public, including inactive users)", tenantUsers.size());
        return tenantUsers;
    }

    /**
     * Get all users across all tenants (returns entities for internal use)
     */
    public Map<String, List<AppUser>> getAllUsersAcrossAllTenants() {
        log.info("🔍 Super Admin: Fetching all users (active and inactive) across all tenants");
        
        Map<String, List<AppUser>> tenantUsers = new HashMap<>();
        for (String tenant : getAllTenants()) {
            try {
                List<AppUser> users = serviceTenantUtil.executeInTenant(tenant, () -> {
                    // SuperAdmin can see both active and inactive users
                    List<AppUser> fetchedUsers = appUserRepository.findAll();
                    
                    // Filter out SUPER_ADMIN users from non-public tenants
                    if (!"public".equals(tenant)) {
                        return fetchedUsers.stream()
                                .filter(user -> !Role.SUPER_ADMIN.equals(user.getRole()))
                                .collect(Collectors.toList());
                    } else {
                        log.debug("📊 Tenant '{}' (public): Showing all {} users including SUPER_ADMIN (active and inactive)", tenant, fetchedUsers.size());
                        return fetchedUsers;
                    }
                });
                
                log.debug("📊 Tenant '{}': Fetched {} users (active and inactive)", tenant, users.size());
                tenantUsers.put(tenant, users);
            } catch (Exception e) {
                log.warn("⚠️ Failed to fetch users for tenant '{}': {}", tenant, e.getMessage());
                tenantUsers.put(tenant, new ArrayList<>());
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
                List<Product> products = serviceTenantUtil.executeInTenant(tenant, () -> productRepository.findAll());
                tenantProducts.put(tenant, products);
                log.debug("📊 Tenant '{}': Found {} products", tenant, products.size());
            } catch (Exception e) {
                log.warn("⚠️ Failed to fetch products for tenant '{}': {}", tenant, e.getMessage());
                tenantProducts.put(tenant, new ArrayList<>());
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
            return serviceTenantUtil.executeInTenant(targetTenant, () -> {
                AppUser createdUser = appUserService.createUser(userDto);
                log.info("✅ Successfully created user '{}' in tenant '{}'", createdUser.getUsername(), targetTenant);
                return createdUser;
            });
        } catch (Exception e) {
            log.error("❌ Failed to create user '{}' in tenant '{}': {}", userDto.getUsername(), targetTenant, e.getMessage());
            throw new RuntimeException("Failed to create user in tenant " + targetTenant, e);
        }
    }

    /**
     * Delete a user from a specific tenant (SUPER_ADMIN only)
     */
    @Transactional
    public void deleteUserFromTenant(String targetTenant, Long userId) {
        log.info("🗑️ Super Admin: Deleting user '{}' from tenant '{}'", userId, targetTenant);
        
        try {
            serviceTenantUtil.executeInTenant(targetTenant, () -> {
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
                return null;
            });
        } catch (Exception e) {
            log.error("❌ Failed to delete user '{}' from tenant '{}': {}", userId, targetTenant, e.getMessage());
            throw new RuntimeException("Failed to delete user from tenant " + targetTenant, e);
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
        serviceTenantUtil.setCurrentTenant(targetTenant);
    }

    /**
     * Get tenant statistics (SUPER_ADMIN only)
     * Note: SUPER_ADMIN users are only counted for the 'public' tenant
     */
    @Transactional(readOnly = true)
    public Map<String, Map<String, Object>> getTenantStatistics() {
        log.info("📊 Super Admin: Generating tenant statistics");
        
        Map<String, Map<String, Object>> tenantStats = new HashMap<>();
        
        for (String tenant : getAllTenants()) {
            try {
                Map<String, Object> stats = serviceTenantUtil.executeInTenant(tenant, () -> {
                    Map<String, Object> tenantData = new HashMap<>();
                    
                    // Calculate user count - only show SUPER_ADMIN for public tenant
                    long userCount;
                    long activeUserCount;
                    
                    if ("public".equals(tenant)) {
                        // For public tenant, include all users including SUPER_ADMIN
                        userCount = appUserRepository.count();
                        activeUserCount = appUserRepository.countByIsActive(true);
                        log.debug("📊 Tenant '{}' (public): All user count {} (including SUPER_ADMIN)", tenant, userCount);
                    } else {
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
                    
                    tenantData.put("userCount", userCount);
                    tenantData.put("activeUserCount", activeUserCount);
                    tenantData.put("productCount", productRepository.count());
                    tenantData.put("totalStockValue", calculateTotalStockValue());
                    tenantData.put("lowStockProductCount", productRepository.countLowStockProducts());
                    
                    // Add contact message statistics
                    long totalContactMessages = contactMessageRepository.count();
                    long unreadContactMessages = contactMessageRepository.countByIsReadFalse();
                    tenantData.put("totalContactMessages", totalContactMessages);
                    tenantData.put("unreadContactMessages", unreadContactMessages);
                    
                    return tenantData;
                });
                
                tenantStats.put(tenant, stats);
                log.debug("📈 Tenant '{}' stats: {} users, {} products", tenant, stats.get("userCount"), stats.get("productCount"));
                
            } catch (Exception e) {
                log.warn("⚠️ Failed to calculate stats for tenant '{}': {}", tenant, e.getMessage());
                Map<String, Object> errorStats = new HashMap<>();
                errorStats.put("error", "Failed to calculate statistics");
                tenantStats.put(tenant, errorStats);
            }
        }
        
        log.info("✅ Generated statistics for {} tenants (SUPER_ADMIN only counted in public)", tenantStats.size());
        return tenantStats;
    }

    /**
     * Get users by role across all tenants (SUPER_ADMIN only)
     */
    @Transactional(readOnly = true)
    public Map<String, Map<Role, List<AppUser>>> getUsersByRoleAcrossAllTenants() {
        log.info("👥 Super Admin: Fetching users by role across all tenants");
        
        Map<String, Map<Role, List<AppUser>>> result = new HashMap<>();
        
        for (String tenant : getAllTenants()) {
            try {
                Map<Role, List<AppUser>> usersByRole = serviceTenantUtil.executeInTenant(tenant, () -> {
                    List<AppUser> allUsers = appUserRepository.findAll();
                    return allUsers.stream()
                        .collect(Collectors.groupingBy(AppUser::getRole));
                });
                
                result.put(tenant, usersByRole);
                
            } catch (Exception e) {
                log.warn("⚠️ Failed to fetch users by role for tenant '{}': {}", tenant, e.getMessage());
                result.put(tenant, new HashMap<>());
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
            serviceTenantUtil.executeInTenant(targetTenant, () -> {
                AppUser user = appUserRepository.findById(userId)
                    .orElseThrow(() -> new IllegalArgumentException("User not found with ID: " + userId));
                
                // Prevent deactivating SUPER_ADMIN users
                if (Role.SUPER_ADMIN.equals(user.getRole()) && !isActive) {
                    throw new IllegalArgumentException("Cannot deactivate SUPER_ADMIN users");
                }
                
                user.setIsActive(isActive);
                appUserRepository.save(user);
                
                return null;
            });
            
            log.info("✅ Successfully {} user '{}' in tenant '{}'", 
                    isActive ? "activated" : "deactivated", userId, targetTenant);
            
        } catch (Exception e) {
            log.error("❌ Failed to toggle status for user '{}' in tenant '{}': {}", userId, targetTenant, e.getMessage());
            throw new RuntimeException("Failed to toggle user status in tenant " + targetTenant, e);
        }
    }

    /**
     * Get available tenants for the super admin
     */
    public Set<String> getAvailableTenants() {
        return getAllTenants();
    }

    /**
     * Calculate total stock value for current tenant
     */
    private BigDecimal calculateTotalStockValue() {
        List<Product> products = productRepository.findAll();
        return products.stream()
            .map(product -> {
                BigDecimal price = product.getPrice() != null ? product.getPrice() : BigDecimal.ZERO;
                Integer quantity = product.getStockLevel() != null ? product.getStockLevel() : 0;
                return price.multiply(new BigDecimal(quantity));
            })
            .reduce(BigDecimal.ZERO, BigDecimal::add);
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
                List<ContactMessage> messages = serviceTenantUtil.executeInTenant(tenant, () -> 
                    contactMessageRepository.findAllByOrderByCreatedAtDesc()
                );
                tenantContactMessages.put(tenant, messages);
            } catch (Exception e) {
                log.warn("⚠️ Failed to fetch contact messages for tenant '{}': {}", tenant, e.getMessage());
                tenantContactMessages.put(tenant, new ArrayList<>());
            }
        }
        
        return tenantContactMessages;
    }

    /**
     * Mark a contact message as read in a specific tenant (SUPER_ADMIN only)
     */
    @Transactional
    public void markContactMessageAsRead(String targetTenant, Long messageId, boolean isRead) {
        log.info("📧 Super Admin: Marking contact message {} as {} in tenant '{}'", 
                messageId, isRead ? "read" : "unread", targetTenant);
        
        try {
            serviceTenantUtil.executeInTenant(targetTenant, () -> {
                ContactMessage message = contactMessageRepository.findById(messageId)
                    .orElseThrow(() -> new IllegalArgumentException("Contact message not found with ID: " + messageId));
                
                message.setIsRead(isRead);
                contactMessageRepository.save(message);
                
                log.info("✅ Successfully {} contact message '{}' in tenant '{}'", 
                        isRead ? "marked as read" : "marked as unread", messageId, targetTenant);
                return null;
            });
        } catch (Exception e) {
            log.error("❌ Failed to mark contact message as {} in tenant '{}': {}", 
                    isRead ? "read" : "unread", targetTenant, e.getMessage());
            throw new RuntimeException("Failed to mark contact message as " + (isRead ? "read" : "unread") + " in tenant " + targetTenant, e);
        }
    }

    /**
     * Delete a contact message from a specific tenant (SUPER_ADMIN only)
     */
    @Transactional
    public void deleteContactMessage(String targetTenant, Long messageId) {
        log.info("🗑️ Super Admin: Deleting contact message {} from tenant '{}'", messageId, targetTenant);
        
        try {
            serviceTenantUtil.executeInTenant(targetTenant, () -> {
                contactMessageRepository.deleteById(messageId);
                log.info("✅ Successfully deleted contact message {} from tenant '{}'", messageId, targetTenant);
                return null;
            });
        } catch (Exception e) {
            log.error("❌ Failed to delete contact message from tenant '{}': {}", targetTenant, e.getMessage());
            throw new RuntimeException("Failed to delete contact message from tenant " + targetTenant, e);
        }
    }

    /**
     * Clear tenant context (SUPER_ADMIN only)
     */
    public void clearTenantContext() {
        serviceTenantUtil.clearCurrentTenant();
    }

    /**
     * Get all tenant subscription plans (SUPER_ADMIN only)
     */
    @Transactional(readOnly = true)
    public Map<String, String> getAllTenantSubscriptionPlans() {
        log.info("💳 Super Admin: Fetching subscription plans for all tenants");
        
        Map<String, String> tenantPlans = new HashMap<>();
        
        for (String tenant : getAllTenants()) {
            try {
                String plan = serviceTenantUtil.executeInTenant(tenant, () -> {
                    // Get subscription plan from tenant config
                    try (Connection connection = dataSource.getConnection()) {
                        connection.setSchema(tenant.toLowerCase());
                        
                        String sql = "SELECT config_value FROM tenant_config WHERE config_key = ?";
                        try (PreparedStatement stmt = connection.prepareStatement(sql)) {
                            stmt.setString(1, "subscription_plan");
                            try (ResultSet rs = stmt.executeQuery()) {
                                if (rs.next()) {
                                    return rs.getString("config_value");
                                }
                            }
                        }
                    } catch (SQLException e) {
                        log.warn("⚠️ Could not get subscription plan for tenant '{}': {}", tenant, e.getMessage());
                    }
                    return "TRIAL"; // Default fallback
                });
                
                tenantPlans.put(tenant, plan != null ? plan.toUpperCase() : "TRIAL");
                log.debug("📊 Tenant '{}': Plan = '{}'", tenant, plan);
                
            } catch (Exception e) {
                log.warn("⚠️ Failed to fetch subscription plan for tenant '{}': {}", tenant, e.getMessage());
                tenantPlans.put(tenant, "TRIAL"); // Default fallback
            }
        }
        
        log.info("✅ Successfully retrieved subscription plans for {} tenants", tenantPlans.size());
        return tenantPlans;
    }

    /**
     * Update tenant subscription plan (SUPER_ADMIN only)
     */
    @Transactional
    public void updateTenantSubscriptionPlan(String targetTenant, String subscriptionPlan) {
        log.info("🔄 Super Admin: Updating subscription plan for tenant '{}' to '{}'", 
                targetTenant, subscriptionPlan);
        
        try {
            serviceTenantUtil.executeInTenant(targetTenant, () -> {
                // Use SubscriptionService to update the plan which handles all the configuration
                subscriptionService.setTenantPlan(targetTenant, subscriptionPlan);
                return null;
            });
            
            log.info("✅ Successfully updated subscription plan for tenant '{}' to '{}'", 
                    targetTenant, subscriptionPlan);
            
        } catch (Exception e) {
            log.error("❌ Failed to update subscription plan for tenant '{}': {}", targetTenant, e.getMessage());
            throw new RuntimeException("Failed to update subscription plan for tenant " + targetTenant, e);
        }
    }

    /**
     * Get contact message statistics across all tenants (SUPER_ADMIN only)
     */
    @Transactional(readOnly = true)
    public Map<String, Object> getContactMessageStatistics() {
        log.info("📊 Super Admin: Fetching contact message statistics across all tenants");
        
        Map<String, Object> stats = new HashMap<>();
        int totalMessages = 0;
        int unreadMessages = 0;
        int readMessages = 0;
        int respondedMessages = 0;
        
        for (String tenant : getAllTenants()) {
            try {
                Map<String, Integer> tenantStats = serviceTenantUtil.executeInTenant(tenant, () -> {
                    List<ContactMessage> messages = contactMessageRepository.findAll();
                    
                    int total = messages.size();
                    int unread = (int) messages.stream().filter(msg -> !msg.getIsRead()).count();
                    int read = (int) messages.stream().filter(ContactMessage::getIsRead).count();
                    int responded = (int) messages.stream().filter(ContactMessage::getResponded).count();
                    
                    Map<String, Integer> tenantStat = new HashMap<>();
                    tenantStat.put("total", total);
                    tenantStat.put("unread", unread);
                    tenantStat.put("read", read);
                    tenantStat.put("responded", responded);
                    
                    return tenantStat;
                });
                
                totalMessages += tenantStats.get("total");
                unreadMessages += tenantStats.get("unread");
                readMessages += tenantStats.get("read");
                respondedMessages += tenantStats.get("responded");
                
            } catch (Exception e) {
                log.warn("⚠️ Failed to fetch contact message stats for tenant '{}': {}", tenant, e.getMessage());
            }
        }
        
        stats.put("totalMessages", totalMessages);
        stats.put("unreadMessages", unreadMessages);
        stats.put("readMessages", readMessages);
        stats.put("respondedMessages", respondedMessages);
        
        log.info("✅ Contact message statistics: Total={}, Unread={}, Read={}, Responded={}", 
                totalMessages, unreadMessages, readMessages, respondedMessages);
        
        return stats;
    }
}
