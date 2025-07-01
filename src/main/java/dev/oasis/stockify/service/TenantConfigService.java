package dev.oasis.stockify.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.Map;

/**
 * Service for managing tenant configuration
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TenantConfigService {

    private final DataSource dataSource;

    /**
     * Get tenant configuration value by key
     */
    public String getConfigValue(String configKey) {
        return getConfigValue(configKey, null);
    }

    /**
     * Get tenant configuration value by key with default value
     */
    public String getConfigValue(String configKey, String defaultValue) {
        String tenantId = getCurrentTenantFromContext();
        log.debug("🔍 Getting config '{}' for tenant: '{}'", configKey, tenantId);
        
        try (Connection connection = dataSource.getConnection()) {
            // CRITICAL: Tenant schema'sını açıkça ayarla
            if (tenantId != null && !tenantId.isEmpty() && !"unknown".equals(tenantId)) {
                String schema = tenantId.toLowerCase();
                connection.setSchema(schema);
                log.info("🔧 FORCED connection schema to: '{}' for tenant: '{}'", schema, tenantId);
            }
            
            // Log current schema
            String currentSchema = connection.getSchema();
            log.info("📊 Current DB schema: '{}' for config key: '{}'", currentSchema, configKey);
            
            String sql = "SELECT config_value FROM tenant_config WHERE config_key = ?";
            try (PreparedStatement stmt = connection.prepareStatement(sql)) {
                stmt.setString(1, configKey);
                try (ResultSet rs = stmt.executeQuery()) {
                    if (rs.next()) {
                        String value = rs.getString("config_value");
                        log.info("✅ Retrieved config '{}' = '{}' from schema: '{}'", configKey, value, currentSchema);
                        return value;
                    } else {
                        log.warn("⚠️ No value found for config key '{}' in schema '{}', using default: '{}'", 
                                configKey, currentSchema, defaultValue);
                    }
                }
            }
        } catch (SQLException e) {
            log.error("❌ Failed to get config value for key '{}': {}", configKey, e.getMessage());
        }
        
        log.debug("📊 Config '{}' not found, returning default: '{}'", configKey, defaultValue);
        return defaultValue;
    }

    /**
     * Get all tenant configuration as a map
     */
    public Map<String, String> getAllConfig() {
        String tenantId = getCurrentTenantFromContext();
        Map<String, String> config = new HashMap<>();
        
        try (Connection connection = dataSource.getConnection()) {
            // CRITICAL: Tenant schema'sını açıkça ayarla
            if (tenantId != null && !tenantId.isEmpty() && !"unknown".equals(tenantId)) {
                String schema = tenantId.toLowerCase();
                connection.setSchema(schema);
                log.info("🔧 FORCED connection schema to: '{}' for tenant: '{}'", schema, tenantId);
            }
            
            String sql = "SELECT config_key, config_value FROM tenant_config";
            try (PreparedStatement stmt = connection.prepareStatement(sql);
                 ResultSet rs = stmt.executeQuery()) {
                
                while (rs.next()) {
                    String key = rs.getString("config_key");
                    String value = rs.getString("config_value");
                    config.put(key, value);
                }
            }
            
            log.debug("📊 Retrieved {} config entries from schema: '{}'", config.size(), connection.getSchema());
            
        } catch (SQLException e) {
            log.error("❌ Failed to get all config: {}", e.getMessage());
        }
        
        return config;
    }

    /**
     * Get current tenant's subscription plan
     */
    public String getSubscriptionPlan() {
        String tenantId = getCurrentTenantFromContext();
        log.info("🔍 Getting subscription plan for tenant: {}", tenantId);
        
        String plan = getConfigValue("subscription_plan", "TRIAL");
        log.info("📊 Retrieved subscription plan: '{}' for tenant: '{}'", plan, tenantId);
        
        return plan;
    }
    
    /**
     * Get current tenant ID from context for debugging
     */
    private String getCurrentTenantFromContext() {
        try {
            return dev.oasis.stockify.config.tenant.TenantContext.getCurrentTenant();
        } catch (Exception e) {
            log.warn("Could not get tenant from context: {}", e.getMessage());
            return "unknown";
        }
    }

    /**
     * Get current tenant's company name
     */
    public String getCompanyName() {
        return getConfigValue("company_name", "Company");
    }

    /**
     * Get current tenant's admin email
     */
    public String getAdminEmail() {
        return getConfigValue("admin_email", "");
    }

    /**
     * Get current tenant's status
     */
    public String getTenantStatus() {
        return getConfigValue("tenant_status", "ACTIVE");
    }

    /**
     * Update tenant configuration value
     */
    public void updateConfigValue(String configKey, String configValue) {
        String tenantId = getCurrentTenantFromContext();
        log.info("🔄 Updating config '{}' = '{}' for tenant: '{}'", configKey, configValue, tenantId);
        
        try (Connection connection = dataSource.getConnection()) {
            // CRITICAL: Tenant schema'sını açıkça ayarla
            if (tenantId != null && !tenantId.isEmpty() && !"unknown".equals(tenantId)) {
                String schema = tenantId.toLowerCase();
                connection.setSchema(schema);
                log.info("🔧 FORCED connection schema to: '{}' for tenant: '{}'", schema, tenantId);
            }
            
            // First check if config exists
            String checkSql = "SELECT id FROM tenant_config WHERE config_key = ?";
            boolean exists = false;
            
            try (PreparedStatement checkStmt = connection.prepareStatement(checkSql)) {
                checkStmt.setString(1, configKey);
                try (ResultSet rs = checkStmt.executeQuery()) {
                    exists = rs.next();
                }
            }
            
            if (exists) {
                // Update existing config
                String updateSql = "UPDATE tenant_config SET config_value = ?, updated_at = CURRENT_TIMESTAMP WHERE config_key = ?";
                try (PreparedStatement stmt = connection.prepareStatement(updateSql)) {
                    stmt.setString(1, configValue);
                    stmt.setString(2, configKey);
                    int updated = stmt.executeUpdate();
                    if (updated > 0) {
                        log.info("✅ Updated config '{}' = '{}' in schema: '{}'", configKey, configValue, connection.getSchema());
                    }
                }
            } else {
                // Insert new config
                String insertSql = "INSERT INTO tenant_config (config_key, config_value, config_type, description, created_at, updated_at) VALUES (?, ?, ?, ?, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)";
                try (PreparedStatement stmt = connection.prepareStatement(insertSql)) {
                    stmt.setString(1, configKey);
                    stmt.setString(2, configValue);
                    stmt.setString(3, "STRING");
                    stmt.setString(4, "Configuration value for " + configKey);
                    int inserted = stmt.executeUpdate();
                    if (inserted > 0) {
                        log.info("✅ Inserted new config '{}' = '{}' in schema: '{}'", configKey, configValue, connection.getSchema());
                    }
                }
            }
            
        } catch (SQLException e) {
            log.error("❌ Failed to update config '{}' = '{}': {}", configKey, configValue, e.getMessage());
            throw new RuntimeException("Failed to update tenant configuration", e);
        }
    }
}
