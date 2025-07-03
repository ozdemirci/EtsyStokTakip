package dev.oasis.stockify.config;

import dev.oasis.stockify.model.AppUser;
import org.springframework.context.annotation.Configuration;

import jakarta.annotation.PostConstruct;

/**
 * Configuration class to inject tenant schemas into AppUser entity
 */
@Configuration
public class TenantSchemaConfiguration {

    // Default tenant schemas since we're not using Flyway anymore
    private final String defaultSchemas = "public,com,rezonans";

    @PostConstruct
    public void initializeTenantSchemas() {
        AppUser.setDefaultAccessibleTenants(defaultSchemas);
    }
}
