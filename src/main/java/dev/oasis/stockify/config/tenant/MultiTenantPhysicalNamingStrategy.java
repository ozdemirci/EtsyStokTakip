package dev.oasis.stockify.config.tenant;

import org.hibernate.boot.model.naming.Identifier;
import org.hibernate.boot.model.naming.PhysicalNamingStrategy;
import org.hibernate.engine.jdbc.env.spi.JdbcEnvironment;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Custom Physical Naming Strategy to handle schema-based multi-tenancy
 * Ensures that all table references include the current tenant schema
 */
public class MultiTenantPhysicalNamingStrategy implements PhysicalNamingStrategy {
    
    private static final Logger log = LoggerFactory.getLogger(MultiTenantPhysicalNamingStrategy.class);    @Override
    public Identifier toPhysicalCatalogName(Identifier identifier, JdbcEnvironment jdbcEnvironment) {
        return identifier;
    }

    @Override
    public Identifier toPhysicalSchemaName(Identifier identifier, JdbcEnvironment jdbcEnvironment) {
        // Get current tenant from context
        String currentTenant = TenantContext.getCurrentTenant();
        
        if (currentTenant != null && !currentTenant.isEmpty() && !currentTenant.equals("public")) {
            log.info("🏢 FORCING schema to current tenant: {} (was: {})", currentTenant, identifier);
            return Identifier.toIdentifier(currentTenant.toLowerCase());
        }
        
        // Fallback to public schema if no tenant context
        log.warn("⚠️ No tenant context found, using public schema (current tenant: {})", currentTenant);
        return Identifier.toIdentifier("public");
    }

    @Override
    public Identifier toPhysicalTableName(Identifier identifier, JdbcEnvironment jdbcEnvironment) {
        // Get current tenant from context
        String currentTenant = TenantContext.getCurrentTenant();
        
        if (currentTenant != null && !currentTenant.isEmpty() && !currentTenant.equals("public")) {
            // Force schema qualification for table names
            String qualifiedTableName = currentTenant.toLowerCase() + "." + identifier.getText();
            log.info("🏢 FORCING table name with schema: {} (original: {})", qualifiedTableName, identifier.getText());
            return Identifier.toIdentifier(qualifiedTableName);
        }
        
        // For public schema or no tenant context, use original table name
        log.info("📋 Using original table name: {} (tenant: {})", identifier.getText(), currentTenant);
        return identifier;
    }

    @Override
    public Identifier toPhysicalSequenceName(Identifier identifier, JdbcEnvironment jdbcEnvironment) {
        return identifier;
    }

    @Override
    public Identifier toPhysicalColumnName(Identifier identifier, JdbcEnvironment jdbcEnvironment) {
        return identifier;
    }
}
