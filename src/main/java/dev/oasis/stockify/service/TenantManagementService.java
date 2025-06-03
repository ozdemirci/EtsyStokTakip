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
                if (!isSystemSchema(schemaName) && !schemaName.equalsIgnoreCase("PUBLIC")) {
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
     */
    public boolean tenantExists(String tenantId) {
        try (Connection connection = dataSource.getConnection()) {
            String schemaName = tenantId.toUpperCase();
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
        String baseId = companyName.toLowerCase()
                .replaceAll("[^a-zA-Z0-9]", "")
                .substring(0, Math.min(companyName.length(), 10));
        
        // Add random suffix to ensure uniqueness
        String suffix = UUID.randomUUID().toString().substring(0, 4);
        return baseId + "_" + suffix;
    }

    private void createTenantSchema(String tenantId) throws SQLException {
        try (Connection connection = dataSource.getConnection();
             Statement statement = connection.createStatement()) {
            
            String schemaName = tenantId.toUpperCase();
            
            // Create schema
            statement.execute(String.format("CREATE SCHEMA IF NOT EXISTS %s", schemaName));
            log.debug("🏗️ Created schema: {}", schemaName);
            
            // Create all required tables in the tenant schema
            createTenantTables(connection, schemaName);
        }
    }

    private void createTenantTables(Connection connection, String schemaName) throws SQLException {
        // Read and execute the schema migration script for the tenant
        String[] tableCreationScripts = {
            // App User table
            String.format("""
                CREATE TABLE IF NOT EXISTS %s.app_user (
                    id BIGINT GENERATED BY DEFAULT AS IDENTITY PRIMARY KEY,
                    username VARCHAR(20) NOT NULL UNIQUE,
                    password VARCHAR(255) NOT NULL,
                    role VARCHAR(50) NOT NULL DEFAULT 'USER',
                    email VARCHAR(255),
                    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                    is_active BOOLEAN DEFAULT TRUE,
                    last_login TIMESTAMP
                )
                """, schemaName),
            
            // Product table
            String.format("""
                CREATE TABLE IF NOT EXISTS %s.product (
                    id BIGINT GENERATED BY DEFAULT AS IDENTITY PRIMARY KEY,
                    sku VARCHAR(100) NOT NULL UNIQUE,
                    title VARCHAR(255) NOT NULL,
                    description TEXT,
                    category VARCHAR(100) NOT NULL,
                    price DECIMAL(15,2) NOT NULL DEFAULT 0.00,
                    stock_level INTEGER NOT NULL DEFAULT 0,
                    low_stock_threshold INTEGER NOT NULL DEFAULT 5,
                    etsy_product_id VARCHAR(100),
                    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                    created_by BIGINT,
                    updated_by BIGINT,
                    is_active BOOLEAN DEFAULT TRUE,
                    is_featured BOOLEAN DEFAULT FALSE
                )
                """, schemaName),
            
            // Stock notification table
            String.format("""
                CREATE TABLE IF NOT EXISTS %s.stock_notification (
                    id BIGINT GENERATED BY DEFAULT AS IDENTITY PRIMARY KEY,
                    product_id BIGINT NOT NULL,
                    message TEXT NOT NULL,
                    notification_type VARCHAR(50) DEFAULT 'LOW_STOCK',
                    is_read BOOLEAN DEFAULT FALSE,
                    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                    read_at TIMESTAMP,
                    read_by BIGINT,
                    priority VARCHAR(20) DEFAULT 'MEDIUM',
                    category VARCHAR(50) DEFAULT 'STOCK_ALERT'
                )
                """, schemaName),
            
            // Tenant config table
            String.format("""
                CREATE TABLE IF NOT EXISTS %s.tenant_config (
                    id BIGINT GENERATED BY DEFAULT AS IDENTITY PRIMARY KEY,
                    config_key VARCHAR(100) NOT NULL UNIQUE,
                    config_value TEXT,
                    config_type VARCHAR(50) DEFAULT 'STRING',
                    description TEXT,
                    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
                )
                """, schemaName)
        };
        
        try (Statement statement = connection.createStatement()) {
            for (String script : tableCreationScripts) {
                statement.execute(script);
            }
            log.debug("📋 Created all tables for schema: {}", schemaName);
        }
    }

    private void setupTenantConfiguration(String tenantId, TenantCreateDTO createDTO) throws SQLException {
        try (Connection connection = dataSource.getConnection()) {
            connection.setSchema(tenantId.toUpperCase());
            
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
    }

    private void setupDefaultConfigurations(String tenantId, TenantCreateDTO createDTO) throws SQLException {
        try (Connection connection = dataSource.getConnection()) {
            connection.setSchema(tenantId.toUpperCase());
            
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
    }

    private TenantDTO getTenantInfo(String tenantId) {
        try (Connection connection = dataSource.getConnection()) {
            connection.setSchema(tenantId.toUpperCase());
            
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

    private void updateTenantStatus(String tenantId, String status) throws SQLException {
        try (Connection connection = dataSource.getConnection()) {
            connection.setSchema(tenantId.toUpperCase());
            
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
            
            String schemaName = tenantId.toUpperCase();
            statement.execute(String.format("DROP SCHEMA IF EXISTS %s CASCADE", schemaName));
            log.info("🧹 Cleaned up failed tenant schema: {}", schemaName);
            
        } catch (SQLException e) {
            log.error("❌ Failed to cleanup tenant {}: {}", tenantId, e.getMessage());
        }
    }
}
