package dev.oasis.stockify.config.tenant;

import org.hibernate.resource.jdbc.spi.StatementInspector;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Custom Statement Inspector to modify SQL queries for tenant isolation
 * This inspector replaces schema references with the current tenant schema
 */
public class TenantAwareStatementInspector implements StatementInspector {
    
    private static final Logger log = LoggerFactory.getLogger(TenantAwareStatementInspector.class);    @Override
    public String inspect(String sql) {
        String currentTenant = TenantContext.getCurrentTenant();
        
        if (currentTenant != null && !currentTenant.isEmpty() && !currentTenant.equals("public")) {
            // Replace both quoted and unquoted public schema references with current tenant schema
            String modifiedSql = sql;
            
            // Handle quoted identifiers: "public"."table_name" -> "tenant"."table_name"
            modifiedSql = modifiedSql.replaceAll("\"public\"\\.", "\"" + currentTenant.toLowerCase() + "\".");
            
            // Handle unquoted identifiers: public.table_name -> tenant.table_name
            modifiedSql = modifiedSql.replaceAll("\\bpublic\\.", currentTenant.toLowerCase() + ".");
            
            if (!sql.equals(modifiedSql)) {
                log.info("🔧 SQL MODIFIED for tenant '{}': \nOriginal: {}\nModified: {}", 
                         currentTenant, sql, modifiedSql);
                return modifiedSql;
            }
        }
        
        // Log unmodified SQL for debugging
        log.debug("📋 SQL UNCHANGED (tenant: {}): {}", currentTenant, sql);
        return sql;
    }
}
