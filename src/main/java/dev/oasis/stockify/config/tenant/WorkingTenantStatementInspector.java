package dev.oasis.stockify.config.tenant;

import org.hibernate.resource.jdbc.spi.StatementInspector;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Temporary StatementInspector solution that works
 * Until we can debug why PhysicalNamingStrategy is not being called
 */
public class WorkingTenantStatementInspector implements StatementInspector {
    
    private static final Logger log = LoggerFactory.getLogger(WorkingTenantStatementInspector.class);

    @Override
    public String inspect(String sql) {
        String currentTenant = TenantContext.getCurrentTenant();
        
        if (currentTenant != null && !currentTenant.isEmpty() && !currentTenant.equals("public")) {
            String modifiedSql = sql;
            
            // Replace quoted identifiers: "public"."table_name" -> "tenant"."table_name"
            modifiedSql = modifiedSql.replaceAll("\"public\"\\.", "\"" + currentTenant.toLowerCase() + "\".");
            
            // Replace unquoted identifiers: public.table_name -> tenant.table_name
            modifiedSql = modifiedSql.replaceAll("\\bpublic\\.", currentTenant.toLowerCase() + ".");
            
            if (!sql.equals(modifiedSql)) {
                log.info("🔧 SQL FIXED for tenant '{}': Schema changed from public to {}", 
                         currentTenant, currentTenant.toLowerCase());
                return modifiedSql;
            }
        }
        
        return sql;
    }
}
