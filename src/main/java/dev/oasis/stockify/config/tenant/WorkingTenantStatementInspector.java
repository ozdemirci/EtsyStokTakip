package dev.oasis.stockify.config.tenant;

import org.hibernate.resource.jdbc.spi.StatementInspector;
import lombok.extern.slf4j.Slf4j;

/**
 * @deprecated This class is deprecated and will be removed in future versions.
 * Use the permanent solution: PostgreSQLMultiTenantPhysicalNamingStrategy + PostgreSQLMultiTenantConnectionProvider
 * 
 * Temporary StatementInspector solution that worked as a workaround
 * until PostgreSQL-native schema-based multi-tenancy was properly configured.
 * 
 * This class is now REPLACED by:
 * - PostgreSQLMultiTenantPhysicalNamingStrategy (for schema naming)
 * - PostgreSQLMultiTenantConnectionProvider (for search_path management)
 */
@Slf4j
@Deprecated(since = "2025-06-24", forRemoval = true)
public class WorkingTenantStatementInspector implements StatementInspector {
    
    public WorkingTenantStatementInspector() {
        log.warn("⚠️ DEPRECATED: WorkingTenantStatementInspector is deprecated. " +
                "Remove 'hibernate.session_factory.statement_inspector' from application.properties " +
                "and rely on PostgreSQLMultiTenantPhysicalNamingStrategy instead.");
    }
    
    @Override
    public String inspect(String sql) {
        // Log deprecation warning every time this is called
        log.warn("⚠️ DEPRECATED: StatementInspector is being used. " +
                "Migration to PhysicalNamingStrategy + ConnectionProvider is recommended.");
        
        String currentTenant = TenantContext.getCurrentTenant();
        
        if (currentTenant != null && !currentTenant.isEmpty() && !currentTenant.equals("public")) {
            String modifiedSql = sql;
            
            // Replace quoted identifiers: "public"."table_name" -> "tenant"."table_name"
            modifiedSql = modifiedSql.replaceAll("\"public\"\\.", "\"" + currentTenant.toLowerCase() + "\".");
            
            // Replace unquoted identifiers: public.table_name -> tenant.table_name
            modifiedSql = modifiedSql.replaceAll("\\bpublic\\.", currentTenant.toLowerCase() + ".");
            
            if (!sql.equals(modifiedSql)) {
                log.warn("🔧 DEPRECATED SQL REWRITE for tenant '{}': Schema changed from public to {} " +
                        "(Consider removing StatementInspector and using PhysicalNamingStrategy)", 
                         currentTenant, currentTenant.toLowerCase());
                return modifiedSql;
            }
        }
        
        return sql;
    }
}
