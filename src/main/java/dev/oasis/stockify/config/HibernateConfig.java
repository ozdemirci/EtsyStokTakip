package dev.oasis.stockify.config;

import dev.oasis.stockify.config.tenant.CurrentTenantIdentifierResolverImpl;
import org.hibernate.cfg.AvailableSettings;
import org.hibernate.engine.jdbc.connections.spi.MultiTenantConnectionProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.orm.jpa.HibernatePropertiesCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.Map;

@Configuration
public class HibernateConfig implements HibernatePropertiesCustomizer {

    private static final Logger log = LoggerFactory.getLogger(HibernateConfig.class);

    private final MultiTenantConnectionProvider<String> multiTenantConnectionProvider;
    private final CurrentTenantIdentifierResolverImpl currentTenantIdentifierResolver;

    @Autowired
    public HibernateConfig(@Qualifier("multiTenantConnectionProvider") MultiTenantConnectionProvider<String> multiTenantConnectionProvider,
                          CurrentTenantIdentifierResolverImpl currentTenantIdentifierResolver) {
        this.multiTenantConnectionProvider = multiTenantConnectionProvider;
        this.currentTenantIdentifierResolver = currentTenantIdentifierResolver;
    }@Override
    public void customize(Map<String, Object> hibernateProperties) {
        log.info("Configuring Hibernate multi-tenancy properties");
        
        // Configure multi-tenancy strategy
        hibernateProperties.put("hibernate.multiTenancy", "SCHEMA");
        hibernateProperties.put(AvailableSettings.MULTI_TENANT_CONNECTION_PROVIDER, multiTenantConnectionProvider);
        hibernateProperties.put(AvailableSettings.MULTI_TENANT_IDENTIFIER_RESOLVER, currentTenantIdentifierResolver);
        
        // Disable schema validation for multi-tenant setup
        hibernateProperties.put(AvailableSettings.HBM2DDL_AUTO, "none");
        
        // Force connection provider usage for all operations
        hibernateProperties.put("hibernate.connection.provider_disables_autocommit", false);
        hibernateProperties.put("hibernate.connection.autocommit", false);
        
        // Set default schema back
        hibernateProperties.put(AvailableSettings.DEFAULT_SCHEMA, "public");
          log.info("Multi-tenancy configuration completed");
    }

    @Bean
    public CurrentTenantIdentifierResolverImpl currentTenantIdentifierResolver() {
        return currentTenantIdentifierResolver;
    }
}
