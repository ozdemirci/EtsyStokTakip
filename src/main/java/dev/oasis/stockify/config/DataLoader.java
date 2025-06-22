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
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.transaction.annotation.Transactional;
import javax.sql.DataSource;
import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.Random;


@Slf4j
@Configuration
@Order(2) // Run after MultiTenantFlywayConfig (1)
@RequiredArgsConstructor
public class DataLoader implements CommandLineRunner {
    
    private final AppUserService appUserService;
    private final ProductService productService;
    private final AppUserRepository appUserRepository;
    private final ProductRepository productRepository;
    private final StockNotificationRepository stockNotificationRepository;
    private final DataSource dataSource;    
    
    @Value("${spring.flyway.schemas}")   
    private List<String> TENANT_IDS;
    
    @Override
    public void run(String... args) {
        log.info("🚀 Starting Multi-Tenant Data Loader...");
        
        log.info("🏢 Configured tenant IDs: {}", TENANT_IDS);
        
        if (TENANT_IDS == null || TENANT_IDS.isEmpty()) {
            log.error("❌ CRITICAL: No tenant IDs configured! Check spring.flyway.schemas property");
            return;
        }
        
        try {
            // First, fix any existing incorrect accessible_tenants data
            fixAccessibleTenantsData(TENANT_IDS);
            
            for (String tenantId : TENANT_IDS) {
                log.info("🔄 Processing tenant: {}", tenantId);
                initializeTenantData(tenantId);
                log.info("✅ Completed processing tenant: {}", tenantId);
            }
            
            log.info("✅ Multi-Tenant Data Loader completed successfully!");
            log.info("📊 Initialized {} tenants with sample data", TENANT_IDS.size());
            log.warn("⚠️ Remember to change default passwords in production!");
            log.warn("⚠️ Remember to remove /h2-console/** in production where security config!");
           
            
        } catch (Exception e) {
            log.error("❌ Error during data loading: {}", e.getMessage(), e);
            throw e;
        } finally {
            TenantContext.clear();
        }
    }      /**
     * Fix incorrect accessible_tenants data from previous configurations
     */
    @Transactional
    private void fixAccessibleTenantsData(List<String> TENANT_IDS) {
        log.info("🔧 Fixing accessible_tenants data for tenant isolation...");
        
        try (Connection connection = dataSource.getConnection()) {

            for (String tenantId : TENANT_IDS) {
                if ("public".equals(tenantId)){ 
                continue; // Skip public schema
                }
                
                String updateSql = String.format(
                    "UPDATE %s.app_user SET accessible_tenants = ? WHERE primary_tenant = ? AND role != 'SUPER_ADMIN' AND accessible_tenants LIKE '%%,%%'",
                    tenantId
                );
                
                try (PreparedStatement stmt = connection.prepareStatement(updateSql)) {
                    stmt.setString(1, tenantId); // Set to own tenant only
                    stmt.setString(2, tenantId); // Where primary_tenant matches
                    
                    int updated = stmt.executeUpdate();
                    if (updated > 0) {
                        log.info("✅ Fixed accessible_tenants for {} users in tenant: {}", updated, tenantId);
                    }
                } catch (SQLException e) {
                    log.debug("Table might not exist yet for tenant {}: {}", tenantId, e.getMessage());
                }
            }
        } catch (Exception e) {
            log.warn("⚠️ Could not fix accessible_tenants data: {}", e.getMessage());
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

            log.info("🔧 Set tenant context to: {} - Current context: {}", 
                tenantId, TenantContext.getCurrentTenant());
            verifyTenantSchema(tenantId);
            
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
        
        UserCreateDTO superAdminDto = UserCreateDTO.builder()
                .username("superadmin")
                .password("superadmin123")
                .role(Role.SUPER_ADMIN)
                .primaryTenant("public")
                .build();
        
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
    }   
    
    private void createProducts(String tenantId) {
        log.info("📦 Creating products for tenant: {}", tenantId);
        
        try {
            TenantContext.setCurrentTenant(tenantId);                           
                        
            List<ProductCreateDTO> products = getSampleProducts();             
            
            for (ProductCreateDTO product : products) {
                try {
                    // Check if product exists in THIS tenant
                    Boolean existingProduct = productRepository.findBySku(product.getSku()).isEmpty();

                    if (existingProduct) {
                        
                        productService.saveProduct(product);
                        
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
            
            
            int created = 5;
            Random random = new Random();
            
            // Create 5 different types of sample notifications
            for (int i = 0; i < Math.min(5, products.size()); i++) {
                Product product = products.get(i);
                
                StockNotification notification = new StockNotification();
                notification.setProduct(product);
                  switch (i) {
                    case 0:
                        // Critical out of stock
                        notification.setNotificationType("OUT_OF_STOCK");
                        notification.setPriority("HIGH");
                        notification.setMessage("🚨 Critical: '" + product.getTitle() + "' is completely out of stock!");
                        notification.setRead(false);
                        break;
                        
                    case 1:
                        // Low stock warning
                        notification.setNotificationType("LOW_STOCK");
                        notification.setPriority("MEDIUM");
                        notification.setMessage("⚠️ Low stock alert: '" + product.getTitle() + "' has only " + 
                            product.getStockLevel() + " items remaining");
                        notification.setRead(false);
                        break;
                        
                    case 2:
                        // Overstocked notification
                        notification.setNotificationType("OVERSTOCKED");
                        notification.setPriority("LOW");
                        notification.setMessage("� Overstocked: '" + product.getTitle() + "' has excess inventory - Current stock: " + 
                            product.getStockLevel() + " units");
                        notification.setRead(true);
                        break;
                        
                    case 3:
                        // Reorder suggestion
                        notification.setNotificationType("REORDER");
                        notification.setPriority("MEDIUM");
                        notification.setMessage("🔄 Reorder suggested: '" + product.getTitle() + "' stock levels are approaching threshold");
                        notification.setRead(random.nextBoolean());
                        break;
                        
                    case 4:
                        // Custom notification
                        notification.setNotificationType("CUSTOM");
                        notification.setPriority("LOW");
                        notification.setMessage("ℹ️ Custom alert: '" + product.getTitle() + "' requires attention from inventory manager");
                        notification.setRead(random.nextBoolean());
                        break;
                }
                
                notification.setCategory("STOCK_ALERT");
                notification.setCreatedAt(LocalDateTime.now().minusDays(random.nextInt(3)));
                
                if (notification.isRead()) {
                    notification.setReadAt(LocalDateTime.now());
                    notification.setReadBy(1L);
                }
                
                stockNotificationRepository.save(notification);
               
            }
            
            log.info("🔔 Created {} sample notifications for tenant: {}", created, tenantId);
            
        } catch (Exception e) {
            log.error("❌ Failed to create notifications for tenant {}: {}", tenantId, e.getMessage(), e);
        }
    }    
    
    // Helper methods
    private UserCreateDTO createUser(String username, String password, Role role, String tenantId) {
        return UserCreateDTO.builder()
                .username(username)
                .password(password)
                .role(role)
                .primaryTenant(tenantId)
                .build();
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
        return ProductCreateDTO.builder()
                .sku(sku)
                .title(title)
                .description(description)
                .category(category)
                .price(new BigDecimal(price))
                .stockLevel(stockLevel)
                .lowStockThreshold(lowStockThreshold)
                .build();
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
