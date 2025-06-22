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
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.Random;

@Slf4j
@Component
@Order(2) // Run after MultiTenantFlywayConfig (1)
@RequiredArgsConstructor
public class DataLoader implements CommandLineRunner {
   
    private final AppUserService appUserService;
    private final ProductService productService;
    private final AppUserRepository appUserRepository;
    private final ProductRepository productRepository;
    private final StockNotificationRepository stockNotificationRepository;
    private final DataSource dataSource;

    private static final List<String> TENANT_IDS = Arrays.asList(
        "public","stockify", "acme_corp", "global_trade", "artisan_crafts", "tech_solutions","company1"
    );
    @Override
    public void run(String... args) {
        log.info("🚀 Starting Multi-Tenant Data Loader...");
        
        try {
            for (String tenantId : TENANT_IDS) {
                log.info("🔄 Processing tenant: {}", tenantId);
                initializeTenantData(tenantId);
                log.info("✅ Completed processing tenant: {}", tenantId);
            }
            
            log.info("✅ Multi-Tenant Data Loader completed successfully!");
            log.info("📊 Initialized {} tenants with sample data", TENANT_IDS.size());
            log.warn("⚠️ Remember to change default passwords in production!");
            
        } catch (Exception e) {
            log.error("❌ Error during data loading: {}", e.getMessage(), e);
            throw e;
        } finally {
            TenantContext.clear();
        }
    }

    @Transactional
    private void initializeTenantData(String tenantId) {
        try {
            TenantContext.setCurrentTenant(tenantId);
            log.info("🏢 Initializing data for tenant: {}", tenantId);
            
            // Check if already initialized to avoid duplicates
            if (isAlreadyInitialized(tenantId)) {
                log.info("📋 Data already exists for tenant: {}, skipping", tenantId);
                return;
            }
            
            // Initialize users (including super admin for public tenant)
            createUsers(tenantId);
            
            // Initialize products
            createProducts(tenantId);
            
            // Initialize sample notifications
            createSampleNotifications(tenantId);
            
            log.info("✨ Successfully initialized tenant: {}", tenantId);
            
        } catch (Exception e) {
            log.error("❌ Failed to initialize tenant {}: {}", tenantId, e.getMessage(), e);
            throw new RuntimeException("Failed to initialize tenant: " + tenantId, e);
        } finally {
            TenantContext.clear();
        }
    }    private boolean isAlreadyInitialized(String tenantId) {
        try {
            TenantContext.setCurrentTenant(tenantId);
            log.info("🔍 Checking if tenant {} is already initialized - Current context: {}", 
                tenantId, TenantContext.getCurrentTenant());
            
            // Verify schema before checking data
            verifyTenantSchema(tenantId);
            
            // Check if basic data exists
            long userCount = appUserRepository.count();
            long productCount = productRepository.count();
            
            log.info("📊 Tenant {} - Users: {}, Products: {}", tenantId, userCount, productCount);
            
            boolean hasUsers = userCount > 0;
            boolean hasProducts = productCount > 0;
            
            // For public tenant, also check for super admin
            if ("public".equals(tenantId)) {
                boolean hasSuperAdmin = appUserRepository.findByUsername("superadmin").isPresent();
                log.info("🔑 Public tenant - SuperAdmin exists: {}", hasSuperAdmin);
                return hasUsers && hasProducts && hasSuperAdmin;
            }
            
            return hasUsers && hasProducts;
            
        } catch (Exception e) {
            log.debug("Could not check existing data for tenant {}, proceeding with initialization: {}", 
                tenantId, e.getMessage());
            return false;
        }
    }

    private void createUsers(String tenantId) {
        log.info("👥 Creating users for tenant: {}", tenantId);
        
        try {
            TenantContext.setCurrentTenant(tenantId);
            
            // Create super admin only for public tenant
            if ("public".equals(tenantId)) {
                createSuperAdmin();
            }
            
            // Create standard users for all tenants
            createStandardUsers(tenantId);
            
        } catch (Exception e) {
            log.error("❌ Failed to create users for tenant {}: {}", tenantId, e.getMessage(), e);
        }
    }

    private void createSuperAdmin() {
        if (appUserRepository.findByUsername("superadmin").isPresent()) {
            log.info("🔑 SuperAdmin already exists, skipping");
            return;
        }
        
        UserCreateDTO superAdminDto = new UserCreateDTO();
        superAdminDto.setUsername("superadmin");
        superAdminDto.setPassword("superadmin123");
        superAdminDto.setRole(Role.SUPER_ADMIN);
        superAdminDto.setPrimaryTenant("public");
        
        appUserService.saveUser(superAdminDto);
        log.info("✅ Created SuperAdmin user");
        log.warn("⚠️ Default SuperAdmin password is 'superadmin123' - CHANGE THIS IN PRODUCTION!");
    }

    private void createStandardUsers(String tenantId) {
        List<UserCreateDTO> users = Arrays.asList(
            createUser("admin", "admin123", Role.ADMIN, tenantId),
            createUser("manager", "manager123", Role.USER, tenantId),
            createUser("operator", "operator123", Role.USER, tenantId)
        );
        
        int created = 0;
        for (UserCreateDTO user : users) {
            try {
                if (appUserRepository.findByUsername(user.getUsername()).isEmpty()) {
                    appUserService.saveUser(user);
                    created++;
                    log.info("✅ Created user: {} for tenant: {}", user.getUsername(), tenantId);
                }
            } catch (Exception e) {
                log.error("❌ Failed to create user {} for tenant {}: {}", 
                    user.getUsername(), tenantId, e.getMessage());
            }
        }
        
        log.info("👥 Created {} users for tenant: {}", created, tenantId);
    }    private void createProducts(String tenantId) {
        log.info("📦 Creating products for tenant: {}", tenantId);
        
        try {
            TenantContext.setCurrentTenant(tenantId);
            log.info("🔧 Set tenant context to: {} - Current context: {}", 
                tenantId, TenantContext.getCurrentTenant());
            
            // Verify the schema is correct
            verifyTenantSchema(tenantId);
            
            // Check existing products BEFORE creating new ones
            long existingProductCount = productRepository.count();
            log.info("📊 Existing product count in tenant {}: {}", tenantId, existingProductCount);
            
            List<ProductCreateDTO> products = getSampleProducts();
            int created = 0;
            
            for (ProductCreateDTO product : products) {
                try {
                    // Check if product exists in THIS tenant
                    Optional<Product> existingProduct = productRepository.findBySku(product.getSku());
                    if (existingProduct.isEmpty()) {
                        // Set tenant-specific external ID
                        product.setEtsyProductId("EXT_" + tenantId.toUpperCase() + "_" + product.getSku());
                        
                        log.debug("💾 Saving product {} for tenant {} with context {}", 
                            product.getSku(), tenantId, TenantContext.getCurrentTenant());
                        
                        productService.saveProduct(product);
                        created++;
                        log.info("✅ Created product: {} for tenant: {}", product.getTitle(), tenantId);
                    } else {
                        log.debug("⏭️ Product {} already exists in tenant {}, skipping", 
                            product.getSku(), tenantId);
                    }
                } catch (Exception e) {
                    log.error("❌ Failed to create product {} for tenant {}: {}", 
                        product.getSku(), tenantId, e.getMessage());
                }
            }
            
            // Check product count AFTER creation
            long finalProductCount = productRepository.count();
            log.info("📦 Created {} products for tenant: {} - Final count: {}", 
                created, tenantId, finalProductCount);
            
        } catch (Exception e) {
            log.error("❌ Failed to create products for tenant {}: {}", tenantId, e.getMessage(), e);
        }
    }

    private void createSampleNotifications(String tenantId) {
        log.info("🔔 Creating sample notifications for tenant: {}", tenantId);
        
        try {
            TenantContext.setCurrentTenant(tenantId);
            
            // Get products to create notifications for
            List<Product> products = productRepository.findAll();
            if (products.isEmpty()) {
                log.warn("⚠️ No products found for tenant: {}, skipping notifications", tenantId);
                return;
            }
            
            // Clear existing notifications to avoid duplicates
            stockNotificationRepository.deleteAll();
            
            int created = 0;
            Random random = new Random();
            
            // Create a few sample notifications
            for (int i = 0; i < Math.min(3, products.size()); i++) {
                Product product = products.get(i);
                
                StockNotification notification = new StockNotification();
                notification.setProduct(product);
                
                if (i == 0) {
                    // Critical out of stock
                    notification.setNotificationType("OUT_OF_STOCK");
                    notification.setPriority("HIGH");
                    notification.setMessage("🚨 Critical: '" + product.getTitle() + "' is out of stock!");
                    notification.setRead(false);
                } else {
                    // Low stock warning
                    notification.setNotificationType("LOW_STOCK");
                    notification.setPriority("MEDIUM");
                    notification.setMessage("⚠️ Low stock: '" + product.getTitle() + "' has only " + 
                        product.getStockLevel() + " items remaining");
                    notification.setRead(random.nextBoolean());
                }
                
                notification.setCategory("STOCK_ALERT");
                notification.setCreatedAt(LocalDateTime.now().minusDays(random.nextInt(3)));
                
                if (notification.isRead()) {
                    notification.setReadAt(LocalDateTime.now());
                    notification.setReadBy(1L);
                }
                
                stockNotificationRepository.save(notification);
                created++;
            }
            
            log.info("🔔 Created {} sample notifications for tenant: {}", created, tenantId);
            
        } catch (Exception e) {
            log.error("❌ Failed to create notifications for tenant {}: {}", tenantId, e.getMessage(), e);
        }
    }

    // Helper methods
    private UserCreateDTO createUser(String username, String password, Role role, String tenantId) {
        UserCreateDTO dto = new UserCreateDTO();
        dto.setUsername(username);
        dto.setPassword(password);
        dto.setRole(role);
        dto.setPrimaryTenant(tenantId);
        return dto;
    }

    private List<ProductCreateDTO> getSampleProducts() {
        return Arrays.asList(
            // Electronics
            createProduct("ELEC-001", "Wireless Headphones", "High-quality wireless headphones", 
                "Electronics", "149.99", 35, 5),
            createProduct("ELEC-002", "USB-C Cable", "Fast charging USB-C cable", 
                "Electronics", "19.99", 100, 10),
            createProduct("ELEC-003", "Phone Stand", "Adjustable smartphone stand", 
                "Electronics", "29.99", 50, 8),
            
            // Home & Garden
            createProduct("HOME-001", "Ceramic Mug", "Handcrafted ceramic coffee mug", 
                "Home & Garden", "24.99", 40, 5),
            createProduct("HOME-002", "Cutting Board", "Premium bamboo cutting board", 
                "Home & Garden", "34.99", 25, 3),
            createProduct("HOME-003", "LED Lamp", "Modern adjustable LED desk lamp", 
                "Home & Garden", "79.99", 20, 2),
            
            // Clothing
            createProduct("CLOTH-001", "Cotton T-Shirt", "Comfortable cotton t-shirt", 
                "Clothing", "19.99", 75, 10),
            createProduct("CLOTH-002", "Denim Jeans", "Classic fit denim jeans", 
                "Clothing", "59.99", 30, 5),
            
            // Books
            createProduct("BOOK-001", "Programming Guide", "Complete programming guide", 
                "Books", "39.99", 45, 5),
            createProduct("BOOK-002", "Business Strategy", "Business strategy principles", 
                "Books", "29.99", 60, 8)
        );
    }

    private ProductCreateDTO createProduct(String sku, String title, String description, 
                                         String category, String price, int stockLevel, int lowStockThreshold) {
        ProductCreateDTO dto = new ProductCreateDTO();
        dto.setSku(sku);
        dto.setTitle(title);
        dto.setDescription(description);
        dto.setCategory(category);        dto.setPrice(new BigDecimal(price));
        dto.setStockLevel(stockLevel);
        dto.setLowStockThreshold(lowStockThreshold);
        return dto;
    }
    
    /**
     * Verify that we're operating in the correct schema
     */
    private void verifyTenantSchema(String tenantId) {
        try (Connection connection = dataSource.getConnection()) {
            String currentSchema = connection.getSchema();
            log.info("🔍 Schema verification for tenant {}: Database connection schema = '{}'", 
                tenantId, currentSchema);
            
            // Force schema set if not matching
            String expectedSchema = tenantId.toLowerCase();
            if (!expectedSchema.equals(currentSchema)) {
                log.warn("⚠️ Schema mismatch! Expected '{}' but got '{}'. Setting schema...", 
                    expectedSchema, currentSchema);
                connection.setSchema(expectedSchema);
                log.info("✅ Schema corrected to: {}", connection.getSchema());
            }
        } catch (SQLException e) {
            log.error("❌ Failed to verify schema for tenant {}: {}", tenantId, e.getMessage());
        }
    }
}
