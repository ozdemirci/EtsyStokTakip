package dev.oasis.stockify.service;

import dev.oasis.stockify.model.PlanType;
import dev.oasis.stockify.util.ServiceTenantUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.Locale;

/**
 * Service for managing subscription plans and limits
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class SubscriptionService {
    
    private final DataSource dataSource;
    private final AppUserService appUserService;
    private final ServiceTenantUtil serviceTenantUtil;
    
    /**
     * Set subscription plan for tenant
     */
    public void setTenantPlan(String tenantId, String planCode) {
        PlanType plan = PlanType.fromCode(planCode);
        
        try (Connection connection = dataSource.getConnection()) {
            connection.setSchema(tenantId.toLowerCase(Locale.ROOT));
            
            // Set plan configuration
            setTenantConfig(connection, "subscription_plan", plan.getCode());
            setTenantConfig(connection, "max_users", String.valueOf(plan.getMaxUsers()));
            setTenantConfig(connection, "max_products", String.valueOf(plan.getMaxProducts()));
            setTenantConfig(connection, "trial_days", String.valueOf(plan.getTrialDays()));
            
            // Set trial expiry date if applicable
            if (plan.getTrialDays() > 0) {
                LocalDateTime expiryDate = LocalDateTime.now().plusDays(plan.getTrialDays());
                setTenantConfig(connection, "trial_expiry", expiryDate.toString());
                setTenantConfig(connection, "trial_active", "true");
            } else {
                setTenantConfig(connection, "trial_active", "false");
            }
            
            log.info("✅ Set subscription plan {} for tenant {}", plan.getCode(), tenantId);
            
        } catch (SQLException e) {
            log.error("❌ Failed to set subscription plan for tenant {}: {}", tenantId, e.getMessage());
            throw new RuntimeException("Failed to set subscription plan", e);
        }
    }
    
    /**
     * Check if tenant can create more users
     */
    public boolean canCreateUser() {
        String tenantId = serviceTenantUtil.getCurrentTenant();
        if (tenantId == null) {
            return false;
        }
        
        try {
            // Get max users from config
            int maxUsers = Integer.parseInt(getTenantConfig("max_users", "1"));
            
            // Count current users
            long currentUsers = appUserService.countActiveUsers();
            
            log.debug("Tenant {}: Current users: {}, Max users: {}", tenantId, currentUsers, maxUsers);
            
            if (maxUsers == -1) {
                return true; // Unlimited
            }
            
            return currentUsers < maxUsers;
            
        } catch (Exception e) {
            log.error("❌ Error checking user limit for tenant {}: {}", tenantId, e.getMessage());
            return false;
        }
    }
    
    /**
     * Check if tenant can create more products
     */
    public boolean canCreateProduct() {
        String tenantId = serviceTenantUtil.getCurrentTenant();
        if (tenantId == null) {
            return false;
        }
        
        try {
            // Get max products from config
            int maxProducts = Integer.parseInt(getTenantConfig("max_products", "100"));
            
            // Count current products
            long currentProducts = countProducts();
            
            log.debug("Tenant {}: Current products: {}, Max products: {}", tenantId, currentProducts, maxProducts);
            
            if (maxProducts == -1) {
                return true; // Unlimited
            }
            
            return currentProducts < maxProducts;
            
        } catch (Exception e) {
            log.error("❌ Error checking product limit for tenant {}: {}", tenantId, e.getMessage());
            return false;
        }
    }
    
    /**
     * Check if tenant trial has expired
     */
    public boolean isTrialExpired() {
        String tenantId = serviceTenantUtil.getCurrentTenant();
        if (tenantId == null) {
            return true;
        }
        
        try {
            String trialActive = getTenantConfig("trial_active", "false");
            if (!"true".equals(trialActive)) {
                return false; // Not a trial account
            }
            
            String trialExpiryStr = getTenantConfig("trial_expiry", null);
            if (trialExpiryStr == null) {
                return false;
            }
            
            LocalDateTime trialExpiry = LocalDateTime.parse(trialExpiryStr);
            boolean expired = LocalDateTime.now().isAfter(trialExpiry);
            
            if (expired) {
                log.warn("⚠️ Trial expired for tenant {}: {}", tenantId, trialExpiry);
            }
            
            return expired;
            
        } catch (Exception e) {
            log.error("❌ Error checking trial expiry for tenant {}: {}", tenantId, e.getMessage());
            return false;
        }
    }
    
    /**
     * Get tenant's current plan
     */
    public PlanType getTenantPlan() {
        String planCode = getTenantConfig("subscription_plan", "trial");
        return PlanType.fromCode(planCode);
    }
    
    /**
     * Get remaining trial days
     */
    public long getRemainingTrialDays() {
        try {
            String trialActive = getTenantConfig("trial_active", "false");
            if (!"true".equals(trialActive)) {
                return -1; // Not a trial account
            }
            
            String trialExpiryStr = getTenantConfig("trial_expiry", null);
            if (trialExpiryStr == null) {
                return -1;
            }
            
            LocalDateTime trialExpiry = LocalDateTime.parse(trialExpiryStr);
            LocalDateTime now = LocalDateTime.now();
            
            if (now.isAfter(trialExpiry)) {
                return 0; // Expired
            }
            
            return java.time.Duration.between(now, trialExpiry).toDays();
            
        } catch (Exception e) {
            log.error("❌ Error getting remaining trial days: {}", e.getMessage());
            return -1;
        }
    }
    
    // Private helper methods
    
    private void setTenantConfig(Connection connection, String key, String value) throws SQLException {
        String sql = """
            INSERT INTO tenant_config (config_key, config_value, config_type, description) 
            VALUES (?, ?, 'STRING', ?) 
            ON CONFLICT (config_key) 
            DO UPDATE SET config_value = EXCLUDED.config_value, updated_at = CURRENT_TIMESTAMP
            """;
        
        try (PreparedStatement stmt = connection.prepareStatement(sql)) {
            stmt.setString(1, key);
            stmt.setString(2, value);
            stmt.setString(3, "Subscription plan configuration");
            stmt.executeUpdate();
        }
    }
    
    private String getTenantConfig(String key, String defaultValue) {
        String tenantId = serviceTenantUtil.getCurrentTenant();
        if (tenantId == null) {
            return defaultValue;
        }
        
        try (Connection connection = dataSource.getConnection()) {
            connection.setSchema(tenantId.toLowerCase(Locale.ROOT));
            
            String sql = "SELECT config_value FROM tenant_config WHERE config_key = ?";
            try (PreparedStatement stmt = connection.prepareStatement(sql)) {
                stmt.setString(1, key);
                try (ResultSet rs = stmt.executeQuery()) {
                    if (rs.next()) {
                        return rs.getString("config_value");
                    }
                }
            }
        } catch (SQLException e) {
            log.error("❌ Error getting tenant config {}: {}", key, e.getMessage());
        }
        
        return defaultValue;
    }
    
    private long countProducts() {
        String tenantId = serviceTenantUtil.getCurrentTenant();
        if (tenantId == null) {
            return 0;
        }
        
        try (Connection connection = dataSource.getConnection()) {
            connection.setSchema(tenantId.toLowerCase(Locale.ROOT));
            
            String sql = "SELECT COUNT(*) FROM product";
            try (PreparedStatement stmt = connection.prepareStatement(sql);
                 ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    return rs.getLong(1);
                }
            }
        } catch (SQLException e) {
            log.error("❌ Error counting products: {}", e.getMessage());
        }
        
        return 0;
    }
}
