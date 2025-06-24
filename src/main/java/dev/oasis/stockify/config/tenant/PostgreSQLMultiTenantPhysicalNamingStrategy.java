package dev.oasis.stockify.config.tenant;

import org.hibernate.boot.model.naming.Identifier;
import org.hibernate.boot.model.naming.PhysicalNamingStrategy;
import org.hibernate.engine.jdbc.env.spi.JdbcEnvironment;
import org.springframework.stereotype.Component;
import lombok.extern.slf4j.Slf4j;

/**
 * PostgreSQL-optimized Physical Naming Strategy for multi-tenant schema resolution
 * 
 * Features:
 * - PostgreSQL schema-based multi-tenancy
 * - Thread-safe and performant with caching
 * - Automatic schema name sanitization
 * - Comprehensive logging for debugging
 * 
 * This class is the PERMANENT solution replacing WorkingTenantStatementInspector
 */
@Slf4j
@Component
public class PostgreSQLMultiTenantPhysicalNamingStrategy implements PhysicalNamingStrategy {
    
    // Cache for schema name conversions to improve performance
    private static final java.util.Map<String, String> schemaCache = new java.util.concurrent.ConcurrentHashMap<>();
    
    public PostgreSQLMultiTenantPhysicalNamingStrategy() {
        log.info("🏗️ PostgreSQL Multi-tenant Physical Naming Strategy initialized");
    }
    
    @Override
    public Identifier toPhysicalCatalogName(Identifier identifier, JdbcEnvironment jdbcEnvironment) {
        return identifier;
    }

    @Override
    public Identifier toPhysicalSchemaName(Identifier identifier, JdbcEnvironment jdbcEnvironment) {
        String tenantId = TenantContext.getCurrentTenant();
        
        log.debug("🔍 Schema resolution request - Current tenant: '{}', Original identifier: '{}'", 
                 tenantId, identifier != null ? identifier.getText() : "null");
        
        // Use public schema for null or empty tenant IDs
        if (tenantId == null || tenantId.isEmpty() || tenantId.equals("public")) {
            log.debug("🏛️ Using public schema");
            return Identifier.toIdentifier("public");
        }
        
        // Convert to PostgreSQL-friendly schema name with caching
        String pgSchema = schemaCache.computeIfAbsent(tenantId, this::convertToPostgreSQLSchema);
        
        log.info("🐘 Schema resolution: '{}' -> '{}' (cached: {})", 
                tenantId, pgSchema, schemaCache.containsKey(tenantId));
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
        
        log.info("🔄 Schema name conversion: '{}' -> '{}'", tenantId, converted);
        return converted;
    }

    @Override
    public Identifier toPhysicalTableName(Identifier identifier, JdbcEnvironment jdbcEnvironment) {
        // Convert CamelCase to snake_case for table names (PostgreSQL convention)
        if (identifier == null) return null;
        
        String tableName = identifier.getText();
        String snakeCaseName = camelToSnakeCase(tableName);
        
        if (!tableName.equals(snakeCaseName)) {
            log.debug("📝 Table name conversion: '{}' -> '{}'", tableName, snakeCaseName);
        }
        
        return Identifier.toIdentifier(snakeCaseName);
    }

    @Override
    public Identifier toPhysicalSequenceName(Identifier identifier, JdbcEnvironment jdbcEnvironment) {
        // PostgreSQL sequences are schema-aware and will use the current schema
        if (identifier == null) return null;
        
        String sequenceName = identifier.getText();
        String snakeCaseName = camelToSnakeCase(sequenceName);
        
        if (!sequenceName.equals(snakeCaseName)) {
            log.debug("🔢 Sequence name conversion: '{}' -> '{}'", sequenceName, snakeCaseName);
        }
        
        return Identifier.toIdentifier(snakeCaseName);
    }

    @Override
    public Identifier toPhysicalColumnName(Identifier identifier, JdbcEnvironment jdbcEnvironment) {
        // Convert CamelCase to snake_case for column names (PostgreSQL convention)
        if (identifier == null) return null;
        
        String columnName = identifier.getText();
        String snakeCaseName = camelToSnakeCase(columnName);
        
        if (!columnName.equals(snakeCaseName)) {
            log.debug("📊 Column name conversion: '{}' -> '{}'", columnName, snakeCaseName);
        }
        
        return Identifier.toIdentifier(snakeCaseName);
    }
    
    /**
     * Convert CamelCase to snake_case following PostgreSQL conventions
     */
    private String camelToSnakeCase(String input) {
        if (input == null || input.isEmpty()) {
            return input;
        }
        
        return input.replaceAll("([a-z])([A-Z])", "$1_$2").toLowerCase();
    }
    
    /**
     * Clear the schema cache (useful for testing or dynamic tenant management)
     */
    public static void clearSchemaCache() {
        schemaCache.clear();
        log.info("🧹 Schema cache cleared");
    }
    
    /**
     * Get current cache size (for monitoring/debugging)
     */
    public static int getCacheSize() {
        return schemaCache.size();
    }
    
    /**
     * Get cached schema mappings (for debugging)
     */
    public static java.util.Map<String, String> getCachedMappings() {
        return new java.util.HashMap<>(schemaCache);
    }
}
