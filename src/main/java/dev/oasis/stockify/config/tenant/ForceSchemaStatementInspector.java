package dev.oasis.stockify.config.tenant;

import org.hibernate.resource.jdbc.spi.StatementInspector;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * EMERGENCY StatementInspector to FORCE tenant schema usage
 * This will actively rewrite any SQL that tries to use public schema
 * when a tenant context exists
 */
@Slf4j
@Component
public class ForceSchemaStatementInspector implements StatementInspector {
    
    @Override
    public String inspect(String sql) {
        String currentTenant = TenantContext.getCurrentTenant();
        
        // Only rewrite if we have a non-public tenant
        if (currentTenant != null && !currentTenant.isEmpty() && !"public".equals(currentTenant)) {
            String originalSql = sql;
            String targetSchema = currentTenant.toLowerCase();
            
            // AGGRESSIVE FORCE rewrite: Replace any public schema references
            String modifiedSql = sql;
            
            // Pattern 1: "public"."table_name" -> "tenant"."table_name"
            modifiedSql = modifiedSql.replaceAll("\"public\"\\.", "\"" + targetSchema + "\".");
            
            // Pattern 2: public.table_name -> tenant.table_name
            modifiedSql = modifiedSql.replaceAll("\\bpublic\\.", targetSchema + ".");
            
            // Pattern 3: FROM public. -> FROM tenant.
            modifiedSql = modifiedSql.replaceAll("(?i)from\\s+public\\.", "from " + targetSchema + ".");
            
            // Pattern 4: INSERT INTO public. -> INSERT INTO tenant.
            modifiedSql = modifiedSql.replaceAll("(?i)insert\\s+into\\s+public\\.", "insert into " + targetSchema + ".");
            
            // Pattern 5: UPDATE public. -> UPDATE tenant.
            modifiedSql = modifiedSql.replaceAll("(?i)update\\s+public\\.", "update " + targetSchema + ".");
            
            // Pattern 6: DELETE FROM public. -> DELETE FROM tenant.
            modifiedSql = modifiedSql.replaceAll("(?i)delete\\s+from\\s+public\\.", "delete from " + targetSchema + ".");
            
            // Pattern 7: CREATE TABLE public. -> CREATE TABLE tenant.
            modifiedSql = modifiedSql.replaceAll("(?i)create\\s+table\\s+public\\.", "create table " + targetSchema + ".");
            
            // Pattern 8: ALTER TABLE public. -> ALTER TABLE tenant.
            modifiedSql = modifiedSql.replaceAll("(?i)alter\\s+table\\s+public\\.", "alter table " + targetSchema + ".");
            
            // Pattern 9: DROP TABLE public. -> DROP TABLE tenant.
            modifiedSql = modifiedSql.replaceAll("(?i)drop\\s+table\\s+public\\.", "drop table " + targetSchema + ".");
            
            // Pattern 10: Standalone table names without schema prefix
            // Bu pattern daha agresif, dikkatli kullan
            String[] commonTables = {"users", "products", "categories", "notifications", "tenants", "subscriptions"};
            for (String table : commonTables) {
                // Only rewrite if the table is not already schema-qualified
                modifiedSql = modifiedSql.replaceAll(
                    "(?i)(?<!\\.)\\b" + table + "\\b(?!\\s*\\.)",
                    targetSchema + "." + table
                );
            }
            
            // Pattern 11: Prevent any unqualified table access for critical operations
            if (modifiedSql.toLowerCase().contains("select") || 
                modifiedSql.toLowerCase().contains("insert") ||
                modifiedSql.toLowerCase().contains("update") ||
                modifiedSql.toLowerCase().contains("delete")) {
                
                // If we still find bare table names, log a critical warning
                for (String table : commonTables) {
                    if (modifiedSql.matches("(?i).*\\b" + table + "\\b(?![\\w\\.]|\\s*\\.).*")) {
                        log.error("🚨 CRITICAL: Possible unqualified table access: {} in SQL for tenant: {}", 
                                table, currentTenant);
                    }
                }
            }
            
            if (!originalSql.equals(modifiedSql)) {
                log.warn("🔥 FORCE SCHEMA REWRITE for tenant '{}': public -> {}", currentTenant, targetSchema);
                log.debug("Original SQL: {}", originalSql);
                log.debug("Modified SQL: {}", modifiedSql);
                return modifiedSql;
            }
        }
        
        return sql;
    }
}
