package dev.oasis.stockify.config.tenant;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.hibernate.engine.jdbc.connections.spi.MultiTenantConnectionProvider;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.SQLException;

/**
 * PostgreSQL-optimized MultiTenantConnectionProvider
 * Uses PostgreSQL's SET search_path for efficient schema switching
 */
@Slf4j
@Component("multiTenantConnectionProvider")
@Primary
@RequiredArgsConstructor
public class PostgreSQLMultiTenantConnectionProvider implements MultiTenantConnectionProvider<String> {
    
    private final DataSource dataSource;

    // Constructor-based initialization logging via @PostConstruct
    @jakarta.annotation.PostConstruct
    public void init() {
        log.info("🐘 PostgreSQL MultiTenantConnectionProvider initialized for production");
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
        Connection connection = null;
        try {
            connection = getAnyConnection();
            
            // PostgreSQL'e özel optimizasyon: search_path kullan
            String schema = tenantIdentifier != null ? tenantIdentifier.toLowerCase() : "public";
            
            // Schema'nın var olduğundan emin ol
            ensureSchemaExists(connection, schema);
            
            // PostgreSQL search_path ayarla (performanslı!)
            String searchPath = String.format("\"%s\", \"public\"", schema);
            try (var stmt = connection.createStatement()) {
                stmt.execute("SET search_path TO " + searchPath);
                
                // Verify the search_path was set correctly
                try (var rs = stmt.executeQuery("SHOW search_path")) {
                    if (rs.next()) {
                        String currentPath = rs.getString(1);
                        log.debug("🐘 PostgreSQL search_path verified: {} for tenant: {}", 
                                currentPath, tenantIdentifier);
                    }
                }
            }
            
            log.info("🐘 PostgreSQL connection configured for tenant '{}' with schema '{}'", 
                    tenantIdentifier, schema);
            
            return connection;
            
        } catch (SQLException e) {
            log.error("Failed to configure PostgreSQL connection for tenant: {}", tenantIdentifier, e);
            if (connection != null) {
                try {
                    connection.close();
                } catch (SQLException closeEx) {
                    log.warn("Failed to close connection after error: {}", closeEx.getMessage());
                }
            }
            throw new SQLException("Failed to configure PostgreSQL connection for tenant: " + tenantIdentifier, e);
        }
    }    @Override
    public void releaseConnection(String tenantIdentifier, Connection connection) throws SQLException {
        if (connection != null && !connection.isClosed()) {
            try {                // Reset to default search_path before releasing
                try (var stmt = connection.createStatement()) {
                    stmt.execute("SET search_path TO \"public\"");
                }
                log.debug("🐘 Reset search_path to public for tenant: {}", tenantIdentifier);
            } catch (SQLException e) {
                log.warn("Failed to reset search_path for tenant {}: {}", tenantIdentifier, e.getMessage());
            } finally {
                try {
                    connection.close();
                } catch (SQLException e) {
                    log.error("Failed to close connection for tenant {}: {}", tenantIdentifier, e.getMessage());
                }
            }
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
    }    /**
     * PostgreSQL'de schema'nın var olduğundan emin ol
     */
    private void ensureSchemaExists(Connection connection, String schema) throws SQLException {
        if (!"public".equals(schema)) {
            try (var stmt = connection.createStatement()) {
                String createSchemaSql = "CREATE SCHEMA IF NOT EXISTS \"" + schema + "\"";
                stmt.execute(createSchemaSql);
                log.debug("🐘 Ensured PostgreSQL schema exists: {}", schema);
            }
        }
    }
}
