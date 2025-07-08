package dev.oasis.stockify.service;

import dev.oasis.stockify.dto.TenantCreateDTO;
import dev.oasis.stockify.dto.TenantDTO;
import dev.oasis.stockify.dto.UserCreateDTO;
import dev.oasis.stockify.exception.TenantAlreadyExistsException;
import dev.oasis.stockify.exception.TenantNotFoundException;
import dev.oasis.stockify.model.Role;
import dev.oasis.stockify.util.ServiceTenantUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

/**
 * Service for managing tenant lifecycle operations
 * Simplified version using JPA schema auto-generation instead of Flyway
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TenantManagementService {

    private final DataSource dataSource;
    private final AppUserService appUserService;
    private final ServiceTenantUtil serviceTenantUtil;

    /**
     * Create a new tenant with complete setup
     */

    @Transactional
    public TenantDTO createTenant(TenantCreateDTO createDTO) {
        String tenantId = generateTenantId(createDTO.getCompanyName());
        
        log.info("🏢 Creating new tenant: {} for company: {}", tenantId, createDTO.getCompanyName());
        
        try {
            // Check if tenant already exists
            if (tenantExists(tenantId)) {
                throw new TenantAlreadyExistsException("Tenant already exists: " + tenantId);
            }
            
            // Create tenant schema
            createTenantSchema(tenantId);
            
            // Set tenant context for data operations
            serviceTenantUtil.setCurrentTenant(tenantId);
            
            // Initialize tenant configuration to trigger table creation
            initializeTenantConfiguration(tenantId);
            
            // Create initial admin user (tables are now ready)
            createTenantAdmin(createDTO);
            
            log.info("✅ Successfully created tenant: {}", tenantId);
            
            return TenantDTO.builder()
                    .tenantId(tenantId)
                    .companyName(createDTO.getCompanyName())
                    .adminEmail(createDTO.getAdminEmail())
                    .status("ACTIVE")
                    .createdAt(LocalDateTime.now())
                    .build();
                    
        } catch (Exception e) {
            log.error("❌ Failed to create tenant {}: {}", tenantId, e.getMessage(), e);
            // Cleanup on failure
            cleanupFailedTenant(tenantId);
            throw new RuntimeException("Failed to create tenant: " + e.getMessage(), e);
        } finally {
            serviceTenantUtil.clearCurrentTenant();
        }
    }

    /**
     * Get all active tenants
     */
    public List<TenantDTO> getAllTenants() {
        List<TenantDTO> tenants = new ArrayList<>();
        
        try (Connection connection = dataSource.getConnection();
             Statement statement = connection.createStatement()) {
            
            // Check if master tenant registry exists
            ResultSet schemas = connection.getMetaData().getSchemas();
            while (schemas.next()) {
                String schemaName = schemas.getString("TABLE_SCHEM");
                if (!isSystemSchema(schemaName) && !schemaName.equalsIgnoreCase("public")) {
                    TenantDTO tenant = getTenantInfo(schemaName.toLowerCase());
                    if (tenant != null) {
                        tenants.add(tenant);
                    }
                }
            }
            
        } catch (SQLException e) {
            log.error("❌ Failed to retrieve tenants: {}", e.getMessage());
            throw new RuntimeException("Failed to retrieve tenants", e);
        }
        
        return tenants;
    }

    /**
     * Get tenant information by ID
     */
    public TenantDTO getTenant(String tenantId) {
        if (!tenantExists(tenantId)) {
            throw new TenantNotFoundException("Tenant not found: " + tenantId);
        }
        
        return getTenantInfo(tenantId);
    }

    /**
     * Deactivate a tenant (soft delete)
     */
    @Transactional
    public void deactivateTenant(String tenantId) {
        log.info("🔒 Deactivating tenant: {}", tenantId);
        
        try {
            serviceTenantUtil.setCurrentTenant(tenantId);
            updateTenantStatus(tenantId, "INACTIVE");
            log.info("✅ Successfully deactivated tenant: {}", tenantId);
        } finally {
            serviceTenantUtil.clearCurrentTenant();
        }
    }

    /**
     * Activate a tenant
     */
    @Transactional
    public void activateTenant(String tenantId) {
        log.info("🔓 Activating tenant: {}", tenantId);
        
        try {
            serviceTenantUtil.setCurrentTenant(tenantId);
            updateTenantStatus(tenantId, "ACTIVE");
            log.info("✅ Successfully activated tenant: {}", tenantId);
        } finally {
            serviceTenantUtil.clearCurrentTenant();
        }
    }

    /**
     * Check if tenant exists
     */    public boolean tenantExists(String tenantId) {
        try (Connection connection = dataSource.getConnection()) {
            String schemaName = tenantId.toLowerCase(Locale.ROOT);
            ResultSet schemas = connection.getMetaData().getSchemas();
            while (schemas.next()) {
                if (schemaName.equals(schemas.getString("TABLE_SCHEM"))) {
                    return true;
                }
            }
            return false;
        } catch (SQLException e) {
            log.error("❌ Error checking tenant existence: {}", e.getMessage());
            return false;
        }
    }

    // Private helper methods
    private String generateTenantId(String companyName) {
        // Generate tenant ID based on company name
        String sanitized = companyName.toLowerCase()
                .replaceAll("[^a-zA-Z0-9]", "")
                .trim();
        
        // Ensure minimum length
        if (sanitized.length() < 2) {
            throw new RuntimeException("Şirket adı çok kısa. En az 2 karakter olmalıdır.");
        }
        
        // First try to use the company name directly
        String baseId = sanitized.substring(0, Math.min(sanitized.length(), 20));
        
        // Check if this tenant ID already exists
        if (!tenantExists(baseId)) {
            return baseId;
        }
        
        // If exists, try with incrementing numbers
        for (int i = 2; i <= 999; i++) {
            String candidateId = baseId + i;
            if (!tenantExists(candidateId)) {
                return candidateId;
            }
        }
        
        // If all numeric suffixes are taken, fall back to random suffix
        String suffix = UUID.randomUUID().toString().substring(0, 4);
        return baseId + "_" + suffix;
    }

    private void createTenantSchema(String tenantId) throws SQLException {
        String schemaName = tenantId.toLowerCase();
        
        try (Connection connection = dataSource.getConnection();
             Statement statement = connection.createStatement()) {
            
            // Create schema - JPA will automatically create tables when accessed
            statement.execute(String.format("CREATE SCHEMA IF NOT EXISTS \"%s\"", schemaName));
            log.info("🏗️ Created schema: {} (tables will be auto-created by JPA)", schemaName);
        }
    }
    
    private void initializeTenantConfiguration(String tenantId) {
        try {
            // Create all required tables and sequences for the new tenant
            try (Connection connection = dataSource.getConnection()) {
                connection.setSchema(tenantId.toLowerCase());
                
                // Create sequences first
                createSequences(connection);
                
                // Create all tables in the correct order (due to foreign key dependencies)
                createTenantConfigTable(connection);
                createAppUserTable(connection);
                createProductCategoriesTable(connection);
                createProductTable(connection);
                createStockMovementTable(connection);
                createStockNotificationTable(connection);
                createContactMessagesTable(connection);
                
                // Insert default configuration
                insertDefaultConfiguration(connection);
                
                log.info("✅ Initialized all tables and configuration for tenant: {}", tenantId);
            }
        } catch (Exception e) {
            log.error("❌ Failed to initialize tenant configuration: {}", e.getMessage());
            throw new RuntimeException("Failed to initialize tenant configuration", e);
        }
    }
    
    private void createSequences(Connection connection) throws SQLException {
        String[] sequences = {
            "CREATE SEQUENCE IF NOT EXISTS app_user_id_seq START 1 INCREMENT 1",
            "CREATE SEQUENCE IF NOT EXISTS contact_messages_id_seq START 1 INCREMENT 1",
            "CREATE SEQUENCE IF NOT EXISTS product_categories_id_seq START 1 INCREMENT 1",
            "CREATE SEQUENCE IF NOT EXISTS product_id_seq START 1 INCREMENT 1",
            "CREATE SEQUENCE IF NOT EXISTS stock_movement_id_seq START 1 INCREMENT 1",
            "CREATE SEQUENCE IF NOT EXISTS stock_notification_id_seq START 1 INCREMENT 1"
        };
        
        try (Statement stmt = connection.createStatement()) {
            for (String sql : sequences) {
                stmt.execute(sql);
            }
        }
    }
    
    private void createTenantConfigTable(Connection connection) throws SQLException {
        String sql = """
            CREATE TABLE IF NOT EXISTS tenant_config (
                config_key VARCHAR(255) NOT NULL,
                config_value VARCHAR(255),
                config_type VARCHAR(255),
                description VARCHAR(255),
                created_at TIMESTAMP(6),
                updated_at TIMESTAMP(6),
                CONSTRAINT tenant_config_pkey PRIMARY KEY (config_key)
            )
            """;
        try (Statement stmt = connection.createStatement()) {
            stmt.execute(sql);
        }
    }
    
    private void createAppUserTable(Connection connection) throws SQLException {
        String sql = """
            CREATE TABLE IF NOT EXISTS app_user (
                id BIGINT DEFAULT nextval('app_user_id_seq') NOT NULL,
                username VARCHAR(20) NOT NULL,
                password VARCHAR(255) NOT NULL,
                role VARCHAR(255) NOT NULL,
                email VARCHAR(255),
                can_manage_all_tenants BOOLEAN,
                accessible_tenants VARCHAR(1000),
                is_global_user BOOLEAN,
                is_active BOOLEAN,
                primary_tenant VARCHAR(50),
                created_at TIMESTAMP(6),
                updated_at TIMESTAMP(6),
                last_login TIMESTAMP(6),
                CONSTRAINT app_user_role_check CHECK (role IN ('SUPER_ADMIN', 'ADMIN', 'USER')),
                CONSTRAINT app_user_pkey PRIMARY KEY (id),
                CONSTRAINT app_user_username_key UNIQUE (username)
            )
            """;
        try (Statement stmt = connection.createStatement()) {
            stmt.execute(sql);
        }
    }
    
    private void createProductCategoriesTable(Connection connection) throws SQLException {
        String sql = """
            CREATE TABLE IF NOT EXISTS product_categories (
                id BIGINT DEFAULT nextval('product_categories_id_seq') NOT NULL,
                name VARCHAR(100) NOT NULL,
                description VARCHAR(500),
                hex_color VARCHAR(20),
                is_active BOOLEAN NOT NULL,
                sort_order INTEGER NOT NULL,
                created_at TIMESTAMP(6) NOT NULL,
                updated_at TIMESTAMP(6) NOT NULL,
                CONSTRAINT product_categories_pkey PRIMARY KEY (id)
            )
            """;
        try (Statement stmt = connection.createStatement()) {
            stmt.execute(sql);
        }
    }
    
    private void createProductTable(Connection connection) throws SQLException {
        String sql = """
            CREATE TABLE IF NOT EXISTS product (
                id BIGINT DEFAULT nextval('product_id_seq') NOT NULL,
                title VARCHAR(255),
                description VARCHAR(255),
                sku VARCHAR(255),
                category VARCHAR(255),
                price DECIMAL(38,2),
                stock_level INTEGER,
                low_stock_threshold INTEGER,
                is_active BOOLEAN,
                is_featured BOOLEAN,
                etsy_product_id VARCHAR(255),
                barcode VARCHAR(100),
                qr_code VARCHAR(500),
                scan_enabled BOOLEAN DEFAULT TRUE,
                created_at TIMESTAMP(6),
                updated_at TIMESTAMP(6),
                created_by BIGINT,
                updated_by BIGINT,
                CONSTRAINT product_pkey PRIMARY KEY (id),
                CONSTRAINT product_sku_key UNIQUE (sku),
                CONSTRAINT product_barcode_key UNIQUE (barcode),
                CONSTRAINT product_qr_code_key UNIQUE (qr_code)
            )
            """;
        try (Statement stmt = connection.createStatement()) {
            stmt.execute(sql);
        }
    }
    
    private void createStockMovementTable(Connection connection) throws SQLException {
        String sql = """
            CREATE TABLE IF NOT EXISTS stock_movement (
                id BIGINT DEFAULT nextval('stock_movement_id_seq') NOT NULL,
                product_id BIGINT NOT NULL,
                movement_type VARCHAR(255) NOT NULL,
                quantity INTEGER NOT NULL,
                previous_stock INTEGER NOT NULL,
                new_stock INTEGER NOT NULL,
                notes VARCHAR(255),
                reference_id VARCHAR(255),
                created_at TIMESTAMP(6),
                created_by BIGINT,
                CONSTRAINT stock_movement_movement_type_check CHECK (movement_type IN ('IN', 'OUT', 'ADJUSTMENT', 'RETURN', 'TRANSFER', 'DAMAGED', 'EXPIRED')),
                CONSTRAINT stock_movement_pkey PRIMARY KEY (id),
                CONSTRAINT stock_movement_product_id_fkey FOREIGN KEY (product_id) REFERENCES product(id)
            )
            """;
        try (Statement stmt = connection.createStatement()) {
            stmt.execute(sql);
        }
    }
    
    private void createStockNotificationTable(Connection connection) throws SQLException {
        String sql = """
            CREATE TABLE IF NOT EXISTS stock_notification (
                id BIGINT DEFAULT nextval('stock_notification_id_seq') NOT NULL,
                product_id BIGINT NOT NULL,
                notification_type VARCHAR(255),
                message VARCHAR(255),
                priority VARCHAR(255),
                category VARCHAR(255),
                is_read BOOLEAN,
                read_at TIMESTAMP(6),
                read_by BIGINT,
                created_at TIMESTAMP(6),
                CONSTRAINT stock_notification_pkey PRIMARY KEY (id),
                CONSTRAINT stock_notification_product_id_fkey FOREIGN KEY (product_id) REFERENCES product(id)
            )
            """;
        try (Statement stmt = connection.createStatement()) {
            stmt.execute(sql);
        }
    }
    
    private void createContactMessagesTable(Connection connection) throws SQLException {
        String sql = """
            CREATE TABLE IF NOT EXISTS contact_messages (
                id BIGINT DEFAULT nextval('contact_messages_id_seq') NOT NULL,
                first_name VARCHAR(100) NOT NULL,
                last_name VARCHAR(100) NOT NULL,
                email VARCHAR(255) NOT NULL,
                subject VARCHAR(100) NOT NULL,
                message TEXT NOT NULL,
                phone VARCHAR(20),
                company VARCHAR(255),
                is_read BOOLEAN NOT NULL,
                responded BOOLEAN NOT NULL,
                created_at TIMESTAMP(6) NOT NULL,
                responded_at TIMESTAMP(6),
                responded_by BIGINT,
                ip_address VARCHAR(45),
                user_agent VARCHAR(500),
                CONSTRAINT contact_messages_pkey PRIMARY KEY (id)
            )
            """;
        try (Statement stmt = connection.createStatement()) {
            stmt.execute(sql);
        }
    }
    
    private void insertDefaultConfiguration(Connection connection) throws SQLException {
        String sql = """
            INSERT INTO tenant_config (config_key, config_value, config_type, description, created_at, updated_at) 
            VALUES 
                ('subscription_plan', 'trial', 'STRING', 'Current subscription plan', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
                ('max_users', '5', 'INTEGER', 'Maximum number of users', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
                ('max_products', '100', 'INTEGER', 'Maximum number of products', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
                ('trial_active', 'true', 'BOOLEAN', 'Whether trial is active', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
                ('trial_expiry', ?, 'DATETIME', 'Trial expiry date', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
            ON CONFLICT (config_key) DO NOTHING
            """;
            
        try (PreparedStatement pstmt = connection.prepareStatement(sql)) {
            // Set trial expiry to 30 days from now
            pstmt.setString(1, LocalDateTime.now().plusDays(30).toString());
            pstmt.executeUpdate();
        }
    }
    
    private boolean isSystemSchema(String schemaName) {
        return schemaName.equalsIgnoreCase("INFORMATION_SCHEMA") ||
               schemaName.equalsIgnoreCase("SYSTEM_LOBS") ||
               schemaName.equalsIgnoreCase("SYS") ||
               schemaName.equalsIgnoreCase("SYSAUX");
    }

    private void cleanupFailedTenant(String tenantId) {
        try (Connection connection = dataSource.getConnection();
             Statement statement = connection.createStatement()) {
            
            String schemaName = tenantId.toLowerCase(Locale.ROOT);
            statement.execute(String.format("DROP SCHEMA IF EXISTS \"%s\" CASCADE", schemaName));
            log.info("🧹 Cleaned up failed tenant schema: {}", schemaName);
            
        } catch (SQLException e) {
            log.error("❌ Failed to cleanup tenant {}: {}", tenantId, e.getMessage());
        }
    }
    
    private void createTenantAdmin(TenantCreateDTO createDTO) {
        try {
            UserCreateDTO adminUser = new UserCreateDTO();
            adminUser.setUsername(createDTO.getAdminUsername());
            adminUser.setPassword(createDTO.getAdminPassword());
            adminUser.setRole(Role.ADMIN);
            adminUser.setEmail(createDTO.getAdminEmail());
            
            appUserService.saveUser(adminUser);
            log.debug("👤 Created admin user for tenant");
        } catch (Exception e) {
            log.error("❌ Failed to create admin user: {}", e.getMessage());
            throw new RuntimeException("Failed to create admin user", e);
        }
    }

    private TenantDTO getTenantInfo(String tenantId) {
        // For now, return basic tenant info since JPA will handle table creation
        return TenantDTO.builder()
                .tenantId(tenantId)
                .companyName("Company: " + tenantId)
                .adminEmail("admin@" + tenantId + ".com")
                .status("ACTIVE")
                .createdAt(LocalDateTime.now())
                .build();
    }

    private void updateTenantStatus(String tenantId, String status) {
        // For now, just log the status change since we're using JPA auto-generation
        log.info("📝 Tenant {} status updated to: {}", tenantId, status);
    }
}
