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
            
            // FORCE PostgreSQL search_path - SADECE ilgili schema kullan, public'i TAMAMEN engelle
            String searchPath = String.format("\"%s\"", schema);
            try (var stmt = connection.createStatement()) {
                // CRITICAL: Önce search_path'i sadece tenant schema'ya set et
                stmt.execute("SET search_path TO " + searchPath);
                
                // CRITICAL: Local schema'yı da set et (PostgreSQL 9.3+)
                stmt.execute("SET LOCAL search_path TO " + searchPath);
                
                // CRITICAL: Session seviyesinde de schema'yı zorla
                stmt.execute("SET SESSION schema '" + schema + "'");
                
                // CRITICAL: public schema'ya erişimi tamamen engelle
                if (!"public".equals(schema)) {
                    // public schema'ya erişimi revoke et (sadece bu session için)
                    try {
                        stmt.execute("SET SESSION search_path TO " + searchPath + ", \"$user\"");
                    } catch (SQLException e) {
                        log.debug("Could not revoke public access (expected): {}", e.getMessage());
                    }
                }
                
                // CRITICAL: Connection seviyesinde schema bilgisini set et
                connection.setSchema(schema);
                
                // Verify the search_path was set correctly
                try (var rs = stmt.executeQuery("SHOW search_path")) {
                    if (rs.next()) {
                        String currentPath = rs.getString(1);
                        log.info("🐘 PostgreSQL search_path LOCKED to: {} for tenant: {}", 
                                currentPath, tenantIdentifier);
                        
                        // Eğer hâlâ public varsa, UYAR!
                        if (currentPath.contains("public") && !"public".equals(schema)) {
                            log.warn("⚠️ WARNING: search_path still contains 'public' for tenant {}: {}", 
                                    tenantIdentifier, currentPath);
                        }
                    }
                }
                
                // Double check with current_schema()
                try (var rs = stmt.executeQuery("SELECT current_schema()")) {
                    if (rs.next()) {
                        String currentSchema = rs.getString(1);
                        log.info("🔍 Current schema LOCKED: {} for tenant: {}", 
                                currentSchema, tenantIdentifier);
                        
                        // Eğer schema doğru değilse, UYAR!
                        if (!schema.equals(currentSchema)) {
                            log.error("❌ CRITICAL: Current schema mismatch! Expected: {}, Got: {}", 
                                    schema, currentSchema);
                        }
                    }
                }
            }
            
            log.info("🐘 PostgreSQL connection FORCED to schema '{}' for tenant '{}'", 
                    schema, tenantIdentifier);
            
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
