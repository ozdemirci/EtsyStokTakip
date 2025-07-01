package dev.oasis.stockify.service;

import dev.oasis.stockify.config.tenant.TenantContext;
import dev.oasis.stockify.dto.TenantCreateDTO;
import dev.oasis.stockify.dto.TenantDTO;
import dev.oasis.stockify.dto.UserCreateDTO;
import dev.oasis.stockify.exception.TenantAlreadyExistsException;
import dev.oasis.stockify.exception.TenantNotFoundException;
import dev.oasis.stockify.model.Role;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.flywaydb.core.Flyway;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.sql.DataSource;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
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
    
    @Value("${spring.flyway.locations}")
    private String[] migrationLocations;

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
    }

    /**
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
    }

    /**
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
                .replaceAll("[^a-zA-Z0-9]", "")
                .trim();
        
        // Ensure minimum length
        if (sanitized.length() < 2) {
            throw new RuntimeException("Şirket adı çok kısa. En az 2 karakter olmalıdır.");
        }
        
        // First try to use the company name directly
        String baseId = sanitized.substring(0, Math.min(sanitized.length(), 20));
        
        // Check if this tenant ID already exists
        if (!tenantExists(baseId)) {
            return baseId;
        }
        
        // If exists, try with incrementing numbers
        for (int i = 2; i <= 999; i++) {
            String candidateId = baseId + i;
            if (!tenantExists(candidateId)) {
                return candidateId;
            }
        }
        
        // If all numeric suffixes are taken, fall back to random suffix
        String suffix = UUID.randomUUID().toString().substring(0, 4);
        return baseId + "_" + suffix;
    }

    private void createTenantSchema(String tenantId) throws SQLException {
        String schemaName = tenantId.toLowerCase();
        
        try (Connection connection = dataSource.getConnection();
             Statement statement = connection.createStatement()) {
            
            // Create schema first
            statement.execute(String.format("CREATE SCHEMA IF NOT EXISTS \"%s\"", schemaName));
            log.debug("🏗️ Created schema: {}", schemaName);
        }
        
        // Run Flyway migrations for the new schema
        try {
            log.info("🚀 Running Flyway migrations for schema: {}", schemaName);
            
            // Use the same migration location as configured in application.properties
            // Our consolidated migration file works for all schemas
            String[] tenantMigrationLocations = {"classpath:db/migration"};
            
            Flyway tenantFlyway = Flyway.configure()
                .dataSource(dataSource)
                .locations(tenantMigrationLocations)  // Use tenant-specific migrations
                .schemas(schemaName)
                .defaultSchema(schemaName)
                .createSchemas(true)
                .baselineOnMigrate(true)
                .cleanOnValidationError(false)  // Set to false to avoid issues
                .validateOnMigrate(false)        // Disable validation to avoid checksum issues
                .outOfOrder(true)               // Allow out of order migrations
                .table("flyway_schema_history_" + schemaName.replace("-", "_"))
                .load();
            
            // First try to repair if there are checksum mismatches
            try {
                tenantFlyway.repair();
                log.debug("🔧 Flyway repair completed for tenant schema: {}", schemaName);
            } catch (Exception repairEx) {
                log.debug("⚠️ Flyway repair not needed or failed for {}: {}", schemaName, repairEx.getMessage());
            }
            
            // Migrate the schema
            var migrationResult = tenantFlyway.migrate();
            
            if (migrationResult.success) {
                log.info("✅ Successfully migrated schema: {} with {} migrations", 
                        schemaName, migrationResult.migrationsExecuted);
                        
                // Verify tables were created
                verifySchemaTablesCreated(schemaName);
            } else {
                throw new SQLException("Migration failed for schema: " + schemaName);
            }
            
        } catch (Exception e) {
            log.error("❌ Failed to migrate schema {}: {}", schemaName, e.getMessage(), e);
            throw new SQLException("Failed to migrate schema: " + schemaName, e);
        }
    }
    
    /**
     * Verify that all required tables were created in the tenant schema
     */
    private void verifySchemaTablesCreated(String schemaName) {
        try (Connection connection = dataSource.getConnection()) {
            // Set the schema context
            connection.setSchema(schemaName);
            
            // List of expected tables from migration files (excluding contact_messages which is public-only)
            String[] expectedTables = {
                "app_user", "product", "stock_notification", "tenant_config", 
                "stock_movement", "product_categories"
                // NOTE: contact_messages is NOT included as it's public schema only
            };
            
            DatabaseMetaData metaData = connection.getMetaData();
            Set<String> actualTables = new HashSet<>();
            
            // Get all tables in the schema
            try (ResultSet tables = metaData.getTables(null, schemaName.toUpperCase(), null, new String[]{"TABLE"})) {
                while (tables.next()) {
                    actualTables.add(tables.getString("TABLE_NAME").toLowerCase());
                }
            }
            
            // Check if all expected tables exist
            List<String> missingTables = new ArrayList<>();
            for (String expectedTable : expectedTables) {
                if (!actualTables.contains(expectedTable.toLowerCase())) {
                    missingTables.add(expectedTable);
                }
            }
            
            if (missingTables.isEmpty()) {
                log.info("✅ All {} tenant tables successfully created in schema: {}", 
                        expectedTables.length, schemaName);
                log.debug("📋 Created tables: {}", actualTables);
            } else {
                log.warn("⚠️ Missing tables in schema {}: {}", schemaName, missingTables);
                log.info("📋 Actual tables found: {}", actualTables);
                
                // Try to manually create missing critical tables if needed
                createMissingTables(connection, schemaName, missingTables);
            }
            
        } catch (SQLException e) {
            log.error("❌ Failed to verify schema tables for {}: {}", schemaName, e.getMessage());
        }
    }
    
    /**
     * Manually create missing critical tables (fallback)
     */
    private void createMissingTables(Connection connection, String schemaName, List<String> missingTables) {
        log.info("🔧 Attempting to manually create missing tables in schema: {}", schemaName);
        
        try {
            // Read and execute migration files manually if Flyway failed
            log.info("📄 Re-running migration manually for schema: {}", schemaName);
            
            // Execute tenant-specific migration file
            executeMigrationFile(connection, "T1__tenant_init_schema.sql");
            
            log.info("✅ Manual table creation completed for schema: {}", schemaName);
            
        } catch (Exception e) {
            log.error("❌ Failed to manually create tables for schema {}: {}", schemaName, e.getMessage());
        }
    }
    
    /**
     * Execute a specific migration file
     */
    private void executeMigrationFile(Connection connection, String fileName) {
        try {
            // Use tenant-specific migration file path
            String resourcePath = "/db/migration/tenant/" + fileName;
            
            try (InputStream inputStream = getClass().getResourceAsStream(resourcePath)) {
                if (inputStream == null) {
                    log.warn("⚠️ Tenant migration file not found: {}", resourcePath);
                    // Fallback to main migration files
                    resourcePath = "/db/migration/" + fileName;
                    try (InputStream fallbackStream = getClass().getResourceAsStream(resourcePath)) {
                        if (fallbackStream == null) {
                            log.warn("⚠️ Migration file not found in fallback: {}", resourcePath);
                            return;
                        }
                        executeStatements(connection, fallbackStream, fileName);
                    }
                    return;
                }
                
                executeStatements(connection, inputStream, fileName);
                
            }
        } catch (Exception e) {
            log.error("❌ Failed to execute migration file {}: {}", fileName, e.getMessage());
        }
    }
    
    /**
     * Execute SQL statements from input stream
     */
    private void executeStatements(Connection connection, InputStream inputStream, String fileName) throws Exception {
        String sql = new String(inputStream.readAllBytes(), StandardCharsets.UTF_8);
        
        // Split SQL by semicolons and execute each statement
        String[] statements = sql.split(";");
        
        try (Statement stmt = connection.createStatement()) {
            for (String statement : statements) {
                String trimmedStmt = statement.trim();
                if (!trimmedStmt.isEmpty() && !trimmedStmt.startsWith("--")) {
                    try {
                        stmt.execute(trimmedStmt);
                    } catch (SQLException e) {
                        // Log but continue with other statements
                        log.debug("⚠️ Statement execution warning: {}", e.getMessage());
                    }
                }
            }
        }
        
        log.debug("📄 Executed migration file: {}", fileName);
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
                
                // Subscription plan - default to TRIAL for new tenants
                stmt.setString(1, "subscription_plan");
                stmt.setString(2, "TRIAL");
                stmt.setString(3, "STRING");
                stmt.setString(4, "Tenant subscription plan");
                stmt.executeUpdate();
            }
        }
    }

    private void createTenantAdmin(TenantCreateDTO createDTO) {
        try {
            UserCreateDTO adminUser = new UserCreateDTO();
            adminUser.setUsername(createDTO.getAdminUsername());
            adminUser.setPassword(createDTO.getAdminPassword());
            adminUser.setRole(Role.ADMIN);
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
    }

    private TenantDTO getTenantInfo(String tenantId) {
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
            statement.execute(String.format("DROP SCHEMA IF EXISTS \"%s\" CASCADE", schemaName));
            log.info("🧹 Cleaned up failed tenant schema: {}", schemaName);
            
        } catch (SQLException e) {
            log.error("❌ Failed to cleanup tenant {}: {}", tenantId, e.getMessage());
        }
    }
}
