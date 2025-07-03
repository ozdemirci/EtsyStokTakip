package dev.oasis.stockify.service;

import dev.oasis.stockify.model.AppUser;
import dev.oasis.stockify.model.Role;
import dev.oasis.stockify.model.ContactMessage;
import dev.oasis.stockify.repository.AppUserRepository;
import dev.oasis.stockify.repository.ContactMessageRepository;
import dev.oasis.stockify.util.ServiceTenantUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.annotation.Order;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.ResultSet;
import java.sql.Statement;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;

/**
 * Database initialization service that creates tenant schemas and waits for JPA to create tables
 * Then initializes data after tables are available
 */
@Service
@RequiredArgsConstructor
@Slf4j
@Order(1)
public class DatabaseInitializationService implements CommandLineRunner {

    private final DataSource dataSource;
    private final AppUserRepository appUserRepository;
    private final ContactMessageRepository contactMessageRepository;
    private final PasswordEncoder passwordEncoder;
    private final ServiceTenantUtil serviceTenantUtil;

    // List of tenant schemas to create
    private final List<String> tenantSchemas = Arrays.asList("public", "com", "rezonans");

    @Override
    public void run(String... args) throws Exception {
        log.info("🚀 Starting database initialization...");
        
        try {
            // 1. Create schemas first
            createSchemas();
            
            // 2. Create tables in all schemas
            createTablesInAllSchemas();
            
            // 3. Wait for JPA to be ready, then initialize data
            initializeDataWithRetry();
            
            log.info("✅ Database initialization completed successfully!");
            
        } catch (Exception e) {
            log.error("❌ Database initialization failed: {}", e.getMessage(), e);
            // Don't throw exception to allow app to start
            log.warn("⚠️ App will continue without initial data");
        }
    }

    /**
     * Initialize data with retry mechanism - waits for JPA to create tables
     */
    private void initializeDataWithRetry() {
        log.info("⏳ Waiting for JPA to create tables before initializing data...");
        
        // Try to initialize data with retries to wait for JPA table creation
        int maxRetries = 10;
        int retryDelay = 2000; // 2 seconds
        
        for (int attempt = 1; attempt <= maxRetries; attempt++) {
            try {
                log.info("📊 Attempting data initialization (attempt {}/{})", attempt, maxRetries);
                
                // Check if tables exist and initialize data
                if (tablesExist()) {
                    initializeData();
                    log.info("✅ Data initialization successful on attempt {}", attempt);
                    return;
                } else {
                    log.info("⏳ Tables not yet created by JPA, waiting...");
                }
                
                // Wait before next attempt
                if (attempt < maxRetries) {
                    Thread.sleep(retryDelay);
                }
                
            } catch (Exception e) {
                log.warn("⚠️ Attempt {} failed: {}", attempt, e.getMessage());
                
                if (attempt < maxRetries) {
                    try {
                        Thread.sleep(retryDelay);
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        break;
                    }
                } else {
                    log.error("❌ All {} attempts failed. Data initialization skipped.", maxRetries);
                }
            }
        }
        
        log.warn("⚠️ Data initialization skipped - tables may not be ready yet");
    }

    /**
     * Check if required tables exist in all tenant schemas
     */
    private boolean tablesExist() {
        try (Connection connection = dataSource.getConnection()) {
            DatabaseMetaData metaData = connection.getMetaData();
            
            // Check for app_user table in all schemas
            for (String schema : tenantSchemas) {
                try (ResultSet rs = metaData.getTables(null, schema, "app_user", new String[]{"TABLE"})) {
                    if (!rs.next()) {
                        log.debug("❌ Table app_user does not exist yet in {} schema", schema);
                        return false;
                    }
                }
            }
            
            log.debug("✓ Tables exist in all schemas");
            return true;
            
        } catch (Exception e) {
            log.debug("❌ Error checking table existence: {}", e.getMessage());
            return false;
        }
    }

    /**
     * Initialize data for all tenants
     */
    private void initializeData() {
        log.info("📊 Initializing database data...");
        
        for (String tenant : tenantSchemas) {
            try {
                log.info("🔄 Initializing data for tenant: {}", tenant);
                serviceTenantUtil.setCurrentTenant(tenant);
                
                if ("public".equals(tenant)) {
                    initializePublicSchemaData();
                } else {
                    initializeTenantSpecificData(tenant);
                }
                
                log.info("✅ Data initialized for tenant: {}", tenant);
                
            } catch (Exception e) {
                log.error("❌ Error initializing data for tenant {}: {}", tenant, e.getMessage());
                // Continue with other tenants
            } finally {
                serviceTenantUtil.clearCurrentTenant();
            }
        }
    }

    /**
     * Initialize public schema data (global data)
     */
    @Transactional
    private void initializePublicSchemaData() {
        log.info("🏗️ Initializing PUBLIC schema with global data...");
        
        // Create SUPER_ADMIN user in public schema
        if (!appUserRepository.existsByUsername("superadmin")) {
            AppUser superAdmin = new AppUser();
            superAdmin.setUsername("superadmin");
            superAdmin.setEmail("superadmin@stockify.com");
            superAdmin.setPassword(passwordEncoder.encode("SuperAdmin123!"));
            superAdmin.setRole(Role.SUPER_ADMIN);
            superAdmin.setIsActive(true);
            superAdmin.setCreatedAt(LocalDateTime.now());
            superAdmin.setUpdatedAt(LocalDateTime.now());
            
            appUserRepository.save(superAdmin);
            log.info("👑 Created SUPER_ADMIN user: superadmin");
        } else {
            log.info("👑 SUPER_ADMIN user already exists");
        }
        
        // Create sample contact message
        if (contactMessageRepository.count() == 0) {
            ContactMessage message = new ContactMessage();
            message.setFirstName("System");
            message.setLastName("Administrator");
            message.setEmail("admin@stockify.com");
            message.setSubject("Welcome to Stockify");
            message.setMessage("This is a sample contact message created during system initialization.");
            message.setCreatedAt(LocalDateTime.now());
            
            contactMessageRepository.save(message);
            log.info("📧 Created sample contact message");
        } else {
            log.info("📧 Contact messages already exist");
        }
    }

    /**
     * Create all tenant schemas
     */
    private void createSchemas() {
        log.info("📋 Creating database schemas...");
        
        try (Connection connection = dataSource.getConnection()) {
            DatabaseMetaData metaData = connection.getMetaData();
            
            for (String schema : tenantSchemas) {
                // Check if schema exists
                boolean schemaExists = false;
                try (ResultSet rs = metaData.getSchemas()) {
                    while (rs.next()) {
                        if (schema.equalsIgnoreCase(rs.getString("TABLE_SCHEM"))) {
                            schemaExists = true;
                            break;
                        }
                    }
                }
                
                if (!schemaExists) {
                    try (Statement stmt = connection.createStatement()) {
                        stmt.execute("CREATE SCHEMA IF NOT EXISTS " + schema);
                        log.info("✓ Created schema: {}", schema);
                    }
                } else {
                    log.info("✓ Schema already exists: {}", schema);
                }
            }
            
        } catch (Exception e) {
            log.error("❌ Error creating schemas: {}", e.getMessage(), e);
            throw new RuntimeException("Failed to create schemas", e);
        }
    }

    /**
     * Create tables in all tenant schemas using JPA
     */
    private void createTablesInAllSchemas() {
        log.info("🗃️ Creating tables in all tenant schemas...");
        
        for (String schema : tenantSchemas) {
            try {
                log.info("📋 Creating tables in schema: {}", schema);
                createTablesInSchema(schema);
                log.info("✅ Tables created in schema: {}", schema);
            } catch (Exception e) {
                log.error("❌ Failed to create tables in schema {}: {}", schema, e.getMessage());
            }
        }
    }

    /**
     * Create tables in a specific schema using raw SQL
     */
    private void createTablesInSchema(String schema) {
        try (Connection connection = dataSource.getConnection();
             Statement stmt = connection.createStatement()) {
            
            // First check if tables already exist
            DatabaseMetaData metaData = connection.getMetaData();
            try (ResultSet rs = metaData.getTables(null, schema, "app_user", new String[]{"TABLE"})) {
                if (rs.next()) {
                    log.info("✓ Tables already exist in schema: {}", schema);
                    return;
                }
            }
            
            // Create tables in the schema
            String[] createTableStatements = {
                "CREATE TABLE IF NOT EXISTS " + schema + ".app_user (" +
                "    id bigserial PRIMARY KEY," +
                "    username varchar(20) NOT NULL UNIQUE," +
                "    password varchar(255) NOT NULL," +
                "    role varchar(255) NOT NULL CHECK (role IN ('SUPER_ADMIN','ADMIN','USER'))," +
                "    email varchar(255)," +
                "    can_manage_all_tenants boolean," +
                "    accessible_tenants varchar(1000)," +
                "    is_global_user boolean," +
                "    is_active boolean," +
                "    primary_tenant varchar(50)," +
                "    created_at timestamp(6)," +
                "    updated_at timestamp(6)," +
                "    last_login timestamp(6)" +
                ")",
                
                "CREATE TABLE IF NOT EXISTS " + schema + ".contact_messages (" +
                "    id bigserial PRIMARY KEY," +
                "    first_name varchar(100) NOT NULL," +
                "    last_name varchar(100) NOT NULL," +
                "    email varchar(255) NOT NULL," +
                "    subject varchar(100) NOT NULL," +
                "    message TEXT NOT NULL," +
                "    phone varchar(20)," +
                "    company varchar(255)," +
                "    is_read boolean NOT NULL," +
                "    responded boolean NOT NULL," +
                "    created_at timestamp(6) NOT NULL," +
                "    responded_at timestamp(6)," +
                "    responded_by bigint," +
                "    ip_address varchar(45)," +
                "    user_agent varchar(500)" +
                ")",
                
                "CREATE TABLE IF NOT EXISTS " + schema + ".product_categories (" +
                "    id bigserial PRIMARY KEY," +
                "    name varchar(100) NOT NULL," +
                "    description varchar(500)," +
                "    hex_color varchar(20)," +
                "    is_active boolean NOT NULL," +
                "    sort_order integer NOT NULL," +
                "    created_at timestamp(6) NOT NULL," +
                "    updated_at timestamp(6) NOT NULL" +
                ")",
                
                "CREATE TABLE IF NOT EXISTS " + schema + ".product (" +
                "    id bigserial PRIMARY KEY," +
                "    title varchar(255)," +
                "    description varchar(255)," +
                "    sku varchar(255) UNIQUE," +
                "    category varchar(255)," +
                "    price numeric(38,2)," +
                "    stock_level integer," +
                "    low_stock_threshold integer," +
                "    is_active boolean," +
                "    is_featured boolean," +
                "    etsy_product_id varchar(255)," +
                "    created_at timestamp(6)," +
                "    updated_at timestamp(6)," +
                "    created_by bigint," +
                "    updated_by bigint" +
                ")",
                
                "CREATE TABLE IF NOT EXISTS " + schema + ".stock_movement (" +
                "    id bigserial PRIMARY KEY," +
                "    product_id bigint NOT NULL," +
                "    movement_type varchar(255) NOT NULL CHECK (movement_type IN ('IN','OUT','ADJUSTMENT','RETURN','TRANSFER','DAMAGED','EXPIRED'))," +
                "    quantity integer NOT NULL," +
                "    previous_stock integer NOT NULL," +
                "    new_stock integer NOT NULL," +
                "    notes varchar(255)," +
                "    reference_id varchar(255)," +
                "    created_at timestamp(6)," +
                "    created_by bigint," +
                "    FOREIGN KEY (product_id) REFERENCES " + schema + ".product(id)" +
                ")",
                
                "CREATE TABLE IF NOT EXISTS " + schema + ".stock_notification (" +
                "    id bigserial PRIMARY KEY," +
                "    product_id bigint NOT NULL," +
                "    notification_type varchar(255)," +
                "    message varchar(255)," +
                "    priority varchar(255)," +
                "    category varchar(255)," +
                "    is_read boolean," +
                "    read_at timestamp(6)," +
                "    read_by bigint," +
                "    created_at timestamp(6)," +
                "    FOREIGN KEY (product_id) REFERENCES " + schema + ".product(id)" +
                ")",
                
                "CREATE TABLE IF NOT EXISTS " + schema + ".tenant_config (" +
                "    config_key varchar(255) PRIMARY KEY," +
                "    config_value varchar(255)," +
                "    config_type varchar(255)," +
                "    description varchar(255)," +
                "    created_at timestamp(6)," +
                "    updated_at timestamp(6)" +
                ")"
            };
            
            for (String sql : createTableStatements) {
                stmt.executeUpdate(sql);
            }
            
            log.info("✅ Successfully created tables in schema: {}", schema);
            
        } catch (Exception e) {
            log.error("❌ Error creating tables in schema {}: {}", schema, e.getMessage());
            throw new RuntimeException("Failed to create tables in schema: " + schema, e);
        }
    }

    /**
     * Initialize tenant-specific data
     */
    @Transactional
    private void initializeTenantSpecificData(String tenant) {
        log.info("🏢 Initializing data for tenant: {}", tenant);
        
        // Create ADMIN user for tenant
        String adminUsername = "admin_" + tenant;
        if (!appUserRepository.existsByUsername(adminUsername)) {
            AppUser admin = new AppUser();
            admin.setUsername(adminUsername);
            admin.setEmail("admin@" + tenant + ".com");
            admin.setPassword(passwordEncoder.encode("Admin123!"));
            admin.setRole(Role.ADMIN);
            admin.setIsActive(true);
            admin.setCreatedAt(LocalDateTime.now());
            admin.setUpdatedAt(LocalDateTime.now());
            
            appUserRepository.save(admin);
            log.info("👨‍💼 Created ADMIN user: {} for tenant: {}", adminUsername, tenant);
        } else {
            log.info("👨‍💼 ADMIN user already exists for tenant: {}", tenant);
        }
        
        // Create USER for tenant
        String userUsername = "user_" + tenant;
        if (!appUserRepository.existsByUsername(userUsername)) {
            AppUser user = new AppUser();
            user.setUsername(userUsername);
            user.setEmail("user@" + tenant + ".com");
            user.setPassword(passwordEncoder.encode("User123!"));
            user.setRole(Role.USER);
            user.setIsActive(true);
            user.setCreatedAt(LocalDateTime.now());
            user.setUpdatedAt(LocalDateTime.now());
            
            appUserRepository.save(user);
            log.info("👤 Created USER: {} for tenant: {}", userUsername, tenant);
        } else {
            log.info("👤 USER already exists for tenant: {}", tenant);
        }
    }
}
