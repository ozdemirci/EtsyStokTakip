package dev.oasis.stockify.config;

import lombok.extern.slf4j.Slf4j;
import org.flywaydb.core.Flyway;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.flyway.FlywayMigrationStrategy;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

import javax.sql.DataSource;
import java.util.Arrays;
import java.util.List;

/**
 * Multi-tenant Flyway configuration
 * Ensures that database migrations are applied to all tenant schemas
 */
@Slf4j
@Configuration
@Profile("dev")
public class MultiTenantFlywayConfig {

    @Value("${spring.flyway.schemas:PUBLIC,stockify,acme_corp,global_trade,artisan_crafts,tech_solutions}")
    private String[] tenantSchemas;

    @Value("${spring.flyway.locations:classpath:db/migration}")
    private String[] migrationLocations;

    /**
     * Custom Flyway migration strategy for multi-tenant setup
     */
    @Bean
    public FlywayMigrationStrategy flywayMigrationStrategy() {
        return new FlywayMigrationStrategy() {
            @Override
            public void migrate(Flyway flyway) {
                log.info("🚀 Starting multi-tenant Flyway migrations...");
                
                // Get the datasource from Flyway
                DataSource dataSource = flyway.getConfiguration().getDataSource();
                
                // Migrate each tenant schema
                List<String> schemas = Arrays.asList(tenantSchemas);
                for (String schema : schemas) {
                    migrateSchema(dataSource, schema);
                }
                
                log.info("✅ Multi-tenant Flyway migrations completed for {} schemas", schemas.size());
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
    }
}
