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
        try (Connection connection = dataSource.getConnection()) {
            String sql = "SELECT config_value FROM tenant_config WHERE config_key = ?";
            try (PreparedStatement stmt = connection.prepareStatement(sql)) {
                stmt.setString(1, configKey);
                try (ResultSet rs = stmt.executeQuery()) {
                    if (rs.next()) {
                        String value = rs.getString("config_value");
                        log.debug("📊 Retrieved config '{}' = '{}'", configKey, value);
                        return value;
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
        Map<String, String> config = new HashMap<>();
        
        try (Connection connection = dataSource.getConnection()) {
            String sql = "SELECT config_key, config_value FROM tenant_config";
            try (PreparedStatement stmt = connection.prepareStatement(sql);
                 ResultSet rs = stmt.executeQuery()) {
                
                while (rs.next()) {
                    String key = rs.getString("config_key");
                    String value = rs.getString("config_value");
                    config.put(key, value);
                }
            }
            
            log.debug("📊 Retrieved {} config entries", config.size());
            
        } catch (SQLException e) {
            log.error("❌ Failed to get all config: {}", e.getMessage());
        }
        
        return config;
    }

    /**
     * Get current tenant's subscription plan
     */
    public String getSubscriptionPlan() {
        return getConfigValue("subscription_plan", "TRIAL");
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
        try (Connection connection = dataSource.getConnection()) {
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
                        log.info("✅ Updated config '{}' = '{}'", configKey, configValue);
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
                        log.info("✅ Inserted new config '{}' = '{}'", configKey, configValue);
                    }
                }
            }
            
        } catch (SQLException e) {
            log.error("❌ Failed to update config '{}' = '{}': {}", configKey, configValue, e.getMessage());
            throw new RuntimeException("Failed to update tenant configuration", e);
        }
    }
}
