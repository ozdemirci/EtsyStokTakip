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
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;

/**
 * Combined Multi-tenant Setup Component
 * Handles both Flyway migrations and super admin initialization
 * Order 1: Ensures this runs before DataLoader (Order 3)
 */
@Slf4j
@Configuration
@Component
@Profile("dev")
@Order(1) // Run before DataLoader
@RequiredArgsConstructor
public class MultiTenantFlywayConfig implements CommandLineRunner {

    @Value("${spring.flyway.schemas:public,stockify,acme_corp,global_trade,artisan_crafts,tech_solutions,tenant1,tenant2}")
    private String[] tenantSchemas;

    @Value("${spring.flyway.locations:classpath:db/migration}")
    private String[] migrationLocations;
    
    private final DataSource dataSource;    @Override
    public void run(String... args) {
        log.info("🚀 Starting multi-tenant setup: Flyway migrations + Super admin creation...");
        
        try {
            // Step 1: Apply Flyway migrations to all tenant schemas
            // This will be handled by the FlywayMigrationStrategy bean
            
            // Step 2: Create super admin in 'stockify' tenant
            createSuperAdminIfNotExists();
            
            log.info("✅ Multi-tenant setup completed successfully!");
            
        } catch (Exception e) {
            log.error("❌ Failed during multi-tenant setup: {}", e.getMessage(), e);
            throw new RuntimeException("Failed to complete multi-tenant setup", e);
        }
    }    /**
     * Custom Flyway migration strategy for multi-tenant setup
     */
    @Bean
    public FlywayMigrationStrategy flywayMigrationStrategy() {
        return new FlywayMigrationStrategy() {
            @Override
            public void migrate(Flyway flyway) {
                log.info("🗄️ Starting Flyway migrations for all tenant schemas...");
                
                // Use the injected datasource
                DataSource ds = dataSource;
                
                // Migrate each tenant schema
                List<String> schemas = Arrays.asList(tenantSchemas);
                for (String schema : schemas) {
                    migrateSchema(ds, schema);
                }
                
                log.info("✅ Flyway migrations completed for {} schemas", schemas.size());
            }
        };
    }

    /**
     * Migrate a specific schema
     */
    private void migrateSchema(DataSource dataSource, String schemaName) {
        try {
            log.info("🏗️ Migrating schema: {}", schemaName);
            
            Flyway tenantFlyway = Flyway.configure()
                .dataSource(dataSource)
                .locations(migrationLocations)
                .schemas(schemaName)
                .defaultSchema(schemaName)
                .createSchemas(true)
                .baselineOnMigrate(true)
                .cleanOnValidationError(true)
                .table("flyway_schema_history_" + schemaName.toLowerCase())
                .load();
            
            // Create schema if it doesn't exist and migrate
            tenantFlyway.migrate();
            
            log.info("✅ Successfully migrated schema: {}", schemaName);
            
        } catch (Exception e) {
            log.error("❌ Failed to migrate schema {}: {}", schemaName, e.getMessage(), e);
            throw new RuntimeException("Failed to migrate schema: " + schemaName, e);
        }
    }    /**
     * Create super admin user in 'stockify' tenant if not exists
     * Uses direct JDBC to avoid circular dependency issues
     */    private void createSuperAdminIfNotExists() {
        try {
            log.info("🔧 Checking/creating super admin user in 'stockify' schema...");
              // Use direct JDBC to avoid circular dependency with repositories
            try (Connection connection = dataSource.getConnection()) {
                  // Switch to stockify schema using connection.setSchema() method
                // Use lowercase schema name as created by Flyway
                connection.setSchema("stockify");
                
                // Check if super admin already exists
                boolean superAdminExists = false;
                try (PreparedStatement checkStmt = connection.prepareStatement(
                    "SELECT COUNT(*) FROM app_user WHERE username = ?")) {
                    checkStmt.setString(1, "superadmin");
                    try (ResultSet rs = checkStmt.executeQuery()) {
                        if (rs.next() && rs.getInt(1) > 0) {
                            superAdminExists = true;
                        }
                    }
                }                  if (!superAdminExists) {
                    log.info("🔧 Creating super admin user in 'stockify' schema...");
                    
                    // Create BCrypt password encoder for password hashing
                    BCryptPasswordEncoder passwordEncoder = new BCryptPasswordEncoder();
                    String hashedPassword = passwordEncoder.encode("superadmin123");

                    // Determine accessible tenants from configured schemas
                    String accessibleTenants = String.join(",", tenantSchemas).toLowerCase();
                    log.debug("Using accessible tenants for super admin: {}", accessibleTenants);
                    
                    // Insert super admin user with full tenant management capabilities
                    try (PreparedStatement insertStmt = connection.prepareStatement(
                        "INSERT INTO app_user (username, password, role, is_active, can_manage_all_tenants, accessible_tenants, is_global_user, primary_tenant, created_at, updated_at) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)")) {
                        insertStmt.setString(1, "superadmin");
                        insertStmt.setString(2, hashedPassword);
                        insertStmt.setString(3, "SUPER_ADMIN");
                        insertStmt.setBoolean(4, true);
                        insertStmt.setBoolean(5, true); // can_manage_all_tenants
                        // Avoid unset parameter when property is empty
                        if (accessibleTenants == null) {
                            insertStmt.setNull(6, java.sql.Types.VARCHAR);
                        } else {
                            insertStmt.setString(6, accessibleTenants);
                        }
                        insertStmt.setBoolean(7, true); // is_global_user
                        insertStmt.setString(8, "stockify"); // primary_tenant (lowercase)
                        insertStmt.setObject(9, LocalDateTime.now());
                        insertStmt.setObject(10, LocalDateTime.now());
                        
                        int rowsAffected = insertStmt.executeUpdate();
                        if (rowsAffected > 0) {                            log.info("✅ Super admin user created successfully in 'stockify' schema");
                            log.info("📋 Super Admin Credentials:");
                            log.info("   Schema: stockify");
                            log.info("   Username: superadmin");
                            log.info("   Password: superadmin123");
                            log.info("   Role: SUPER_ADMIN");
                            log.info("   Can Manage All Tenants: YES");
                            log.info("   Accessible Tenants: ALL");                            log.info("   Primary Tenant: stockify");
                            log.info("⚠️  Please change the password after first login!");
                        } else {
                            log.warn("⚠️  Super admin user creation returned 0 rows affected");
                        }
                    }
                } else {
                    log.info("✓ Super admin user already exists in 'stockify' schema");
                }
            }
            
        } catch (SQLException e) {
            log.error("❌ Database error while creating super admin user: {}", e.getMessage(), e);
            throw new RuntimeException("Failed to initialize super admin", e);
        } catch (Exception e) {
            log.error("❌ Failed to create super admin user in 'stockify' schema: {}", e.getMessage(), e);
            throw new RuntimeException("Failed to initialize super admin", e);
        }
    }
}
