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
        // Use Locale.ROOT to avoid Turkish locale uppercase issues (i -> İ)
        String schemaName = tenantIdentifier.toUpperCase(Locale.ROOT);
        
        try {
            // Schema switching only - Flyway handles table creation
            connection.setSchema(schemaName);
            
            log.debug("Successfully set connection schema to: {}", schemaName);
        } catch (SQLException e) {
            log.error("Failed to set schema for tenant: {}", schemaName, e);
            throw new SQLException("Failed to set tenant schema: " + schemaName, e);
        }
        return connection;
    }    @Override
    public void releaseConnection(String tenantIdentifier, Connection connection) throws SQLException {
        try {
            // Use PUBLIC schema as default, avoiding Turkish locale issues
            connection.setSchema("PUBLIC");
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
