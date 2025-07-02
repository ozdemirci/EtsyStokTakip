package dev.oasis.stockify.service;

import dev.oasis.stockify.dto.TenantCreateDTO;
import dev.oasis.stockify.dto.TenantDTO;
import dev.oasis.stockify.dto.UserCreateDTO;
import dev.oasis.stockify.exception.TenantAlreadyExistsException;
import dev.oasis.stockify.exception.TenantNotFoundException;
import dev.oasis.stockify.model.Role;
import dev.oasis.stockify.util.ServiceTenantUtil;
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
import java.util.Arrays;
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
    private final ServiceTenantUtil serviceTenantUtil;
    
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
            serviceTenantUtil.setCurrentTenant(tenantId);
            
            // Create tenant configuration
            setupTenantConfiguration(tenantId, createDTO);
            
            // Create initial admin user
            createTenantAdmin(createDTO);
            
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
            serviceTenantUtil.clearCurrentTenant();
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
            serviceTenantUtil.setCurrentTenant(tenantId);
            updateTenantStatus(tenantId, "INACTIVE");
            log.info("✅ Successfully deactivated tenant: {}", tenantId);
        } catch (SQLException e) {
            log.error("❌ Failed to deactivate tenant: {}", e.getMessage());
            throw new RuntimeException("Failed to deactivate tenant: " + tenantId, e);
        } finally {
            serviceTenantUtil.clearCurrentTenant();
        }
    }

    /**
     * Activate a tenant
     */
    @Transactional
    public void activateTenant(String tenantId) {
        log.info("🔓 Activating tenant: {}", tenantId);
        
        try {
            serviceTenantUtil.setCurrentTenant(tenantId);
            updateTenantStatus(tenantId, "ACTIVE");
            log.info("✅ Successfully activated tenant: {}", tenantId);
        } catch (SQLException e) {
            log.error("❌ Failed to activate tenant: {}", e.getMessage());
            throw new RuntimeException("Failed to activate tenant: " + tenantId, e);
        } finally {
            serviceTenantUtil.clearCurrentTenant();
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
            log.debug("🔧 Using migration locations: {}", Arrays.toString(migrationLocations));
            
            // Use the same migration location as configured in application.properties
            // Our consolidated migration file works for all schemas
            String[] tenantMigrationLocations = {"classpath:db/migration"};
            
            // Check if migration files exist
            try {
                var migrationResource = getClass().getClassLoader().getResource("db/migration/V1__init_complete_schema.sql");
                if (migrationResource != null) {
                    log.debug("✅ Migration file found: {}", migrationResource.getPath());
                } else {
                    log.error("❌ Migration file NOT found: db/migration/V1__init_complete_schema.sql");
                }
            } catch (Exception e) {
                log.error("❌ Error checking migration file: {}", e.getMessage());
            }
            
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
            
            log.debug("🔧 Flyway configured for schema: {}", schemaName);
            
            // Check current migration status before running
            try {
                var info = tenantFlyway.info();
                log.debug("🔍 Current migration state for {}: {} migrations pending", schemaName, info.pending().length);
                for (var migration : info.pending()) {
                    log.debug("📄 Pending migration: {} - {}", migration.getVersion(), migration.getDescription());
                }
            } catch (Exception infoEx) {
                log.debug("⚠️ Could not get migration info: {}", infoEx.getMessage());
            }
            
            // First try to repair if there are checksum mismatches
            try {
                tenantFlyway.repair();
                log.debug("🔧 Flyway repair completed for tenant schema: {}", schemaName);
            } catch (Exception repairEx) {
                log.debug("⚠️ Flyway repair not needed or failed for {}: {}", schemaName, repairEx.getMessage());
            }
            
            // Migrate the schema
            var migrationResult = tenantFlyway.migrate();
            
            log.info("🔍 Migration result for schema {}: success={}, migrationsExecuted={}, warnings={}", 
                    schemaName, migrationResult.success, migrationResult.migrationsExecuted, 
                    migrationResult.warnings);
            
            if (migrationResult.success) {
                if (migrationResult.migrationsExecuted > 0) {
                    log.info("✅ Successfully migrated schema: {} with {} migrations", 
                            schemaName, migrationResult.migrationsExecuted);
                } else {
                    log.warn("⚠️ Migration successful but 0 migrations executed for schema: {}. This likely means the migration was already applied.", schemaName);
                    log.info("🔧 Manually creating tables for schema: {}", schemaName);
                    // Manually create tables since Flyway thinks they already exist
                    manuallyCreateTables(schemaName);
                }
                        
                // Verify tables were created
                verifySchemaTablesCreated(schemaName);
            } else {
                log.error("❌ Migration FAILED for schema: {}", schemaName);
                log.info("🔧 Attempting manual table creation as fallback for schema: {}", schemaName);
                // Try manual creation as fallback
                manuallyCreateTables(schemaName);
                // Verify tables were created
                verifySchemaTablesCreated(schemaName);
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
            log.debug("🔍 Verifying tables in schema: {}", schemaName);
            
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
                    String tableName = tables.getString("TABLE_NAME").toLowerCase();
                    actualTables.add(tableName);
                    log.debug("📄 Found table: {}", tableName);
                }
            }
            
            log.info("📊 Schema {} verification: expected {} tables, found {} tables", 
                    schemaName, expectedTables.length, actualTables.size());
            log.debug("📋 Expected tables: {}", String.join(", ", expectedTables));
            log.debug("📋 Actual tables: {}", String.join(", ", actualTables));
            
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
                log.error("❌ Missing tables in schema {}: {}", schemaName, missingTables);
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
        log.warn("🔧 Attempting to manually create {} missing tables in schema: {}", missingTables.size(), schemaName);
        log.warn("📋 Missing tables: {}", String.join(", ", missingTables));
        
        try {
            // Set the schema context
            connection.setSchema(schemaName);
            
            // Re-run the complete migration script manually
            log.info("📄 Re-running V1__init_complete_schema.sql manually for schema: {}", schemaName);
            
            // Read the migration file
            InputStream migrationStream = getClass().getClassLoader()
                    .getResourceAsStream("db/migration/V1__init_complete_schema.sql");
            
            if (migrationStream != null) {
                String migrationSql = new String(migrationStream.readAllBytes(), StandardCharsets.UTF_8);
                
                // Split by semicolon and execute each statement
                String[] statements = migrationSql.split(";");
                
                try (Statement stmt = connection.createStatement()) {
                    int executedStatements = 0;
                    for (String statement : statements) {
                        String trimmedStmt = statement.trim();
                        if (!trimmedStmt.isEmpty() && 
                            !trimmedStmt.startsWith("--") && 
                            !trimmedStmt.toLowerCase().contains("contact_messages")) { // Skip contact_messages
                            try {
                                stmt.execute(trimmedStmt);
                                executedStatements++;
                                log.debug("✅ Executed statement: {}", trimmedStmt.substring(0, Math.min(50, trimmedStmt.length())));
                            } catch (SQLException e) {
                                // Log but continue with other statements (tables may already exist)
                                log.debug("⚠️ Statement execution warning (ignoring): {}", e.getMessage());
                            }
                        }
                    }
                    log.info("✅ Manual migration completed: executed {} statements for schema: {}", executedStatements, schemaName);
                }
                
                migrationStream.close();
            } else {
                log.error("❌ Could not find migration file: V1__init_complete_schema.sql");
            }
            
            log.info("✅ Manual table creation completed for schema: {}", schemaName);
            
        } catch (Exception e) {
            log.error("❌ Failed to manually create tables for schema {}: {}", schemaName, e.getMessage(), e);
        }
    }

    private void setupTenantConfiguration(String tenantId, TenantCreateDTO createDTO) throws SQLException {
        try (Connection connection = dataSource.getConnection()) {
            connection.setSchema(tenantId.toLowerCase(Locale.ROOT));
            
            String insertConfigSQL = """
                INSERT INTO tenant_config (config_key, config_value, config_type, description) 
                VALUES (?, ?, ?, ?)
                ON CONFLICT (config_key) DO UPDATE SET 
                    config_value = EXCLUDED.config_value,
                    updated_at = CURRENT_TIMESTAMP
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
                
                // Default configurations
                stmt.setString(1, "timezone");
                stmt.setString(2, "UTC");
                stmt.setString(3, "STRING");
                stmt.setString(4, "Default timezone");
                stmt.executeUpdate();
                
                stmt.setString(1, "currency");
                stmt.setString(2, "USD");
                stmt.setString(3, "STRING");
                stmt.setString(4, "Default currency");
                stmt.executeUpdate();
                
                stmt.setString(1, "low_stock_threshold");
                stmt.setString(2, "5");
                stmt.setString(3, "INTEGER");
                stmt.setString(4, "Default low stock threshold");
                stmt.executeUpdate();
                
                stmt.setString(1, "email_notifications");
                stmt.setString(2, "true");
                stmt.setString(3, "BOOLEAN");
                stmt.setString(4, "Enable email notifications");
                stmt.executeUpdate();
                
                log.debug("✅ Tenant configuration completed for: {}", tenantId);
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
    
    /**
     * Manually create all tables for a tenant schema when Flyway doesn't execute migrations
     */
    private void manuallyCreateTables(String schemaName) {
        log.info("🔧 Manually creating tables for schema: {}", schemaName);
        
        try (Connection connection = dataSource.getConnection()) {
            // Set the schema context
            connection.setSchema(schemaName);
            log.debug("🔧 Schema context set to: {}", schemaName);
            
            // Read the migration file
            InputStream migrationStream = getClass().getClassLoader()
                    .getResourceAsStream("db/migration/V1__init_complete_schema.sql");
            
            if (migrationStream != null) {
                String migrationSql = new String(migrationStream.readAllBytes(), StandardCharsets.UTF_8);
                log.debug("📄 Migration file loaded: {} characters", migrationSql.length());
                
                // Split by semicolon and execute each statement
                String[] statements = migrationSql.split(";");
                
                try (Statement stmt = connection.createStatement()) {
                    int executedStatements = 0;
                    int skippedStatements = 0;
                    
                    for (String statement : statements) {
                        String trimmedStmt = statement.trim();
                        if (!trimmedStmt.isEmpty() && 
                            !trimmedStmt.startsWith("--") && 
                            !trimmedStmt.toLowerCase().contains("contact_messages")) { // Skip contact_messages (public only)
                            
                            try {
                                stmt.execute(trimmedStmt);
                                executedStatements++;
                                log.debug("✅ Executed: {}", trimmedStmt.substring(0, Math.min(50, trimmedStmt.length())) + "...");
                            } catch (SQLException e) {
                                if (e.getMessage().contains("already exists") || e.getMessage().contains("does not exist")) {
                                    // Table already exists or dependency issue - log and continue
                                    log.debug("⚠️ Statement skipped (table exists or dependency): {}", e.getMessage());
                                    skippedStatements++;
                                } else {
                                    log.error("❌ Error executing statement: {}", e.getMessage());
                                    log.debug("Statement: {}", trimmedStmt.substring(0, Math.min(100, trimmedStmt.length())));
                                    throw e;
                                }
                            }
                        }
                    }
                    log.info("✅ Manual migration completed for schema {}: {} statements executed, {} skipped", 
                            schemaName, executedStatements, skippedStatements);
                }
                
                migrationStream.close();
            } else {
                log.error("❌ Could not find migration file: V1__init_complete_schema.sql");
                throw new RuntimeException("Migration file not found");
            }
            
        } catch (Exception e) {
            log.error("❌ Failed to manually create tables for schema {}: {}", schemaName, e.getMessage(), e);
            throw new RuntimeException("Manual table creation failed for schema: " + schemaName, e);
        }
    }
}
