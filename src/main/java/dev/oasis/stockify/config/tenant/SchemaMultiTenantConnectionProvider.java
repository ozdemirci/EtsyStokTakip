package dev.oasis.stockify.config.tenant;

import org.hibernate.engine.jdbc.connections.spi.MultiTenantConnectionProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.Locale;

@Component
public class SchemaMultiTenantConnectionProvider implements MultiTenantConnectionProvider<String> {

    private static final Logger log = LoggerFactory.getLogger(SchemaMultiTenantConnectionProvider.class);
    private final DataSource dataSource;

    @Value("${spring.jpa.properties.hibernate.default_schema:public}")
    private String defaultSchema;

    @Autowired
    public SchemaMultiTenantConnectionProvider(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    @Override
    public Connection getAnyConnection() throws SQLException {
        return dataSource.getConnection();
    }

    @Override
    public void releaseAnyConnection(Connection connection) throws SQLException {
        connection.close();
    }    @Override
    public Connection getConnection(String tenantIdentifier) throws SQLException {
        log.debug("Getting connection for tenant: {}", tenantIdentifier);
        Connection connection = getAnyConnection();
        
        // Map tenant identifier to actual schema name
        String schemaName = mapTenantToSchema(tenantIdentifier);
        
        try {
            // Schema switching only - Flyway handles table creation
            connection.setSchema(schemaName);
            
            log.debug("Successfully set connection schema to: {}", schemaName);
        } catch (SQLException e) {
            log.error("Failed to set schema for tenant: {}", schemaName, e);
            throw new SQLException("Failed to set tenant schema: " + schemaName, e);
        }
        return connection;
    }
      /**
     * Map tenant identifier to actual schema name in database
     * This handles the difference between logical tenant names and physical schema names
     */
    private String mapTenantToSchema(String tenantIdentifier) {
        if (tenantIdentifier == null) {
            return "public";
        }
        
        // All schema names in lowercase for consistency with H2 settings
        return tenantIdentifier.toLowerCase(Locale.ROOT);
    }    @Override
    public void releaseConnection(String tenantIdentifier, Connection connection) throws SQLException {
        try {
            // Use public schema as default (lowercase for consistency)
            connection.setSchema("public");
        } finally {
            connection.close();
        }
    }

    @Override
    public boolean supportsAggressiveRelease() {
        return false;
    }

    @Override
    public boolean isUnwrappableAs(Class<?> unwrapType) {
        return MultiTenantConnectionProvider.class.isAssignableFrom(unwrapType);
    }

    @SuppressWarnings("unchecked")
    @Override
    public <T> T unwrap(Class<T> unwrapType) {
        if (isUnwrappableAs(unwrapType)) {
            return (T) this;
        }
        throw new IllegalArgumentException("Cannot unwrap to " + unwrapType);
    }
}
