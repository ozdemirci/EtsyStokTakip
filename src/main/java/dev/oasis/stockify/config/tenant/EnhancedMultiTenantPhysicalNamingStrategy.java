package dev.oasis.stockify.config.tenant;

import org.hibernate.boot.model.naming.Identifier;
import org.hibernate.boot.model.naming.PhysicalNamingStrategy;
import org.hibernate.engine.jdbc.env.spi.JdbcEnvironment;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Enhanced Physical Naming Strategy for dynamic multi-tenant schema resolution
 * Uses Hibernate's native naming strategy for cleaner integration
 */
public class EnhancedMultiTenantPhysicalNamingStrategy implements PhysicalNamingStrategy {
    
    private static final Logger log = LoggerFactory.getLogger(EnhancedMultiTenantPhysicalNamingStrategy.class);
    
    private final ThreadLocal<String> currentSchema = new ThreadLocal<>();

    @Override
    public Identifier toPhysicalCatalogName(Identifier identifier, JdbcEnvironment jdbcEnvironment) {
        return identifier;
    }

    @Override
    public Identifier toPhysicalSchemaName(Identifier identifier, JdbcEnvironment jdbcEnvironment) {
        String tenantId = resolveTenantSchema();
        
        if (tenantId != null && !tenantId.equals("public")) {
            log.debug("🏢 Dynamic schema resolution: {} -> {}", identifier, tenantId);
            return Identifier.toIdentifier(tenantId);
        }
        
        return identifier != null ? identifier : Identifier.toIdentifier("public");
    }

    @Override
    public Identifier toPhysicalTableName(Identifier identifier, JdbcEnvironment jdbcEnvironment) {
        // Let Hibernate handle table naming naturally with schema from toPhysicalSchemaName
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
    
    /**
     * Dynamically resolve tenant schema with fallback mechanisms
     */
    private String resolveTenantSchema() {
        // Priority 1: ThreadLocal cache
        String cached = currentSchema.get();
        if (cached != null) {
            return cached;
        }
        
        // Priority 2: TenantContext
        String fromContext = TenantContext.getCurrentTenant();
        if (fromContext != null && !fromContext.isEmpty()) {
            currentSchema.set(fromContext);
            return fromContext;
        }
        
        // Priority 3: Spring Security Context (if available)
        String fromSecurity = resolveFromSecurityContext();
        if (fromSecurity != null) {
            currentSchema.set(fromSecurity);
            return fromSecurity;
        }
        
        return "public";
    }
    
    /**
     * Try to resolve tenant from Spring Security context
     */
    private String resolveFromSecurityContext() {
        try {
            // This would require Spring Security integration
            // return SecurityContextHolder.getContext().getAuthentication()...
            return null;
        } catch (Exception e) {
            log.debug("Could not resolve tenant from security context: {}", e.getMessage());
            return null;
        }
    }
    
    /**
     * Clear ThreadLocal cache (important for thread pool environments)
     */
    public void clearCache() {
        currentSchema.remove();
    }
}
