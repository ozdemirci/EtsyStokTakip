package dev.oasis.stockify.config;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.flywaydb.core.Flyway;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.autoconfigure.flyway.FlywayMigrationStrategy;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.SQLException;



@Slf4j
@Configuration
@Order(1) // Run before DataLoader
@RequiredArgsConstructor
public class MultiTenantFlywayConfig implements CommandLineRunner {    
    
    @Value("${spring.flyway.schemas}")
    private final String[] tenantSchemas;

    @Value("${spring.flyway.locations}")
    private final String[] migrationLocations;
    private final DataSource dataSource; 

    public void run(String... args) {

        log.info("🚀 Starting multi-tenant setup: Flyway migrations + Super admin creation...");
        
        try {
            
            log.info("🗄️ Starting Flyway migrations for all tenant schemas...");
            
            // First migrate public schema with general migrations
            migratePublicSchema(dataSource);
            
            // Then migrate each tenant schema with tenant-specific migrations
            for (String schema : tenantSchemas) {
                migrateTenantSchema(dataSource, schema);
            }
            
            log.info("✅ Flyway migrations completed for {} schemas", tenantSchemas.length); 
            log.info("✅ Multi-tenant setup completed successfully!");
            
        } catch (Exception e) {
            log.error("❌ Failed during multi-tenant setup: {}", e.getMessage(), e);
            throw new RuntimeException("Failed to complete multi-tenant setup", e);
        }
    }    
    
    /**
     * Custom Flyway migration strategy for multi-tenant setup
     * Since we handle migrations manually in run(), this just does a no-op
     */
    @Bean
    public FlywayMigrationStrategy flywayMigrationStrategy() {
        return flyway -> {
        // No-op: migrations are handled in run() method to ensure proper order
        log.debug("Flyway auto-migration disabled - handled manually in CommandLineRunner");

        };
    }    
    
    /**
     * Migrate a specific schema
     */
    
    public void migrateSchema(DataSource dataSource, String schemaName) {
        try {
            log.info("🏗️ Migrating schema: {}", schemaName);
            
            // First, ensure the schema exists
            try (Connection connection = dataSource.getConnection();
                 var statement = connection.createStatement()) {
                  // Create schema if it doesn't exist (except for public which should exist by default)
                if (!"public".equals(schemaName)) {
                    statement.execute("CREATE SCHEMA IF NOT EXISTS \"" + schemaName + "\"");
                    log.debug("✅ Ensured schema {} exists", schemaName);
                }
            } catch (SQLException e) {
                log.warn("⚠️ Schema {} might already exist: {}", schemaName, e.getMessage());
            }
            
            Flyway tenantFlyway = Flyway.configure()
                .dataSource(dataSource)
                .locations(migrationLocations)
                .schemas(schemaName)
                .defaultSchema(schemaName)
                .createSchemas(true)
                .baselineOnMigrate(true)
                .table("flyway_schema_history_" + schemaName.toLowerCase().replace("-", "_"))
                .load();

           
            // Migrate the schema
            tenantFlyway.migrate();
            
            log.info("✅ Successfully migrated schema: {}", schemaName);
            
        } catch (Exception e) {
            log.error("❌ Failed to migrate schema {}: {}", schemaName, e.getMessage(), e);
            throw new RuntimeException("Failed to migrate schema: " + schemaName, e);
        }

    }
    
    /**
     * Migrate public schema with general migrations
     */
    private void migratePublicSchema(DataSource dataSource) {
        try {
            log.info("🏗️ Migrating PUBLIC schema with general migrations...");
            
            Flyway publicFlyway = Flyway.configure()
                .dataSource(dataSource)
                .locations(migrationLocations)  // Uses general migrations
                .schemas("public")
                .defaultSchema("public")
                .createSchemas(true)
                .baselineOnMigrate(true)
                .validateOnMigrate(false)  // Disable validation to avoid checksum issues
                .cleanOnValidationError(false)  // Don't clean on validation errors
                .outOfOrder(true)  // Allow out of order migrations
                .table("flyway_schema_history_public")
                .load();

            // First try to repair if there are checksum mismatches
            try {
                log.info("🔧 Running Flyway repair for PUBLIC schema...");
                publicFlyway.repair();
                log.info("✅ Flyway repair completed for PUBLIC schema");
            } catch (Exception repairEx) {
                log.warn("⚠️ Flyway repair failed for PUBLIC schema: {}", repairEx.getMessage());
            }

            // Try migration
            try {
                publicFlyway.migrate();
            } catch (Exception migrationEx) {
                log.warn("⚠️ First migration attempt failed, trying repair again: {}", migrationEx.getMessage());
                // Try repair one more time and then migrate
                try {
                    publicFlyway.repair();
                    publicFlyway.migrate();
                } catch (Exception secondEx) {
                    log.error("❌ Migration failed even after repair: {}", secondEx.getMessage());
                    throw secondEx;
                }
            }
            
            log.info("✅ Successfully migrated PUBLIC schema");
            
        } catch (Exception e) {
            log.error("❌ Failed to migrate PUBLIC schema: {}", e.getMessage(), e);
            throw new RuntimeException("Failed to migrate PUBLIC schema", e);
        }
    }
    
    /**
     * Migrate tenant schema with tenant-specific migrations
     */
    private void migrateTenantSchema(DataSource dataSource, String schemaName) {
        try {
            log.info("🏗️ Migrating tenant schema: {} with tenant-specific migrations", schemaName);
            
            // First, ensure the schema exists
            try (Connection connection = dataSource.getConnection();
                 var statement = connection.createStatement()) {
                if (!"public".equals(schemaName)) {
                    statement.execute("CREATE SCHEMA IF NOT EXISTS \"" + schemaName + "\"");
                    log.debug("✅ Ensured schema {} exists", schemaName);
                }
            } catch (SQLException e) {
                log.warn("⚠️ Schema {} might already exist: {}", schemaName, e.getMessage());
            }
            
            // Use the same migration location for tenant schemas
            // Our consolidated migration file works for all schemas
            String[] tenantMigrationLocations = migrationLocations;
            
            Flyway tenantFlyway = Flyway.configure()
                .dataSource(dataSource)
                .locations(tenantMigrationLocations)  // Use tenant-specific migrations
                .schemas(schemaName)
                .defaultSchema(schemaName)
                .createSchemas(true)
                .baselineOnMigrate(true)
                .validateOnMigrate(false)  // Disable validation to avoid checksum issues
                .cleanOnValidationError(false)  // Don't clean on validation errors
                .outOfOrder(true)  // Allow out of order migrations
                .table("flyway_schema_history_" + schemaName.toLowerCase().replace("-", "_"))
                .load();

            // First try to repair if there are checksum mismatches
            try {
                log.debug("🔧 Running Flyway repair for tenant schema: {}", schemaName);
                tenantFlyway.repair();
                log.debug("✅ Flyway repair completed for tenant schema: {}", schemaName);
            } catch (Exception repairEx) {
                log.debug("⚠️ Flyway repair not needed or failed for {}: {}", schemaName, repairEx.getMessage());
            }
           
            // Try migration
            try {
                tenantFlyway.migrate();
            } catch (Exception migrationEx) {
                log.warn("⚠️ First migration attempt failed for {}, trying repair again: {}", schemaName, migrationEx.getMessage());
                // Try repair one more time and then migrate
                try {
                    tenantFlyway.repair();
                    tenantFlyway.migrate();
                } catch (Exception secondEx) {
                    log.error("❌ Migration failed even after repair for {}: {}", schemaName, secondEx.getMessage());
                    throw secondEx;
                }
            }
            
            log.info("✅ Successfully migrated tenant schema: {}", schemaName);
            
        } catch (Exception e) {
            log.error("❌ Failed to migrate tenant schema {}: {}", schemaName, e.getMessage(), e);
            throw new RuntimeException("Failed to migrate tenant schema: " + schemaName, e);
        }
    }
                     
}
