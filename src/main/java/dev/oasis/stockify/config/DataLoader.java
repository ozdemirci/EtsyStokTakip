package dev.oasis.stockify.config;

import dev.oasis.stockify.config.tenant.TenantContext;
import dev.oasis.stockify.dto.ProductCreateDTO;
import dev.oasis.stockify.dto.UserCreateDTO;
import dev.oasis.stockify.model.Product;
import dev.oasis.stockify.model.Role;
import dev.oasis.stockify.model.StockNotification;
import dev.oasis.stockify.repository.AppUserRepository;
import dev.oasis.stockify.repository.ProductRepository;
import dev.oasis.stockify.repository.StockNotificationRepository;
import dev.oasis.stockify.service.AppUserService;
import dev.oasis.stockify.service.ProductService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import javax.sql.DataSource;
import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Random;

@Slf4j
@Component
@Order(2) // Run after MultiTenantFlywayConfig (1)
@RequiredArgsConstructor
public class DataLoader implements CommandLineRunner {
   
    private final DataSource dataSource;
    private final AppUserService appUserService;
    private final ProductService productService;
    private final AppUserRepository appUserRepository;
    private final ProductRepository productRepository;
    private final StockNotificationRepository stockNotificationRepository;    private static final List<String> TENANT_IDS = Arrays.asList(
        "public","stockify","acme_corp","global_trade","artisan_crafts","tech_solutions","company1"
    );
    
    // Sample users using DTO directly
    private static final List<UserCreateDTO> SAMPLE_USERS = Arrays.asList(
        createUserDTO("admin", "admin123", Role.ADMIN),
        createUserDTO("operator", "operator123", Role.USER),
        createUserDTO("manager", "manager123", Role.USER)
    );

    // Sample products using DTO directly
    private static final List<ProductCreateDTO> SAMPLE_PRODUCTS = Arrays.asList(
        // Electronics category
        createProductDTO("ELEC-001", "Wireless Bluetooth Headphones", "High-quality wireless headphones with noise cancellation", "Electronics", "149.99", 35, 5),
        createProductDTO("ELEC-002", "USB-C Charging Cable", "Fast charging USB-C cable with durable braided design", "Electronics", "19.99", 100, 10),
        createProductDTO("ELEC-003", "Smartphone Stand", "Adjustable aluminum smartphone stand for desk use", "Electronics", "29.99", 50, 8),
        
        // Home & Garden category
        createProductDTO("HOME-001", "Ceramic Coffee Mug", "Beautiful handcrafted ceramic mug with unique design", "Home & Garden", "24.99", 40, 5),
        createProductDTO("HOME-002", "Wooden Cutting Board", "Premium bamboo cutting board with juice groove", "Home & Garden", "34.99", 25, 3),
        createProductDTO("HOME-003", "LED Desk Lamp", "Modern adjustable LED desk lamp with touch control", "Home & Garden", "79.99", 20, 2),
        
        // Clothing category
        createProductDTO("CLOTH-001", "Cotton T-Shirt", "Comfortable 100% cotton t-shirt in various colors", "Clothing", "19.99", 75, 10),
        createProductDTO("CLOTH-002", "Denim Jeans", "Classic fit denim jeans with premium quality", "Clothing", "59.99", 30, 5),
        createProductDTO("CLOTH-003", "Wool Sweater", "Warm and cozy wool sweater for winter", "Clothing", "89.99", 15, 3),
        
        // Books category
        createProductDTO("BOOK-001", "Programming Guide", "Complete guide to modern programming techniques", "Books", "39.99", 45, 5),
        createProductDTO("BOOK-002", "Business Strategy", "Essential business strategy and management principles", "Books", "29.99", 60, 8),
        createProductDTO("BOOK-003", "Cooking Recipes", "Collection of delicious and easy cooking recipes", "Books", "24.99", 35, 4)
    );
    
    
    @Override
    public void run(String... args)  {
        log.info("🚀 Starting Multi-Tenant Data Loader...");
          try {
            // Initialize data for each tenant
            for (String tenantId : TENANT_IDS) {
                log.info("🔄 Processing tenant: {}", tenantId);
                initializeTenantData(tenantId);
                log.info("✅ Completed processing tenant: {}", tenantId);
            }            log.info("✅ Multi-Tenant Data Loader completed successfully!");
            log.info("📊 Initialized {} tenants with sample data", TENANT_IDS.size());
            log.info("👥 Each tenant has {} users, {} products, and sample notifications", SAMPLE_USERS.size(), SAMPLE_PRODUCTS.size());
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
    protected void initializeTenantData(String tenantId) {
        log.info("🏢 Initializing data for tenant: {}", tenantId);
        
        try {
            // Set tenant context for this initialization
            TenantContext.setCurrentTenant(tenantId);
            log.debug("🔄 Set tenant context to: {}", tenantId);            // Special handling for 'stockify' tenant (super admin tenant)
            if ("stockify".equals(tenantId)) {
                log.info("🏛️ Stockify platform tenant - initializing with regular users");
                // Stockify tenant should also have regular admin/operator/manager users
                // No special super admin creation here as it's handled in public tenant
            }
            
            // Special handling for 'public' tenant (default tenant) - create superadmin
            if ("public".equals(tenantId)) {
                log.info("🌐 Public tenant - initializing default tenant data with superadmin");
                // Check if superadmin already exists
                if (appUserRepository.findByUsername("superadmin").isEmpty()) {
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
            
            // Initialize sample notifications
            initializeTenantNotifications(tenantId);
            
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
    protected boolean isDataAlreadyLoaded(String tenantId) {
        try {
            // Set tenant context to check in the correct schema
            TenantContext.setCurrentTenant(tenantId);
            log.debug("🔍 Checking if data already loaded for tenant: {}", tenantId);

            boolean productsExist = productRepository.count() > 0;
            boolean notificationsExist = stockNotificationRepository.count() > 0;            // For public tenant, check both superadmin and admin users
            if ("public".equals(tenantId)) {
                boolean superAdminExists = appUserRepository.findByUsername("superadmin").isPresent();
                boolean adminExists = appUserRepository.findByUsername("admin").isPresent();
                // Don't include notifications in loaded check - let them be recreated if needed
                boolean loaded = superAdminExists && adminExists && productsExist;
                log.debug("🔍 Public tenant users - SuperAdmin: {}, Admin: {}, Products: {}, Notifications: {} (ignored for load check)", 
                    superAdminExists, adminExists, productsExist, notificationsExist);
                return loaded;
            } else {
                // For other tenants, check if admin user exists and products exist
                boolean adminExists = appUserRepository.findByUsername("admin").isPresent();
                // Don't include notifications in loaded check - let them be recreated if needed
                boolean loaded = adminExists && productsExist;
                log.debug("🔍 Tenant {} - Admin user exists: {}, Products exist: {}, Notifications exist: {} (ignored for load check)", 
                    tenantId, adminExists, productsExist, notificationsExist);
                return loaded;
            }
            
        } catch (Exception e) {
            // If there's an error checking, assume data doesn't exist
            log.debug("🔍 Could not check existing data for tenant {}, proceeding with initialization: {}", tenantId, e.getMessage());
            return false;
        }
    }/**
     * Initialize users for the tenant
     */
    @Transactional
    protected void initializeTenantUsers(String tenantId) {
        log.info("👥 Creating users for tenant: {}", tenantId);
          int createdUserCount = 0;
        for (UserCreateDTO sampleUser : SAMPLE_USERS) {
            try {
                // Ensure tenant context is set
                TenantContext.setCurrentTenant(tenantId);
                log.debug("🔄 Set tenant context to: {} for user: {}", tenantId, sampleUser.getUsername());
                
                // Check if user already exists in this tenant
                boolean userExists = appUserRepository.findByUsername(sampleUser.getUsername()).isPresent();
                log.debug("🔍 User {} exists in tenant {}: {}", sampleUser.getUsername(), tenantId, userExists);
                
                if (userExists) {
                    log.debug("👤 User {} already exists for tenant {}, skipping", sampleUser.getUsername(), tenantId);
                    continue;
                }
                  // Use the DTO directly (create a copy to avoid modifying the original)
                UserCreateDTO userDTO = new UserCreateDTO();
                userDTO.setUsername(sampleUser.getUsername());
                userDTO.setPassword(sampleUser.getPassword());
                userDTO.setRole(sampleUser.getRole());
                userDTO.setPrimaryTenant(tenantId); // Set the tenant ID
                
                log.debug("💾 Saving user: {} with role: {} for tenant: {}", 
                    sampleUser.getUsername(), sampleUser.getRole(), tenantId);
                
                // Save user through service
                appUserService.saveUser(userDTO);
                createdUserCount++;                
                log.info("✅ Created user: {} with role: {} for tenant: {}", 
                    sampleUser.getUsername(), sampleUser.getRole(), tenantId);
                
            } catch (Exception e) {
                log.error("❌ Failed to create user {} for tenant {}: {}", 
                    sampleUser.getUsername(), tenantId, e.getMessage(), e);
                // Continue with other users instead of failing completely
            }
        }
        
        log.info("👥 Successfully created {} users for tenant: {}", createdUserCount, tenantId);
    }    /**
     * Initialize products for the tenant
     */
    @Transactional
    protected void initializeTenantProducts(String tenantId) {
        log.info("📦 Creating products for tenant: {}", tenantId);
          int createdProductCount = 0;
        for (ProductCreateDTO sampleProduct : SAMPLE_PRODUCTS) {
            try {
                // Ensure tenant context is set
                TenantContext.setCurrentTenant(tenantId);
                
                // Check if product already exists in this tenant
                if (productRepository.findBySku(sampleProduct.getSku()).isPresent()) {
                    log.debug("📦 Product {} already exists for tenant {}, skipping", sampleProduct.getSku(), tenantId);
                    continue;
                }                // Use the DTO directly (create a copy to avoid modifying the original)
                ProductCreateDTO productDTO = new ProductCreateDTO();
                productDTO.setSku(sampleProduct.getSku());
                productDTO.setTitle(sampleProduct.getTitle());
                productDTO.setDescription(sampleProduct.getDescription());
                productDTO.setCategory(sampleProduct.getCategory());
                productDTO.setPrice(sampleProduct.getPrice());
                productDTO.setStockLevel(sampleProduct.getStockLevel());
                productDTO.setLowStockThreshold(sampleProduct.getLowStockThreshold());
                // productDTO.setTenantId(tenantId); // Commented out until database migration
                
                // Set external product ID (simulate external integration)
                productDTO.setEtsyProductId("EXT_" + tenantId.toUpperCase(Locale.ROOT) + "_" + sampleProduct.getSku());
                
                // Save product through service
                productService.saveProduct(productDTO);
                createdProductCount++;
                
                log.info("✅ Created product: {} for tenant: {}", sampleProduct.getTitle(), tenantId);
                
            } catch (Exception e) {
                log.error("❌ Failed to create product {} for tenant {}: {}", 
                    sampleProduct.getSku(), tenantId, e.getMessage());
                // Continue with other products instead of failing completely
            }
        }
        
        log.info("📦 Successfully created {} products for tenant: {}", createdProductCount, tenantId);
    }    /**
     * Initialize sample notifications for the tenant
     */
    @Transactional
    protected void initializeTenantNotifications(String tenantId) {
        log.info("🔔 Creating sample notifications for tenant: {}", tenantId);
          try {
            // Ensure tenant context is set
            TenantContext.setCurrentTenant(tenantId);
              // Clear existing notifications first to avoid duplicates
            long existingNotificationCount = stockNotificationRepository.count();
            if (existingNotificationCount > 0) {
                log.info("🔔 Clearing {} existing notifications for tenant: {} before creating new ones", existingNotificationCount, tenantId);
                stockNotificationRepository.deleteAll();
            }
            
            // Get some products to create notifications for
            List<Product> products = productRepository.findAll();
            if (products.isEmpty()) {
                log.warn("⚠️ No products found for tenant: {}, cannot create sample notifications", tenantId);
                return;
            }
            
            Random random = new Random();
            int createdNotificationCount = 0;
            
            // Create various types of sample notifications with realistic scenarios
            
            // 1. Create 2-3 critical OUT_OF_STOCK notifications
            for (int i = 0; i < Math.min(3, products.size()); i++) {
                Product product = products.get(i);
                
                StockNotification notification = new StockNotification();
                notification.setProduct(product);
                notification.setNotificationType("OUT_OF_STOCK");
                notification.setPriority("HIGH");
                notification.setCategory("STOCK_ALERT");
                notification.setMessage(String.format(
                    "🚨 Critical: '%s' is completely out of stock! Immediate restocking required.",
                    product.getTitle()
                ));
                notification.setRead(false); // Keep these unread for visibility
                
                // Set creation time (last 2 days for urgency)
                LocalDateTime createdAt = LocalDateTime.now().minusDays(random.nextInt(2));
                notification.setCreatedAt(createdAt);
                
                stockNotificationRepository.save(notification);
                createdNotificationCount++;
                
                log.info("✅ Created OUT_OF_STOCK notification for product: {} in tenant: {}", 
                    product.getTitle(), tenantId);
            }
            
            // 2. Create 3-4 LOW_STOCK notifications
            for (int i = 3; i < Math.min(7, products.size()); i++) {
                Product product = products.get(i);
                
                StockNotification notification = new StockNotification();
                notification.setProduct(product);
                notification.setNotificationType("LOW_STOCK");
                notification.setPriority("MEDIUM");
                notification.setCategory("STOCK_ALERT");
                notification.setMessage(String.format(
                    "⚠️ Low Stock Alert: '%s' has only %d items remaining. Current level is below the threshold of %d.",
                    product.getTitle(), product.getStockLevel(), product.getLowStockThreshold()
                ));
                
                // 70% unread, 30% read
                boolean isRead = random.nextDouble() < 0.3;
                notification.setRead(isRead);
                
                // Set creation time (last 5 days)
                LocalDateTime createdAt = LocalDateTime.now().minusDays(random.nextInt(5));
                notification.setCreatedAt(createdAt);
                
                if (isRead) {
                    notification.setReadAt(createdAt.plusHours(random.nextInt(48)));
                    notification.setReadBy(1L);
                }
                
                stockNotificationRepository.save(notification);
                createdNotificationCount++;
                
                log.info("✅ Created LOW_STOCK notification for product: {} in tenant: {}", 
                    product.getTitle(), tenantId);
            }
            
            // 3. Create 1-2 INVENTORY_UPDATE notifications (mostly read)
            for (int i = 7; i < Math.min(9, products.size()); i++) {
                Product product = products.get(i);
                
                StockNotification notification = new StockNotification();
                notification.setProduct(product);
                notification.setNotificationType("INVENTORY_UPDATE");
                notification.setPriority("LOW");
                notification.setCategory("SYSTEM_NOTIFICATION");
                notification.setMessage(String.format(
                    "📊 Inventory Update: Stock level for '%s' has been updated. New quantity: %d units.",
                    product.getTitle(), product.getStockLevel()
                ));
                
                // Most of these are read (80% read)
                boolean isRead = random.nextDouble() < 0.8;
                notification.setRead(isRead);
                
                // Set creation time (last 7 days)
                LocalDateTime createdAt = LocalDateTime.now().minusDays(random.nextInt(7));
                notification.setCreatedAt(createdAt);
                
                if (isRead) {
                    notification.setReadAt(createdAt.plusHours(random.nextInt(72)));
                    notification.setReadBy(1L);
                }
                
                stockNotificationRepository.save(notification);
                createdNotificationCount++;
                
                log.info("✅ Created INVENTORY_UPDATE notification for product: {} in tenant: {}", 
                    product.getTitle(), tenantId);
            }
              log.info("🔔 Successfully created {} sample notifications for tenant: {}", createdNotificationCount, tenantId);
            
        } catch (Exception e) {
            log.error("❌ Failed to create sample notifications for tenant {}: {}", tenantId, e.getMessage(), e);
            // Continue processing - notifications are not critical for initialization
        }
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
              // Insert tenant-specific configurations using H2-compatible MERGE syntax
            String[] configInserts = {
                String.format("MERGE INTO tenant_config (config_key, config_value, config_type, description) VALUES " +
                    "('tenant_name', '%s', 'STRING', 'Display name for the tenant')", 
                    getTenantDisplayName(tenantId)),
                    
                "MERGE INTO tenant_config (config_key, config_value, config_type, description) VALUES " +
                    "('low_stock_email_enabled', 'true', 'BOOLEAN', 'Enable email notifications for low stock')",
                    
                "MERGE INTO tenant_config (config_key, config_value, config_type, description) VALUES " +
                    "('default_low_stock_threshold', '5', 'INTEGER', 'Default threshold for low stock alerts')",
                    
                "MERGE INTO tenant_config (config_key, config_value, config_type, description) VALUES " +
                    "('currency', 'USD', 'STRING', 'Default currency for pricing')",
                    
                "MERGE INTO tenant_config (config_key, config_value, config_type, description) VALUES " +
                    "('timezone', 'UTC', 'STRING', 'Default timezone for the tenant')"
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
    protected void createSuperAdminForPublicTenant() {
        try {
            log.info("🔑 Creating SuperAdmin user for public tenant");

            // Ensure we're in public tenant context
            TenantContext.setCurrentTenant("public");            // Create superadmin user DTO
            UserCreateDTO superAdminDto = new UserCreateDTO();
            superAdminDto.setUsername("superadmin");
            superAdminDto.setPassword("superadmin123"); // Strong password - should be changed in production
            superAdminDto.setRole(Role.SUPER_ADMIN);

            // Create the superadmin user
            appUserService.saveUser(superAdminDto);

            log.info("✅ Successfully created SuperAdmin user for public tenant");
            log.warn("⚠️ Default SuperAdmin password is 'superadmin123' - CHANGE THIS IN PRODUCTION!");

        } catch (Exception e) {
            log.error("❌ Failed to create SuperAdmin user for public tenant: {}", e.getMessage(), e);
            throw new RuntimeException("Failed to create SuperAdmin for public tenant", e);
        }
    }      /**
     * Helper method to create UserCreateDTO
     */
    private static UserCreateDTO createUserDTO(String username, String password, Role role) {
        UserCreateDTO dto = new UserCreateDTO();
        dto.setUsername(username);
        dto.setPassword(password);
        dto.setRole(role);
        return dto;
    }

    /**
     * Helper method to create ProductCreateDTO
     */
    private static ProductCreateDTO createProductDTO(String sku, String title, String description, 
                                                   String category, String price, int stockLevel, int lowStockThreshold) {
        ProductCreateDTO dto = new ProductCreateDTO();
        dto.setSku(sku);
        dto.setTitle(title);
        dto.setDescription(description);
        dto.setCategory(category);
        dto.setPrice(new BigDecimal(price));
        dto.setStockLevel(stockLevel);
        dto.setLowStockThreshold(lowStockThreshold);
        return dto;
    }
}
