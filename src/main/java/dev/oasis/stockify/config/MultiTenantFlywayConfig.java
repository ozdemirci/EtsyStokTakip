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

    @Value("${spring.flyway.schemas:public,stockify,acme_corp,global_trade,artisan_crafts,tech_solutions}")
    private String[] tenantSchemas;

    @Value("${spring.flyway.locations:classpath:db/migration}")
    private String[] migrationLocations;
    private final DataSource dataSource;

    public void run(String... args) {
        log.info("🚀 Starting multi-tenant setup: Flyway migrations ");
        

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
    }
}
