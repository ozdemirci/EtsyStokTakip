package dev.oasis.stockify.config.tenant;

import org.hibernate.engine.jdbc.connections.spi.MultiTenantConnectionProvider;
import org.springframework.stereotype.Component;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.SQLException;

/**
 * PostgreSQL-optimized MultiTenantConnectionProvider
 * Uses PostgreSQL's SET search_path for efficient schema switching
 */
@Component
public class PostgreSQLMultiTenantConnectionProvider implements MultiTenantConnectionProvider<String> {
    
    private static final Logger log = LoggerFactory.getLogger(PostgreSQLMultiTenantConnectionProvider.class);
    
    private final DataSource dataSource;
    
    public PostgreSQLMultiTenantConnectionProvider(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    @Override
    public Connection getAnyConnection() throws SQLException {
        return dataSource.getConnection();
    }

    @Override
    public void releaseAnyConnection(Connection connection) throws SQLException {
        connection.close();
    }

    @Override
    public Connection getConnection(String tenantIdentifier) throws SQLException {
        Connection connection = getAnyConnection();
        
        try {
            // PostgreSQL'e özel optimizasyon: search_path kullan
            String schema = tenantIdentifier != null ? tenantIdentifier.toLowerCase() : "public";
            
            // Schema'nın var olduğundan emin ol
            ensureSchemaExists(connection, schema);
            
            // PostgreSQL search_path ayarla (performanslı!)
            String searchPath = String.format("%s, public", schema);
            connection.createStatement().execute("SET search_path TO " + searchPath);
            
            log.debug("🐘 PostgreSQL search_path set to: {}", searchPath);
            
            return connection;
            
        } catch (SQLException e) {
            log.error("Failed to set search_path for tenant: {}", tenantIdentifier, e);
            connection.close();
            throw e;
        }
    }

    @Override
    public void releaseConnection(String tenantIdentifier, Connection connection) throws SQLException {
        try {
            // Reset to default search_path before releasing
            connection.createStatement().execute("SET search_path TO public");
        } catch (SQLException e) {
            log.warn("Failed to reset search_path: {}", e.getMessage());
        } finally {
            connection.close();
        }
    }

    @Override
    public boolean supportsAggressiveRelease() {
        return false;
    }    @Override
    public boolean isUnwrappableAs(Class<?> unwrapType) {
        return false;
    }

    @Override
    public <T> T unwrap(Class<T> unwrapType) {
        return null;
    }
    
    /**
     * PostgreSQL'de schema'nın var olduğundan emin ol
     */
    private void ensureSchemaExists(Connection connection, String schema) throws SQLException {
        if (!"public".equals(schema)) {
            String createSchemaSql = "CREATE SCHEMA IF NOT EXISTS " + schema;
            connection.createStatement().execute(createSchemaSql);
            log.debug("🐘 Ensured PostgreSQL schema exists: {}", schema);
        }
    }
}
