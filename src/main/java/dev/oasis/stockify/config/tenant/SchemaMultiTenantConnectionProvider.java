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
            // Always set the schema, even if connection is reused
            String currentSchema = connection.getSchema();
            if (!schemaName.equals(currentSchema)) {
                log.debug("Connection schema mismatch. Current: {}, Required: {}", currentSchema, schemaName);
            }            // Force schema switch every time
            connection.setSchema(schemaName);            // For H2, also execute SET SCHEMA command to ensure it takes effect
            try (var stmt = connection.createStatement()) {
                // Check if schema exists, if not create it (except for public)
                if (!"public".equals(schemaName)) {
                    try {
                        // Create schema - H2 with DATABASE_TO_LOWER=TRUE will handle lowercase
                        stmt.execute("CREATE SCHEMA IF NOT EXISTS " + schemaName);
                        log.debug("Ensured schema {} exists", schemaName);
                    } catch (SQLException e) {
                        log.debug("Schema {} might already exist: {}", schemaName, e.getMessage());
                    }
                }
                
                // Set schema
                stmt.execute("SET SCHEMA " + schemaName);
                log.debug("Executed SET SCHEMA {} command for H2", schemaName);
            }// Verify the schema was set correctly
            String verifiedSchema = connection.getSchema();
            log.debug("Successfully set connection schema to: {} (verified: {})", schemaName, verifiedSchema);
        } catch (SQLException e) {
            log.error("Failed to set schema for tenant: {}", schemaName, e);
            throw new SQLException("Failed to set tenant schema: " + schemaName, e);
        }
        return connection;
    }    /**
     * Map tenant identifier to actual schema name in database
     * This handles the difference between logical tenant names and physical schema names
     */
    private String mapTenantToSchema(String tenantIdentifier) {
        if (tenantIdentifier == null) {
            return "public";
        }
        
        // Always return lowercase for consistency
        return tenantIdentifier.toLowerCase(Locale.ROOT);
    }    @Override
    public void releaseConnection(String tenantIdentifier, Connection connection) throws SQLException {
        try {
            // Use public schema as default when releasing connection (lowercase)
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
