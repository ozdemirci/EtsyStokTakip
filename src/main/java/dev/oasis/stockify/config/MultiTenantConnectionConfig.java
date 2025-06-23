package dev.oasis.stockify.config;

import dev.oasis.stockify.config.tenant.PostgreSQLMultiTenantConnectionProvider;
import dev.oasis.stockify.config.tenant.SchemaMultiTenantConnectionProvider;
import org.hibernate.engine.jdbc.connections.spi.MultiTenantConnectionProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.context.annotation.Profile;

/**
 * Configuration for multi-tenant connection providers based on active profiles
 */
@Configuration
public class MultiTenantConnectionConfig {

    private static final Logger log = LoggerFactory.getLogger(MultiTenantConnectionConfig.class);

    /**
     * H2-based connection provider for development profiles
     */
    @Bean("multiTenantConnectionProvider")
    @Primary
    @Profile({"dev", "test"})
    public MultiTenantConnectionProvider<String> h2MultiTenantConnectionProvider(
            SchemaMultiTenantConnectionProvider provider) {
        log.info("🔧 Configuring H2/Schema-based MultiTenantConnectionProvider for development");
        return provider;
    }

    /**
     * PostgreSQL-based connection provider for production profiles
     */
    @Bean("multiTenantConnectionProvider")
    @Primary
    @Profile({"prod", "production"})
    public MultiTenantConnectionProvider<String> postgresqlMultiTenantConnectionProvider(
            PostgreSQLMultiTenantConnectionProvider provider) {
        log.info("🐘 Configuring PostgreSQL-based MultiTenantConnectionProvider for production");
        return provider;
    }
}
