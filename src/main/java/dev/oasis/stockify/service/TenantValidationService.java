package dev.oasis.stockify.service;

import dev.oasis.stockify.config.tenant.TenantContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Locale;

/**
 * Service for validating tenant access and permissions
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TenantValidationService {

    private final DataSource dataSource;

    /**
     * Validate if current user has access to the current tenant
     */    public boolean validateTenantAccess(String username) {
        String currentTenant = TenantContext.getCurrentTenant();
        
        if (currentTenant == null || currentTenant.isEmpty() || "public".equalsIgnoreCase(currentTenant)) {
            return true; // Allow access to public tenant
        }

        try {
            return userExistsInTenant(username, currentTenant);
        } catch (Exception e) {
            log.error("❌ Error validating tenant access for user {} in tenant {}: {}", 
                     username, currentTenant, e.getMessage());
            return false;
        }
    }

    /**
     * Check if tenant is active and accessible
     */    public boolean isTenantActive(String tenantId) {
        if ("public".equalsIgnoreCase(tenantId)) {
            return true;
        }        try (Connection connection = dataSource.getConnection()) {
            connection.setSchema(tenantId.toLowerCase(Locale.ROOT));
            
            String query = """
                SELECT config_value FROM tenant_config 
                WHERE config_key = 'tenant_status'
                """;
            
            try (PreparedStatement stmt = connection.prepareStatement(query);
                 ResultSet rs = stmt.executeQuery()) {
                
                if (rs.next()) {
                    String status = rs.getString("config_value");
                    return "ACTIVE".equals(status);
                }
            }
        } catch (SQLException e) {
            log.error("❌ Error checking tenant status for {}: {}", tenantId, e.getMessage());
        }
        
        return false;
    }

    /**
     * Get tenant display name
     */    public String getTenantDisplayName(String tenantId) {
        if ("public".equalsIgnoreCase(tenantId)) {
            return "Public";
        }        try (Connection connection = dataSource.getConnection()) {
            connection.setSchema(tenantId.toLowerCase(Locale.ROOT));
            
            String query = """
                SELECT config_value FROM tenant_config 
                WHERE config_key = 'company_name'
                """;
            
            try (PreparedStatement stmt = connection.prepareStatement(query);
                 ResultSet rs = stmt.executeQuery()) {
                
                if (rs.next()) {
                    return rs.getString("config_value");
                }
            }
        } catch (SQLException e) {
            log.debug("Could not get display name for tenant {}: {}", tenantId, e.getMessage());
        }
        
        return tenantId; // Fallback to tenant ID
    }

    /**
     * Validate tenant exists and schema is properly set up
     */
    public boolean validateTenantSchema(String tenantId) {        try (Connection connection = dataSource.getConnection()) {
            // Check if schema exists
            connection.setSchema(tenantId.toLowerCase(Locale.ROOT));
            
            // Check if required tables exist
            String[] requiredTables = {"app_user", "product", "stock_notification", "tenant_config"};
            
            for (String tableName : requiredTables) {
                if (!tableExists(connection, tableName)) {
                    log.warn("⚠️ Required table {} not found in tenant schema {}", tableName, tenantId);
                    return false;
                }
            }
            
            return true;
            
        } catch (SQLException e) {
            log.error("❌ Error validating tenant schema for {}: {}", tenantId, e.getMessage());
            return false;
        }
    }

    private boolean userExistsInTenant(String username, String tenantId) throws SQLException {        try (Connection connection = dataSource.getConnection()) {
            connection.setSchema(tenantId.toLowerCase(Locale.ROOT));
            
            String query = """
                SELECT COUNT(*) FROM app_user 
                WHERE username = ? AND is_active = true
                """;
            
            try (PreparedStatement stmt = connection.prepareStatement(query)) {
                stmt.setString(1, username);
                
                try (ResultSet rs = stmt.executeQuery()) {
                    if (rs.next()) {
                        return rs.getInt(1) > 0;
                    }
                }
            }
        }
        
        return false;
    }

    private boolean tableExists(Connection connection, String tableName) {
        try (PreparedStatement stmt = connection.prepareStatement(
                "SELECT COUNT(*) FROM INFORMATION_SCHEMA.TABLES WHERE TABLE_NAME = ?")) {
            stmt.setString(1, tableName.toUpperCase(Locale.ROOT));
            
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    return rs.getInt(1) > 0;
                }
            }
        } catch (SQLException e) {
            log.debug("Error checking table existence for {}: {}", tableName, e.getMessage());
        }
        
        return false;
    }
}
