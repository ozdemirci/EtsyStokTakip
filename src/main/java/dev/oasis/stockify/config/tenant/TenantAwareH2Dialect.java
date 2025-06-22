package dev.oasis.stockify.config.tenant;

import org.hibernate.dialect.H2Dialect;
import org.hibernate.engine.jdbc.env.spi.JdbcEnvironment;
import org.hibernate.boot.model.naming.Identifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Custom H2 Dialect that dynamically handles schema resolution for multi-tenancy
 * More elegant than hard-coded SQL string replacement
 */
public class TenantAwareH2Dialect extends H2Dialect {
    
    private static final Logger log = LoggerFactory.getLogger(TenantAwareH2Dialect.class);

    @Override
    public String getTableTypeString() {
        return super.getTableTypeString();
    }

    @Override
    public boolean supportsIfExistsBeforeTableName() {
        return true;
    }

    @Override
    public boolean supportsIfExistsAfterTableName() {
        return false;
    }

    /**
     * Override to dynamically qualify table names with current tenant schema
     */
    public String qualifyTableName(String tableName) {
        String currentTenant = TenantContext.getCurrentTenant();
        
        if (currentTenant != null && !currentTenant.isEmpty() && !currentTenant.equals("public")) {
            String qualifiedName = "\"" + currentTenant.toLowerCase() + "\".\"" + tableName + "\"";
            log.debug("🔧 Dialect: Qualifying table '{}' with schema '{}'", tableName, currentTenant);
            return qualifiedName;
        }
        
        return "\"public\".\"" + tableName + "\"";
    }
}
