package dev.oasis.stockify.config.tenant;

import org.hibernate.boot.model.relational.Namespace;
import org.hibernate.boot.model.relational.Sequence;
import org.hibernate.mapping.Table;
import org.hibernate.tool.schema.spi.SchemaFilter;
import org.hibernate.tool.schema.spi.SchemaFilterProvider;
import lombok.extern.slf4j.Slf4j;

/**
 * PostgreSQL Schema Filter Provider for Multi-tenant environments
 * Ensures that schema operations are properly filtered and executed in the correct tenant context
 */
@Slf4j
public class PostgreSQLSchemaFilterProvider implements SchemaFilterProvider {

    @Override
    public SchemaFilter getCreateFilter() {
        return new TenantAwareSchemaFilter();
    }

    @Override
    public SchemaFilter getDropFilter() {
        return new TenantAwareSchemaFilter();
    }

    @Override
    public SchemaFilter getMigrateFilter() {
        return new TenantAwareSchemaFilter();
    }

    @Override
    public SchemaFilter getValidateFilter() {
        return new TenantAwareSchemaFilter();
    }

    @Override
    public SchemaFilter getTruncatorFilter() {
        return new TenantAwareSchemaFilter();
    }

    /**
     * Tenant-aware schema filter that respects the current tenant context
     */
    private static class TenantAwareSchemaFilter implements SchemaFilter {

        @Override
        public boolean includeNamespace(Namespace namespace) {
            String currentTenant = TenantContext.getCurrentTenant();
            String schemaName = namespace.getName().getSchema() != null ? 
                               namespace.getName().getSchema().getText() : "public";
            
            // Always include public schema
            if ("public".equals(schemaName)) {
                return true;
            }
            
            // Include current tenant's schema
            if (currentTenant != null && !currentTenant.isEmpty()) {
                String tenantSchema = currentTenant.toLowerCase();
                boolean include = tenantSchema.equals(schemaName);
                
                if (include) {
                    log.debug("🏗️ Including schema '{}' for tenant '{}'", schemaName, currentTenant);
                }
                
                return include;
            }
            
            // Default: exclude unknown schemas
            return false;
        }

        @Override
        public boolean includeTable(Table table) {
            // Include all tables - schema filtering is handled at namespace level
            return true;
        }

        @Override
        public boolean includeSequence(Sequence sequence) {
            // Include all sequences - schema filtering is handled at namespace level
            return true;
        }
    }
}
