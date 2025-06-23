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
            
            // Migrate each tenant schema
            for (String schema : tenantSchemas) {
                migrateSchema(dataSource, schema);
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
    
    private void migrateSchema(DataSource dataSource, String schemaName) {
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

    }
                     
}
