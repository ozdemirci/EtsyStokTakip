package dev.oasis.stockify.service;

import dev.oasis.stockify.config.tenant.TenantContext;
import dev.oasis.stockify.dto.TenantCreateDTO;
import dev.oasis.stockify.dto.TenantDTO;
import dev.oasis.stockify.dto.UserCreateDTO;
import dev.oasis.stockify.exception.TenantAlreadyExistsException;
import dev.oasis.stockify.exception.TenantNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

/**
 * Service for managing tenant lifecycle operations
 * Handles tenant creation, activation, deactivation, and schema management
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TenantManagementService {

    private final DataSource dataSource;
    private final AppUserService appUserService;

    /**
     * Create a new tenant with complete setup
     */
    @Transactional
    public TenantDTO createTenant(TenantCreateDTO createDTO) {
        String tenantId = generateTenantId(createDTO.getCompanyName());
        
        log.info("🏢 Creating new tenant: {} for company: {}", tenantId, createDTO.getCompanyName());
        
        try {
            // Check if tenant already exists
            if (tenantExists(tenantId)) {
                throw new TenantAlreadyExistsException("Tenant already exists: " + tenantId);
            }
            
            // Create tenant schema
            createTenantSchema(tenantId);
            
            // Set tenant context for data operations
            TenantContext.setCurrentTenant(tenantId);
            
            // Create tenant configuration
            setupTenantConfiguration(tenantId, createDTO);
            
            // Create initial admin user
            createTenantAdmin(createDTO);
            
            // Create default configurations
            setupDefaultConfigurations(tenantId, createDTO);
            
            log.info("✅ Successfully created tenant: {}", tenantId);
            
            return TenantDTO.builder()
                    .tenantId(tenantId)
                    .companyName(createDTO.getCompanyName())
                    .adminEmail(createDTO.getAdminEmail())
                    .status("ACTIVE")
                    .createdAt(LocalDateTime.now())
                    .build();
                    
        } catch (Exception e) {
            log.error("❌ Failed to create tenant {}: {}", tenantId, e.getMessage(), e);
            // Cleanup on failure
            cleanupFailedTenant(tenantId);
            throw new RuntimeException("Failed to create tenant: " + e.getMessage(), e);
        } finally {
            TenantContext.clear();
        }
    }

    /**
     * Get all active tenants
     */
    public List<TenantDTO> getAllTenants() {
        List<TenantDTO> tenants = new ArrayList<>();
        
        try (Connection connection = dataSource.getConnection();
             Statement statement = connection.createStatement()) {
            
            // Check if master tenant registry exists
            ResultSet schemas = connection.getMetaData().getSchemas();
            while (schemas.next()) {
                String schemaName = schemas.getString("TABLE_SCHEM");
                if (!isSystemSchema(schemaName) && !schemaName.equalsIgnoreCase("public")) {
                    TenantDTO tenant = getTenantInfo(schemaName.toLowerCase());
                    if (tenant != null) {
                        tenants.add(tenant);
                    }
                }
            }
            
        } catch (SQLException e) {
            log.error("❌ Failed to retrieve tenants: {}", e.getMessage());
            throw new RuntimeException("Failed to retrieve tenants", e);
        }
        
        return tenants;
    }

    /**
     * Get tenant information by ID
     */
    public TenantDTO getTenant(String tenantId) {
        if (!tenantExists(tenantId)) {
            throw new TenantNotFoundException("Tenant not found: " + tenantId);
        }
        
        return getTenantInfo(tenantId);
    }    /**
     * Deactivate a tenant (soft delete)
     */
    @Transactional
    public void deactivateTenant(String tenantId) {
        log.info("🔒 Deactivating tenant: {}", tenantId);
        
        try {
            TenantContext.setCurrentTenant(tenantId);
            updateTenantStatus(tenantId, "INACTIVE");
            log.info("✅ Successfully deactivated tenant: {}", tenantId);
        } catch (SQLException e) {
            log.error("❌ Failed to deactivate tenant: {}", e.getMessage());
            throw new RuntimeException("Failed to deactivate tenant: " + tenantId, e);
        } finally {
            TenantContext.clear();
        }
    }    /**
     * Activate a tenant
     */
    @Transactional
    public void activateTenant(String tenantId) {
        log.info("🔓 Activating tenant: {}", tenantId);
        
        try {
            TenantContext.setCurrentTenant(tenantId);
            updateTenantStatus(tenantId, "ACTIVE");
            log.info("✅ Successfully activated tenant: {}", tenantId);
        } catch (SQLException e) {
            log.error("❌ Failed to activate tenant: {}", e.getMessage());
            throw new RuntimeException("Failed to activate tenant: " + tenantId, e);
        } finally {
            TenantContext.clear();
        }
    }

    /**
     * Check if tenant exists
     */    public boolean tenantExists(String tenantId) {
        try (Connection connection = dataSource.getConnection()) {
            String schemaName = tenantId.toLowerCase(Locale.ROOT);
            ResultSet schemas = connection.getMetaData().getSchemas();
            while (schemas.next()) {
                if (schemaName.equals(schemas.getString("TABLE_SCHEM"))) {
                    return true;
                }
            }
            return false;
        } catch (SQLException e) {
            log.error("❌ Error checking tenant existence: {}", e.getMessage());
            return false;
        }
    }

    // Private helper methods

    private String generateTenantId(String companyName) {
        // Generate tenant ID based on company name
        String sanitized = companyName.toLowerCase()
                .replaceAll("[^a-zA-Z0-9]", "");
        String baseId = sanitized.substring(0, Math.min(sanitized.length(), 10));

        // Add random suffix to ensure uniqueness
        String suffix = UUID.randomUUID().toString().substring(0, 4);
        return baseId + "_" + suffix;
    }

    private void createTenantSchema(String tenantId) throws SQLException {
        try (Connection connection = dataSource.getConnection();
             Statement statement = connection.createStatement()) {
            
            String schemaName = tenantId.toLowerCase(Locale.ROOT);
            
            // Create schema only - Flyway handles table creation
            statement.execute(String.format("CREATE SCHEMA IF NOT EXISTS %s", schemaName));
            log.debug("🏗️ Created schema: {} (tables will be created by Flyway)", schemaName);
        }
    }

    private void setupTenantConfiguration(String tenantId, TenantCreateDTO createDTO) throws SQLException {
        try (Connection connection = dataSource.getConnection()) {
            connection.setSchema(tenantId.toLowerCase(Locale.ROOT));
            
            String insertConfigSQL = """
                INSERT INTO tenant_config (config_key, config_value, config_type, description) VALUES (?, ?, ?, ?)
                """;
            
            try (PreparedStatement stmt = connection.prepareStatement(insertConfigSQL)) {
                // Company name
                stmt.setString(1, "company_name");
                stmt.setString(2, createDTO.getCompanyName());
                stmt.setString(3, "STRING");
                stmt.setString(4, "Company display name");
                stmt.executeUpdate();
                
                // Admin email
                stmt.setString(1, "admin_email");
                stmt.setString(2, createDTO.getAdminEmail());
                stmt.setString(3, "STRING");
                stmt.setString(4, "Primary admin email");
                stmt.executeUpdate();
                
                // Tenant status
                stmt.setString(1, "tenant_status");
                stmt.setString(2, "ACTIVE");
                stmt.setString(3, "STRING");
                stmt.setString(4, "Tenant activation status");
                stmt.executeUpdate();
            }
        }
    }

    private void createTenantAdmin(TenantCreateDTO createDTO) {
        try {
            UserCreateDTO adminUser = new UserCreateDTO();
            adminUser.setUsername(createDTO.getAdminUsername());
            adminUser.setPassword(createDTO.getAdminPassword());
            adminUser.setRole("ADMIN");
            adminUser.setEmail(createDTO.getAdminEmail());
            
            appUserService.saveUser(adminUser);
            log.debug("👤 Created admin user for tenant");
        } catch (Exception e) {
            log.error("❌ Failed to create admin user: {}", e.getMessage());
            throw new RuntimeException("Failed to create admin user", e);
        }
    }    private void setupDefaultConfigurations(String tenantId, TenantCreateDTO createDTO) throws SQLException {
        try (Connection connection = dataSource.getConnection()) {
            connection.setSchema(tenantId.toLowerCase(Locale.ROOT));
            
            String[] defaultConfigs = {
                "('timezone', 'UTC', 'STRING', 'Default timezone')",
                "('currency', 'USD', 'STRING', 'Default currency')",
                "('low_stock_threshold', '5', 'INTEGER', 'Default low stock threshold')",
                "('email_notifications', 'true', 'BOOLEAN', 'Enable email notifications')"
            };
            
            try (Statement statement = connection.createStatement()) {
                for (String config : defaultConfigs) {
                    String sql = "INSERT INTO tenant_config (config_key, config_value, config_type, description) VALUES " + config;
                    statement.execute(sql);
                }
            }
        }
    }    private TenantDTO getTenantInfo(String tenantId) {
        try (Connection connection = dataSource.getConnection()) {
            connection.setSchema(tenantId.toLowerCase(Locale.ROOT));
            
            String query = """
                SELECT config_key, config_value FROM tenant_config 
                WHERE config_key IN ('company_name', 'admin_email', 'tenant_status')
                """;
            
            try (PreparedStatement stmt = connection.prepareStatement(query);
                 ResultSet rs = stmt.executeQuery()) {
                
                TenantDTO.TenantDTOBuilder builder = TenantDTO.builder().tenantId(tenantId);
                
                while (rs.next()) {
                    String key = rs.getString("config_key");
                    String value = rs.getString("config_value");
                    
                    switch (key) {
                        case "company_name" -> builder.companyName(value);
                        case "admin_email" -> builder.adminEmail(value);
                        case "tenant_status" -> builder.status(value);
                    }
                }
                
                return builder.build();
            }
        } catch (SQLException e) {
            log.error("❌ Failed to get tenant info for {}: {}", tenantId, e.getMessage());
            return null;
        }
    }

    private void updateTenantStatus(String tenantId, String status) throws SQLException {        try (Connection connection = dataSource.getConnection()) {
            connection.setSchema(tenantId.toLowerCase(Locale.ROOT));
            
            String updateSQL = """
                UPDATE tenant_config SET config_value = ?, updated_at = CURRENT_TIMESTAMP 
                WHERE config_key = 'tenant_status'
                """;
            
            try (PreparedStatement stmt = connection.prepareStatement(updateSQL)) {
                stmt.setString(1, status);
                stmt.executeUpdate();
            }
        }
    }

    private boolean isSystemSchema(String schemaName) {
        return schemaName.equalsIgnoreCase("INFORMATION_SCHEMA") ||
               schemaName.equalsIgnoreCase("SYSTEM_LOBS") ||
               schemaName.equalsIgnoreCase("SYS") ||
               schemaName.equalsIgnoreCase("SYSAUX");
    }

    private void cleanupFailedTenant(String tenantId) {
        try (Connection connection = dataSource.getConnection();
             Statement statement = connection.createStatement()) {
            
            String schemaName = tenantId.toLowerCase(Locale.ROOT);
            statement.execute(String.format("DROP SCHEMA IF EXISTS %s CASCADE", schemaName));
            log.info("🧹 Cleaned up failed tenant schema: {}", schemaName);
            
        } catch (SQLException e) {
            log.error("❌ Failed to cleanup tenant {}: {}", tenantId, e.getMessage());
        }
    }
}
