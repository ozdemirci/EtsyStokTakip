package dev.oasis.stockify.config;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.flywaydb.core.Flyway;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.autoconfigure.flyway.FlywayMigrationStrategy;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.core.annotation.Order;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Component;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDateTime;

/**
 * Combined Multi-tenant Setup Component
 * Handles both Flyway migrations and super admin initialization
 * Order 1: Ensures this runs before DataLoader (Order 3)
 */
@Slf4j
@Configuration
@Order(1) // Run before DataLoader
@RequiredArgsConstructor
public class MultiTenantFlywayConfig implements CommandLineRunner {    @Value("${spring.flyway.schemas:public,company1}")
    private String[] tenantSchemas;

    @Value("${spring.flyway.locations:classpath:db/migration}")
    private String[] migrationLocations;
    private final DataSource dataSource;    public void run(String... args) {
        log.info("🚀 Starting multi-tenant setup: Flyway migrations + Super admin creation...");
        
        try {
            // Step 1: Apply Flyway migrations to all tenant schemas first
            log.info("🗄️ Starting Flyway migrations for all tenant schemas...");
            
            // Migrate each tenant schema
            for (String schema : tenantSchemas) {
                migrateSchema(dataSource, schema);
            }
            
            log.info("✅ Flyway migrations completed for {} schemas", tenantSchemas.length);
            
            // Step 2: Create super admin in 'company1' tenant (after migrations are done)
            createSuperAdminIfNotExists();
            
            log.info("✅ Multi-tenant setup completed successfully!");
            
        } catch (Exception e) {
            log.error("❌ Failed during multi-tenant setup: {}", e.getMessage(), e);
            throw new RuntimeException("Failed to complete multi-tenant setup", e);
        }
    }    /**
     * Custom Flyway migration strategy for multi-tenant setup
     * Since we handle migrations manually in run(), this just does a no-op
     */
    @Bean
    public FlywayMigrationStrategy flywayMigrationStrategy() {
        return new FlywayMigrationStrategy() {
            @Override
            public void migrate(Flyway flyway) {
                // No-op: migrations are handled in run() method to ensure proper order
                log.debug("Flyway auto-migration disabled - handled manually in CommandLineRunner");
            }
        };
    }    /**
     * Migrate a specific schema
     */
    private void migrateSchema(DataSource dataSource, String schemaName) {
        try {
            log.info("🏗️ Migrating schema: {}", schemaName);
            
            // First, ensure the schema exists
            try (Connection connection = dataSource.getConnection();
                 var stmt = connection.createStatement()) {
                
                // Create schema if it doesn't exist (except for public which should exist by default)
                if (!"public".equals(schemaName)) {
                    stmt.execute("CREATE SCHEMA IF NOT EXISTS " + schemaName);
                    log.debug("Ensured schema {} exists", schemaName);
                }
            } catch (SQLException e) {
                log.debug("Schema {} might already exist: {}", schemaName, e.getMessage());
            }
            
            Flyway tenantFlyway = Flyway.configure()
                .dataSource(dataSource)
                .locations(migrationLocations)
                .schemas(schemaName)
                .defaultSchema(schemaName)
                .createSchemas(true)
                .baselineOnMigrate(true)
                .cleanOnValidationError(true)
                .table("flyway_schema_history_" + schemaName.toLowerCase().replace("-", "_"))
                .load();
            
            // Migrate the schema
            tenantFlyway.migrate();
            
            log.info("✅ Successfully migrated schema: {}", schemaName);
            
        } catch (Exception e) {
            log.error("❌ Failed to migrate schema {}: {}", schemaName, e.getMessage(), e);
            throw new RuntimeException("Failed to migrate schema: " + schemaName, e);
        }
    }/**
     * Create super admin user in 'stockify' tenant if not exists
     * Uses direct JDBC to avoid circular dependency issues
     */    private void createSuperAdminIfNotExists() {        try {
            log.info("🔧 Checking/creating admin user in 'company1' schema...");
            
            // Use direct JDBC to avoid circular dependency with repositories
            try (Connection connection = dataSource.getConnection()) {
                
                // Switch to company1 schema using connection.setSchema() method
                // Use lowercase schema name as created by Flyway
                connection.setSchema("company1");
                
                // Check if admin already exists
                boolean adminExists = false;
                try (PreparedStatement checkStmt = connection.prepareStatement(
                    "SELECT COUNT(*) FROM app_user WHERE username = ?")) {
                    checkStmt.setString(1, "admin");
                    try (ResultSet rs = checkStmt.executeQuery()) {
                        if (rs.next() && rs.getInt(1) > 0) {
                            adminExists = true;
                        }
                    }
                }
                
                if (!adminExists) {
                    log.info("🔧 Creating admin user in 'company1' schema...");
                    
                    // Create BCrypt password encoder for password hashing
                    BCryptPasswordEncoder passwordEncoder = new BCryptPasswordEncoder();
                    String hashedPassword = passwordEncoder.encode("admin123");

                    // Determine accessible tenants from configured schemas
                    String accessibleTenants = String.join(",", tenantSchemas).toLowerCase();
                    log.debug("Using accessible tenants for admin: {}", accessibleTenants);
                      // Insert admin user with tenant management capabilities
                    try (PreparedStatement insertStmt = connection.prepareStatement(
                        "INSERT INTO app_user (username, password, role, is_active, can_manage_all_tenants, accessible_tenants, is_global_user, primary_tenant, created_at, updated_at) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)")) {
                        insertStmt.setString(1, "admin");
                        insertStmt.setString(2, hashedPassword);
                        insertStmt.setString(3, "ADMIN");
                        insertStmt.setBoolean(4, true);
                        insertStmt.setBoolean(5, true); // can_manage_all_tenants
                        // Avoid unset parameter when property is empty
                        if (accessibleTenants == null) {
                            insertStmt.setNull(6, java.sql.Types.VARCHAR);
                        } else {
                            insertStmt.setString(6, accessibleTenants);
                        }
                        insertStmt.setBoolean(7, true); // is_global_user
                        insertStmt.setString(8, "company1"); // primary_tenant (lowercase)
                        insertStmt.setObject(9, LocalDateTime.now());
                        insertStmt.setObject(10, LocalDateTime.now());
                        
                        int rowsAffected = insertStmt.executeUpdate();
                        if (rowsAffected > 0) {
                            log.info("✅ Admin user created successfully in 'company1' schema");
                            log.info("📋 Admin Credentials:");
                            log.info("   Schema: company1");
                            log.info("   Username: admin");
                            log.info("   Password: admin123");
                            log.info("   Role: ADMIN");
                            log.info("   Can Manage All Tenants: YES");                            log.info("   Accessible Tenants: ALL");
                            log.info("   Primary Tenant: company1");
                            log.info("⚠️  Please change the password after first login!");
                        } else {
                            log.warn("⚠️  Admin user creation returned 0 rows affected");
                        }
                    }
                } else {
                    log.info("✓ Admin user already exists in 'company1' schema");
                }
            }
            
        } catch (SQLException e) {
            log.error("❌ Database error while creating admin user: {}", e.getMessage(), e);
            throw new RuntimeException("Failed to initialize admin", e);
        } catch (Exception e) {
            log.error("❌ Failed to create admin user in 'company1' schema: {}", e.getMessage(), e);
            throw new RuntimeException("Failed to initialize admin", e);
        }
    }
}
