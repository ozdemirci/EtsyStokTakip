package dev.oasis.stockify.config;

import dev.oasis.stockify.config.tenant.TenantContext;
import dev.oasis.stockify.dto.ProductCreateDTO;
import dev.oasis.stockify.dto.UserCreateDTO;
import dev.oasis.stockify.repository.AppUserRepository;
import dev.oasis.stockify.repository.ProductRepository;
import dev.oasis.stockify.service.AppUserService;
import dev.oasis.stockify.service.ProductService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import javax.sql.DataSource;
import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

/**
 * Multi-Tenant Data Loader Component
 * This component initializes sample data for multiple tenants in the Stockify application.
 * It populates existing tenant schemas (created by Flyway) with initial data including:
 * - Administrative users with proper roles
 * - Sample products with realistic inventory data
 * - Tenant-specific configurations
 * 
 * The data loader runs only in 'dev' profile and ensures complete isolation
 * between tenant data while maintaining consistent data structure.
 * Schema and table creation is handled by MultiTenantFlywayConfig.
 * 
 * @author Stockify Team
 * @version 1.0
 * @since 2025
 */
@Slf4j
@Component
@Profile("dev") // Re-enabled after fixing schema case issues
@Order(2) // Run after MultiTenantFlywayConfig (1)
@RequiredArgsConstructor
public class DataLoader implements CommandLineRunner {
    
    private final DataSource dataSource;
    private final AppUserService appUserService;
    private final ProductService productService;
    private final AppUserRepository appUserRepository;
    private final ProductRepository productRepository;    // Configuration for tenant setup - Real company-based tenant schemas
    // Note: 'stockify' tenant is reserved for super admin and created by SuperAdminInitializer
    private static final List<String> TENANT_IDS = Arrays.asList(
        "public", "stockify", "acme_corp", "global_trade", "artisan_crafts", "tech_solutions"
    );// Sample data configurations - 3 users per tenant
    private static final List<SampleUser> SAMPLE_USERS = Arrays.asList(
        new SampleUser("admin", "admin123", "ADMIN"),
        new SampleUser("operator", "operator123", "USER"),
        new SampleUser("manager", "manager123", "USER")
    );    private static final List<SampleProduct> SAMPLE_PRODUCTS = Arrays.asList(
        // Electronics category
        new SampleProduct("ELEC-001", "Wireless Bluetooth Headphones", "High-quality wireless headphones with noise cancellation", "Electronics", "149.99", 35, 5),
        new SampleProduct("ELEC-002", "USB-C Charging Cable", "Fast charging USB-C cable with durable braided design", "Electronics", "19.99", 100, 10),
        new SampleProduct("ELEC-003", "Smartphone Stand", "Adjustable aluminum smartphone stand for desk use", "Electronics", "29.99", 50, 8),
        
        // Home & Garden category
        new SampleProduct("HOME-001", "Ceramic Coffee Mug", "Beautiful handcrafted ceramic mug with unique design", "Home & Garden", "24.99", 40, 5),
        new SampleProduct("HOME-002", "Wooden Cutting Board", "Premium bamboo cutting board with juice groove", "Home & Garden", "34.99", 25, 3),
        new SampleProduct("HOME-003", "LED Desk Lamp", "Modern adjustable LED desk lamp with touch control", "Home & Garden", "79.99", 20, 2),
        
        // Clothing category
        new SampleProduct("CLOTH-001", "Cotton T-Shirt", "Comfortable 100% cotton t-shirt in various colors", "Clothing", "19.99", 75, 10),
        new SampleProduct("CLOTH-002", "Denim Jeans", "Classic fit denim jeans with premium quality", "Clothing", "59.99", 30, 5),
        new SampleProduct("CLOTH-003", "Wool Sweater", "Warm and cozy wool sweater for winter", "Clothing", "89.99", 15, 3),
        
        // Books category
        new SampleProduct("BOOK-001", "Programming Guide", "Complete guide to modern programming techniques", "Books", "39.99", 45, 5),
        new SampleProduct("BOOK-002", "Business Strategy", "Essential business strategy and management principles", "Books", "29.99", 60, 8),
        new SampleProduct("BOOK-003", "Cooking Recipes", "Collection of delicious and easy cooking recipes", "Books", "24.99", 35, 4)
    );    @Override
    public void run(String... args)  {
        log.info("🚀 Starting Multi-Tenant Data Loader...");
          try {
            // Initialize data for each tenant
            for (String tenantId : TENANT_IDS) {
                log.info("🔄 Processing tenant: {}", tenantId);
                initializeTenantData(tenantId);
                log.info("✅ Completed processing tenant: {}", tenantId);
            }
              log.info("✅ Multi-Tenant Data Loader completed successfully!");
            log.info("📊 Initialized {} tenants with sample data", TENANT_IDS.size());
            log.info("👥 Each tenant has {} users and {} products", SAMPLE_USERS.size(), SAMPLE_PRODUCTS.size());
            log.info("🔑 Public tenant also has a SuperAdmin user with full privileges");
            log.warn("⚠️ Remember to change default passwords in production!");
            } catch (Exception e) {
            log.error("❌ Error during data loading: {}", e.getMessage(), e);
            throw e;
        } finally {
            // Always clear tenant context
            TenantContext.clear();
        }
    }    /**
     * Initialize data for a specific tenant
     */
    @Transactional
    private void initializeTenantData(String tenantId) {
        log.info("🏢 Initializing data for tenant: {}", tenantId);
        
        try {
            // Set tenant context for this initialization
            TenantContext.setCurrentTenant(tenantId);
            log.debug("🔄 Set tenant context to: {}", tenantId);
              // Special handling for 'stockify' tenant (super admin tenant)
            if ("stockify".equals(tenantId)) {
                log.info("🏛️ Stockify platform tenant - super admin already created by SuperAdminInitializer");
                initializeTenantConfig(tenantId);
                return;
            }
            
            // Special handling for 'public' tenant (default tenant) - create superadmin
            if ("public".equals(tenantId)) {
                log.info("🌐 Public tenant - initializing default tenant data with superadmin");
                // Check if superadmin already exists
                if (!appUserRepository.findByUsername("superadmin").isPresent()) {
                    createSuperAdminForPublicTenant();
                } else {
                    log.info("🔑 SuperAdmin already exists in public tenant");
                }
            }
            
            // Check if data already exists to avoid duplicates
            if (isDataAlreadyLoaded(tenantId)) {
                log.info("📋 Data already exists for tenant: {}, skipping initialization", tenantId);
                return;
            }
            
            // Initialize users
            initializeTenantUsers(tenantId);
            
            // Initialize products
            initializeTenantProducts(tenantId);
            
            // Initialize tenant-specific configurations
            initializeTenantConfig(tenantId);
            
            log.info("✨ Successfully initialized tenant: {}", tenantId);
            
        } catch (Exception e) {
            log.error("❌ Failed to initialize tenant {}: {}", tenantId, e.getMessage(), e);
            throw new RuntimeException("Failed to initialize tenant: " + tenantId, e);
        } finally {
            // Clear tenant context after processing this tenant
            TenantContext.clear();
            log.debug("🧹 Cleared tenant context for: {}", tenantId);
        }
    }    /**
     * Check if data is already loaded for the tenant
     */
    @Transactional(readOnly = true)
    private boolean isDataAlreadyLoaded(String tenantId) {
        try {
            // Set tenant context to check in the correct schema
            TenantContext.setCurrentTenant(tenantId);
            log.debug("🔍 Checking if data already loaded for tenant: {}", tenantId);
            
            // For public tenant, check both superadmin and admin users
            if ("public".equals(tenantId)) {
                boolean superAdminExists = appUserRepository.findByUsername("superadmin").isPresent();
                boolean adminExists = appUserRepository.findByUsername("admin").isPresent();
                
                if (superAdminExists && adminExists) {
                    log.debug("🔍 SuperAdmin and Admin users already exist for public tenant");
                    return true;
                }
                log.debug("🔍 Public tenant users - SuperAdmin: {}, Admin: {}", superAdminExists, adminExists);
                return false;
            } else {
                // For other tenants, check if admin user exists
                boolean adminExists = appUserRepository.findByUsername("admin").isPresent();
                log.debug("🔍 Tenant {} - Admin user exists: {}", tenantId, adminExists);
                return adminExists;
            }
            
        } catch (Exception e) {
            // If there's an error checking, assume data doesn't exist
            log.debug("🔍 Could not check existing data for tenant {}, proceeding with initialization: {}", tenantId, e.getMessage());
            return false;
        }
    }    /**
     * Initialize users for the tenant
     */
    @Transactional
    private void initializeTenantUsers(String tenantId) {
        log.info("👥 Creating users for tenant: {}", tenantId);
        
        int createdUserCount = 0;
        for (SampleUser sampleUser : SAMPLE_USERS) {
            try {
                // Ensure tenant context is set
                TenantContext.setCurrentTenant(tenantId);
                log.debug("🔄 Set tenant context to: {} for user: {}", tenantId, sampleUser.username);
                
                // Check if user already exists in this tenant
                boolean userExists = appUserRepository.findByUsername(sampleUser.username).isPresent();
                log.debug("🔍 User {} exists in tenant {}: {}", sampleUser.username, tenantId, userExists);
                
                if (userExists) {
                    log.debug("👤 User {} already exists for tenant {}, skipping", sampleUser.username, tenantId);
                    continue;
                }
                
                // Create user DTO
                UserCreateDTO userDTO = new UserCreateDTO();
                userDTO.setUsername(sampleUser.username);
                userDTO.setPassword(sampleUser.password);
                userDTO.setRole(sampleUser.role);
                
                log.debug("💾 Saving user: {} with role: {} for tenant: {}", 
                    sampleUser.username, sampleUser.role, tenantId);
                
                // Save user through service
                appUserService.saveUser(userDTO);
                createdUserCount++;
                
                log.info("✅ Created user: {} with role: {} for tenant: {}", 
                    sampleUser.username, sampleUser.role, tenantId);
                
            } catch (Exception e) {
                log.error("❌ Failed to create user {} for tenant {}: {}", 
                    sampleUser.username, tenantId, e.getMessage(), e);
                // Continue with other users instead of failing completely
            }
        }
        
        log.info("👥 Successfully created {} users for tenant: {}", createdUserCount, tenantId);
    }    /**
     * Initialize products for the tenant
     */
    @Transactional
    private void initializeTenantProducts(String tenantId) {
        log.info("📦 Creating products for tenant: {}", tenantId);
        
        int createdProductCount = 0;
        for (SampleProduct sampleProduct : SAMPLE_PRODUCTS) {
            try {
                // Ensure tenant context is set
                TenantContext.setCurrentTenant(tenantId);
                
                // Check if product already exists in this tenant
                if (productRepository.findBySku(sampleProduct.sku).isPresent()) {
                    log.debug("📦 Product {} already exists for tenant {}, skipping", sampleProduct.sku, tenantId);
                    continue;
                }
                
                // Create product DTO
                ProductCreateDTO productDTO = new ProductCreateDTO();
                productDTO.setSku(sampleProduct.sku);
                productDTO.setTitle(sampleProduct.title);
                productDTO.setDescription(sampleProduct.description);
                productDTO.setCategory(sampleProduct.category);
                productDTO.setPrice(new BigDecimal(sampleProduct.price));
                productDTO.setStockLevel(sampleProduct.stockLevel);
                productDTO.setLowStockThreshold(sampleProduct.lowStockThreshold);
                
                // Set external product ID (simulate external integration)
                productDTO.setEtsyProductId("EXT_" + tenantId.toUpperCase(Locale.ROOT) + "_" + sampleProduct.sku);
                
                // Save product through service
                productService.saveProduct(productDTO);
                createdProductCount++;
                
                log.info("✅ Created product: {} for tenant: {}", sampleProduct.title, tenantId);
                
            } catch (Exception e) {
                log.error("❌ Failed to create product {} for tenant {}: {}", 
                    sampleProduct.sku, tenantId, e.getMessage());
                // Continue with other products instead of failing completely
            }
        }
        
        log.info("📦 Successfully created {} products for tenant: {}", createdProductCount, tenantId);
    }

    /**
     * Initialize tenant-specific configurations
     */
    private void initializeTenantConfig(String tenantId) {        log.info("⚙️ Setting up configuration for tenant: {}", tenantId);
          try (Connection connection = dataSource.getConnection();
             Statement statement = connection.createStatement()) {
            
            // Use the correct schema name mapping (same as SchemaMultiTenantConnectionProvider)
            String schemaName = mapTenantToSchema(tenantId);
            connection.setSchema(schemaName);
            
            // Insert tenant-specific configurations
            String[] configInserts = {
                String.format("INSERT INTO tenant_config (config_key, config_value, config_type, description) VALUES " +
                    "('tenant_name', '%s', 'STRING', 'Display name for the tenant') ON CONFLICT (config_key) DO NOTHING", 
                    getTenantDisplayName(tenantId)),
                    
                "INSERT INTO tenant_config (config_key, config_value, config_type, description) VALUES " +
                    "('low_stock_email_enabled', 'true', 'BOOLEAN', 'Enable email notifications for low stock') ON CONFLICT (config_key) DO NOTHING",
                    
                "INSERT INTO tenant_config (config_key, config_value, config_type, description) VALUES " +
                    "('default_low_stock_threshold', '5', 'INTEGER', 'Default threshold for low stock alerts') ON CONFLICT (config_key) DO NOTHING",
                    
                "INSERT INTO tenant_config (config_key, config_value, config_type, description) VALUES " +
                    "('currency', 'USD', 'STRING', 'Default currency for pricing') ON CONFLICT (config_key) DO NOTHING",
                    
                "INSERT INTO tenant_config (config_key, config_value, config_type, description) VALUES " +
                    "('timezone', 'UTC', 'STRING', 'Default timezone for the tenant') ON CONFLICT (config_key) DO NOTHING"
            };
            
            for (String configSQL : configInserts) {
                statement.execute(configSQL);
            }
            
            log.info("⚙️ Configuration completed for tenant: {}", tenantId);
            
        } catch (SQLException e) {
            log.warn("⚠️ Could not initialize config for tenant {}: {}", tenantId, e.getMessage());
            // Non-critical error, continue processing
        }
    }    /**
     * Get display name for tenant
     */
    private String getTenantDisplayName(String tenantId) {
        return switch (tenantId.toLowerCase()) {
            case "public" -> "Default Public Tenant";
            case "stockify" -> "Stockify Platform (Super Admin)";
            case "acme_corp" -> "ACME Corporation";
            case "global_trade" -> "Global Trade Solutions";
            case "artisan_crafts" -> "Artisan Crafts Co.";
            case "tech_solutions" -> "Tech Solutions Inc.";
            case "demo" -> "Demo Company";
            case "test" -> "Test Environment";
            default -> "Tenant " + tenantId.toUpperCase(Locale.ROOT);
        };
    }/**
     * Map tenant identifier to actual schema name in database
     * This handles the difference between logical tenant names and physical schema names
     */
    private String mapTenantToSchema(String tenantIdentifier) {
        if (tenantIdentifier == null) {
            return "public";
        }
        
        // All schema names in lowercase for consistency with H2 settings
        return tenantIdentifier.toLowerCase(Locale.ROOT);    }    /**
     * Create SuperAdmin user for public tenant
     * This creates a superadmin user with SUPER_ADMIN role in the public tenant
     */
    @Transactional
    private void createSuperAdminForPublicTenant() {
        try {
            log.info("🔑 Creating SuperAdmin user for public tenant");
            
            // Ensure we're in public tenant context
            TenantContext.setCurrentTenant("public");
            
            // Create superadmin user DTO
            UserCreateDTO superAdminDto = new UserCreateDTO();
            superAdminDto.setUsername("superadmin");
            superAdminDto.setPassword("superadmin123"); // Strong password - should be changed in production
            superAdminDto.setRole("SUPER_ADMIN");
            
            // Create the superadmin user
            appUserService.saveUser(superAdminDto);
            
            log.info("✅ Successfully created SuperAdmin user for public tenant");
            log.warn("⚠️ Default SuperAdmin password is 'superadmin123' - CHANGE THIS IN PRODUCTION!");
            
        } catch (Exception e) {
            log.error("❌ Failed to create SuperAdmin user for public tenant: {}", e.getMessage(), e);
            throw new RuntimeException("Failed to create SuperAdmin for public tenant", e);
        }
    }

    /**
     * Sample user data structure
     */
    private static class SampleUser {
        final String username;
        final String password;
        final String role;

        SampleUser(String username, String password, String role) {
            this.username = username;
            this.password = password;
            this.role = role;
        }
    }

    /**
     * Sample product data structure
     */
    private static class SampleProduct {
        final String sku;
        final String title;
        final String description;
        final String category;
        final String price;
        final int stockLevel;
        final int lowStockThreshold;

        SampleProduct(String sku, String title, String description, String category, 
                     String price, int stockLevel, int lowStockThreshold) {
            this.sku = sku;
            this.title = title;
            this.description = description;
            this.category = category;
            this.price = price;
            this.stockLevel = stockLevel;
            this.lowStockThreshold = lowStockThreshold;
        }
    }
}
