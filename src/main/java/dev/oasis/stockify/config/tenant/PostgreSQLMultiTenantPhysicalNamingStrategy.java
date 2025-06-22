package dev.oasis.stockify.config.tenant;

import org.hibernate.boot.model.naming.Identifier;
import org.hibernate.boot.model.naming.PhysicalNamingStrategy;
import org.hibernate.engine.jdbc.env.spi.JdbcEnvironment;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * PostgreSQL-optimized Physical Naming Strategy for multi-tenant schema resolution
 * 
 * Features:
 * - Works with both H2 (development) and PostgreSQL (production)
 * - Converts tenant IDs to PostgreSQL-friendly schema names
 * - Thread-safe and performant
 * - No hard-coding, uses Hibernate's native APIs
 * 
 * Usage:
 * - Development: Works with H2 for testing
 * - Production: Optimized for PostgreSQL schema-based multi-tenancy
 */
public class PostgreSQLMultiTenantPhysicalNamingStrategy implements PhysicalNamingStrategy {
    
    private static final Logger log = LoggerFactory.getLogger(PostgreSQLMultiTenantPhysicalNamingStrategy.class);
    
    // Cache for schema name conversions to improve performance
    private static final java.util.Map<String, String> schemaCache = new java.util.concurrent.ConcurrentHashMap<>();
    
    @Override
    public Identifier toPhysicalCatalogName(Identifier identifier, JdbcEnvironment jdbcEnvironment) {
        return identifier;
    }    @Override
    public Identifier toPhysicalSchemaName(Identifier identifier, JdbcEnvironment jdbcEnvironment) {
        String tenantId = TenantContext.getCurrentTenant();
        
        // Use public schema for null or empty tenant IDs
        if (tenantId == null || tenantId.isEmpty() || tenantId.equals("public")) {
            return Identifier.toIdentifier("public");
        }
        
        // Convert to PostgreSQL-friendly schema name with caching
        String pgSchema = schemaCache.computeIfAbsent(tenantId, this::convertToPostgreSQLSchema);
        
        log.info("🐘 Schema resolution: {} -> {}", tenantId, pgSchema);
        return Identifier.toIdentifier(pgSchema);
    }
    
    /**
     * Convert tenant ID to PostgreSQL-friendly schema name
     * - Lowercase conversion
     * - Replace invalid characters with underscores
     * - Ensure it starts with a letter or underscore
     */
    private String convertToPostgreSQLSchema(String tenantId) {
        String converted = tenantId.toLowerCase()
                                   .replaceAll("[^a-z0-9_]", "_")
                                   .replaceAll("^[0-9]", "_$0"); // Ensure doesn't start with number
        
        log.info("🔄 Schema name conversion: {} -> {}", tenantId, converted);
        return converted;
    }    @Override
    public Identifier toPhysicalTableName(Identifier identifier, JdbcEnvironment jdbcEnvironment) {
        // Let Hibernate and the database handle table naming naturally
        // Schema will be resolved via toPhysicalSchemaName
        return identifier;
    }

    @Override
    public Identifier toPhysicalSequenceName(Identifier identifier, JdbcEnvironment jdbcEnvironment) {
        // PostgreSQL sequences are schema-aware and will use the current schema
        return identifier;
    }

    @Override
    public Identifier toPhysicalColumnName(Identifier identifier, JdbcEnvironment jdbcEnvironment) {
        // Column names don't need tenant-specific modifications
        return identifier;
    }
    
    /**
     * Clear the schema cache (useful for testing or dynamic tenant management)
     */
    public static void clearSchemaCache() {
        schemaCache.clear();
        log.debug("🧹 Schema cache cleared");
    }
    
    /**
     * Get current cache size (for monitoring/debugging)
     */
    public static int getCacheSize() {
        return schemaCache.size();
    }
}
