package dev.oasis.stockify.config;

import dev.oasis.stockify.model.AppUser;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;

import jakarta.annotation.PostConstruct;

/**
 * Configuration class to inject tenant schemas from properties into AppUser entity
 */
@Configuration
public class TenantSchemaConfiguration {

    @Value("${spring.flyway.schemas:public,com,rezonans}")
    private String flywaySchemas;

    @PostConstruct
    public void initializeTenantSchemas() {
        // Set the default accessible tenants from flyway schemas
        AppUser.setDefaultAccessibleTenants(flywaySchemas);
    }
}
