package dev.oasis.stockify.config;

import dev.oasis.stockify.config.tenant.TenantContext;
import dev.oasis.stockify.dto.ProductCreateDTO;
import dev.oasis.stockify.dto.UserCreateDTO;
import dev.oasis.stockify.repository.AppUserRepository;
import dev.oasis.stockify.repository.ProductRepository;
import dev.oasis.stockify.service.AppUserService;
import dev.oasis.stockify.service.ProductService;
import dev.oasis.stockify.service.TenantManagementService;
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

/**
 * Multi-Tenant Data Loader Component
 * This component initializes sample data for multiple tenants in the Stockify application.
 * It creates separate schemas for each tenant and populates them with initial data including:
 * - Administrative users with proper roles
 * - Sample products with realistic inventory data
 * - Tenant-specific configurations
 * The data loader runs only in 'dev' profile and ensures complete isolation
 * between tenant data while maintaining consistent data structure.
 * 
 * @author Stockify Team
 * @version 1.0
 * @since 2025
 */
@Slf4j
@Component
@Profile("dev")
@Order(2) // Run after SuperAdminInitializer
@RequiredArgsConstructor
public class DataLoader implements CommandLineRunner {

    private final DataSource dataSource;
    private final AppUserService appUserService;
    private final ProductService productService;
    private final AppUserRepository appUserRepository;
    private final ProductRepository productRepository;
    private final TenantManagementService tenantManagementService;


    // Configuration for tenant setup
    private static final List<String> TENANT_IDS = Arrays.asList(
        "company1", "company2"
    );

    // Sample data configurations
    private static final List<SampleUser> SAMPLE_USERS = Arrays.asList(
        new SampleUser("admin", "admin123", "ADMIN", "System Administrator"),
       new SampleUser("operator", "operator123", "USER", "Warehouse Operator")

    );

    private static final List<SampleProduct> SAMPLE_PRODUCTS = Arrays.asList(
        new SampleProduct("ETSY-001", "Handmade Ceramic Mug", "Beautiful handcrafted ceramic mug with unique design", "Ceramics", "29.99", 25, 5),
        new SampleProduct("ETSY-002", "Vintage Style Jewelry Box", "Elegant wooden jewelry box with vintage brass fittings", "Jewelry", "89.99", 12, 3),
        new SampleProduct("ETSY-003", "Organic Cotton Tote Bag", "Eco-friendly tote bag made from 100% organic cotton", "Bags", "24.99", 50, 10)
         );

    @Override
    @Transactional
    public void run(String... args)  {
        log.info("🚀 Starting Multi-Tenant Data Loader...");
        
        try {
            // Initialize data for each tenant
            for (String tenantId : TENANT_IDS) {
                initializeTenantData(tenantId);
            }
            
            log.info("✅ Multi-Tenant Data Loader completed successfully!");
            log.info("📊 Initialized {} tenants with sample data", TENANT_IDS.size());
            log.info("👥 Each tenant has {} users and {} products", SAMPLE_USERS.size(), SAMPLE_PRODUCTS.size());
            
        } catch (Exception e) {
            log.error("❌ Error during data loading: {}", e.getMessage(), e);
            throw e;
        } finally {
            // Always clear tenant context
            TenantContext.clear();
        }
    }

    /**
     * Initialize data for a specific tenant
     */
    private void initializeTenantData(String tenantId) {
        log.info("🏢 Initializing data for tenant: {}", tenantId);
        
        try {
            // Set tenant context
            TenantContext.setCurrentTenant(tenantId);
            
            // Ensure schema exists
            createTenantSchemaIfNotExists(tenantId);
            
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
            // Clear tenant context after processing
            TenantContext.clear();
        }
    }

    /**
     * Create tenant schema if it doesn't exist
     */
    private void createTenantSchemaIfNotExists(String tenantId) {
        try (Connection connection = dataSource.getConnection();
             Statement statement = connection.createStatement()) {
            
            String schemaName = tenantId.toUpperCase();
            
            // Create schema
            String createSchemaSQL = String.format("CREATE SCHEMA IF NOT EXISTS %s", schemaName);
            statement.execute(createSchemaSQL);
            
            // Set schema for this connection
            String setSchemaSQL = String.format("SET SCHEMA '%s'", schemaName);
            statement.execute(setSchemaSQL);

            log.debug("🏗️ Schema ensured for tenant: {}", tenantId);
            
        } catch (SQLException e) {
            log.error("❌ Failed to create schema for tenant {}: {}", tenantId, e.getMessage());
            throw new RuntimeException("Failed to create schema for tenant: " + tenantId, e);
        }
    }

    /**
     * Check if data is already loaded for the tenant
     */
    private boolean isDataAlreadyLoaded(String tenantId) {
        try {
            // Check if admin user exists
            return appUserRepository.findByUsername("admin").isPresent();
        } catch (Exception e) {
            // If there's an error checking, assume data doesn't exist
            log.debug("🔍 Could not check existing data for tenant {}, proceeding with initialization", tenantId);
            return false;
        }
    }

    /**
     * Initialize users for the tenant
     */
    private void initializeTenantUsers(String tenantId) {
        log.info("👥 Creating users for tenant: {}", tenantId);
        
        for (SampleUser sampleUser : SAMPLE_USERS) {
            try {
                // Check if user already exists
                if (appUserRepository.findByUsername(sampleUser.username).isPresent()) {
                    log.debug("👤 User {} already exists for tenant {}, skipping", sampleUser.username, tenantId);
                    continue;
                }
                
                // Create user DTO
                UserCreateDTO userDTO = new UserCreateDTO();
                userDTO.setUsername(sampleUser.username);
                userDTO.setPassword(sampleUser.password);
                userDTO.setRole(sampleUser.role);
                
                // Save user through service
                appUserService.saveUser(userDTO);
                
                log.info("✅ Created user: {} with role: {} for tenant: {}", 
                    sampleUser.username, sampleUser.role, tenantId);
                
            } catch (Exception e) {
                log.error("❌ Failed to create user {} for tenant {}: {}", 
                    sampleUser.username, tenantId, e.getMessage());
                // Continue with other users instead of failing completely
            }
        }
    }

    /**
     * Initialize products for the tenant
     */
    private void initializeTenantProducts(String tenantId) {
        log.info("📦 Creating products for tenant: {}", tenantId);
        
        for (SampleProduct sampleProduct : SAMPLE_PRODUCTS) {
            try {
                // Check if product already exists
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
                
                // Set Etsy product ID (simulate external integration)
                productDTO.setEtsyProductId("ETSY_" + tenantId.toUpperCase() + "_" + sampleProduct.sku);
                
                // Save product through service
                productService.saveProduct(productDTO);
                
                log.info("✅ Created product: {} for tenant: {}", sampleProduct.title, tenantId);
                
            } catch (Exception e) {
                log.error("❌ Failed to create product {} for tenant {}: {}", 
                    sampleProduct.sku, tenantId, e.getMessage());
                // Continue with other products instead of failing completely
            }
        }
    }

    /**
     * Initialize tenant-specific configurations
     */
    private void initializeTenantConfig(String tenantId) {
        log.info("⚙️ Setting up configuration for tenant: {}", tenantId);
        
        try (Connection connection = dataSource.getConnection();
             Statement statement = connection.createStatement()) {
            
            String schemaName = tenantId.toUpperCase();
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
    }

    /**
     * Get display name for tenant
     */
    private String getTenantDisplayName(String tenantId) {
        return switch (tenantId.toLowerCase()) {
            case "company1" -> "Artisan Crafts Co.";
            case "company2" -> "Vintage Treasures Ltd.";
            case "company3" -> "Eco-Friendly Goods Inc.";
            case "demo" -> "Demo Company";
            case "test" -> "Test Environment";
            default -> "Tenant " + tenantId;
        };
    }

    /**
     * Sample user data structure
     */
    private static class SampleUser {
        final String username;
        final String password;
        final String role;
        final String description;

        SampleUser(String username, String password, String role, String description) {
            this.username = username;
            this.password = password;
            this.role = role;
            this.description = description;
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
