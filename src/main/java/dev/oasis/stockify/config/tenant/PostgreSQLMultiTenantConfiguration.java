package dev.oasis.stockify.config.tenant;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

/**
 * PostgreSQL Multi-tenant Configuration
 * Ensures proper setup of all multi-tenancy components
 */
@Configuration
@Slf4j
public class PostgreSQLMultiTenantConfiguration {

    /**
     * Explicitly configure the Physical Naming Strategy as a Spring Bean
     * This ensures it's properly integrated with Spring's context
     */
    @Bean
    @Primary
    public PostgreSQLMultiTenantPhysicalNamingStrategy physicalNamingStrategy() {
        log.info("🏗️ Configuring PostgreSQL Multi-tenant Physical Naming Strategy as Spring Bean");
        return new PostgreSQLMultiTenantPhysicalNamingStrategy();
    }

    /**
     * Log deprecation warning if StatementInspector is still configured
     */
    @Bean
    @ConditionalOnProperty(name = "spring.jpa.properties.hibernate.session_factory.statement_inspector")
    public DeprecationWarningLogger deprecationWarningLogger() {
        return new DeprecationWarningLogger();
    }

    /**
     * Helper class to warn about deprecated StatementInspector usage
     */
    static class DeprecationWarningLogger {
        public DeprecationWarningLogger() {
            log.warn("⚠️ DEPRECATION WARNING: " +
                    "hibernate.session_factory.statement_inspector is configured in application.properties. " +
                    "Consider removing it and relying on PostgreSQLMultiTenantPhysicalNamingStrategy for " +
                    "a more robust and maintainable solution.");
        }
    }
}
