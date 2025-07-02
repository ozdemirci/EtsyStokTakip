package dev.oasis.stockify.config;

import dev.oasis.stockify.dto.ProductCreateDTO;
import dev.oasis.stockify.dto.UserCreateDTO;
import dev.oasis.stockify.model.Product;
import dev.oasis.stockify.model.Role;
import dev.oasis.stockify.model.StockMovement;
import dev.oasis.stockify.model.StockNotification;
import dev.oasis.stockify.model.PlanType;
import dev.oasis.stockify.repository.AppUserRepository;
import dev.oasis.stockify.repository.ProductRepository;
import dev.oasis.stockify.repository.StockMovementRepository;
import dev.oasis.stockify.repository.StockNotificationRepository;
import dev.oasis.stockify.service.AppUserService;
import dev.oasis.stockify.service.ProductService;
import dev.oasis.stockify.service.SubscriptionService;
import dev.oasis.stockify.util.ServiceTenantUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.transaction.annotation.Transactional;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Random;

@Slf4j
@Configuration
@Order(2) // Run after MultiTenantFlywayConfig (1)
@RequiredArgsConstructor
public class DataLoader implements CommandLineRunner {

    private final AppUserService appUserService;
    private final ProductService productService;
    private final SubscriptionService subscriptionService;
    private final ServiceTenantUtil serviceTenantUtil;
    private final AppUserRepository appUserRepository;
    private final ProductRepository productRepository;
    private final StockNotificationRepository stockNotificationRepository;
    private final StockMovementRepository stockMovementRepository;

    @Value("${spring.flyway.schemas}")
    private final String[] TENANT_IDS;

    @Override
    public void run(String... args) {
        log.info("🚀 Starting PostgreSQL Multi-Tenant Data Loader...");
        log.info("📋 Target tenants: {}", String.join(", ", TENANT_IDS));

        try {
            for (String tenantId : TENANT_IDS) {
                log.info("🔄 Processing tenant: '{}'", tenantId);
                initializeTenantData(tenantId);
                log.info("✅ Completed tenant: '{}'", tenantId);
            }

            log.info("✅ Multi-Tenant Data Loader completed successfully!");
            log.warn("⚠️ Remember to change default passwords in production!");

        } catch (Exception e) {
            log.error("❌ Error during data loading: {}", e.getMessage(), e);
            throw new RuntimeException("Multi-tenant data loading failed", e);
        } finally {
            serviceTenantUtil.clearCurrentTenant();
        }
    }

    @Transactional
    private void initializeTenantData(String tenantId) {
        try {
            serviceTenantUtil.executeInTenant(tenantId, () -> {

                // Check if already initialized
                if (isAlreadyInitialized(tenantId)) {
                    log.info("📋 Tenant '{}' already initialized, skipping", tenantId);
                    return null;
                }

                // Create users (SuperAdmin only for public tenant)
                if ("public".equals(tenantId)) {
                    createSuperAdmin();
                }

                // sample datas
                setTenantPlan(tenantId);
                createSampleUsers(tenantId);
                createSampleProducts(tenantId);
                createSampleStockMovements(tenantId);
                createSampleNotifications(tenantId);

                log.info("✨ Successfully initialized tenant: '{}'", tenantId);
                return null;
            });

        } catch (Exception e) {
            log.error("❌ Failed to initialize tenant '{}': {}", tenantId, e.getMessage(), e);
            throw new RuntimeException("Failed to initialize tenant: " + tenantId, e);
        } finally {
            serviceTenantUtil.clearCurrentTenant();
        }
    }

    private void setTenantPlan(String tenantId) {
        // Set default plan type to ENTERPRISE
        PlanType defaultPlan = PlanType.ENTERPRISE;
        log.info("🔄 Setting default plan type '{}' for tenant: '{}'", defaultPlan.getDisplayName(), tenantId);

        // Save the plan using SubscriptionService
        try {
            subscriptionService.setTenantPlan(tenantId, defaultPlan.getCode());
            log.info("✅ Successfully set plan '{}' for tenant: '{}'", defaultPlan.getDisplayName(), tenantId);
        } catch (Exception e) {
            log.warn("⚠️ Could not set plan for tenant '{}': {}", tenantId, e.getMessage());
        } finally {
            serviceTenantUtil.clearCurrentTenant();
        }

    }

    private boolean isAlreadyInitialized(String tenantId) {
        try {
            return serviceTenantUtil.executeInTenant(tenantId, () -> {
                long userCount = appUserRepository.count();
                long productCount = productRepository.count();

                // For public tenant, check for SuperAdmin
                if ("public".equals(tenantId)) {
                    boolean hasSuperAdmin = appUserRepository.findByUsername("superadmin").isPresent();
                    return userCount > 0 && productCount > 0 && hasSuperAdmin;
                }

                return userCount > 0 && productCount > 0;
            });

        } catch (Exception e) {
            log.debug("Could not check tenant '{}' initialization: {}", tenantId, e.getMessage());
            return false;
        }finally {
            serviceTenantUtil.clearCurrentTenant();
        }
    }

    private void createSuperAdmin() {
        if (appUserRepository.findByUsername("superadmin").isPresent()) {
            return;
        }

        UserCreateDTO dto = new UserCreateDTO();
        dto.setUsername("superadmin");
        dto.setPassword("superadmin123");
        dto.setRole(Role.SUPER_ADMIN);
        dto.setPrimaryTenant("public");
        dto.setAccessibleTenants(String.join(",", TENANT_IDS));

        appUserService.saveUser(dto);
        log.info("✅ Created SuperAdmin with access to all tenants");
      
        serviceTenantUtil.clearCurrentTenant();
         
    }

    private void createSampleUsers(String tenantId) {
        // Create 3 sample users per tenant
        String[] usernames = { "admin", "manager", "operator" };
        Role[] roles = { Role.ADMIN, Role.USER, Role.USER };

        for (int i = 0; i < 3; i++) {
            String username = "public".equals(tenantId) ? usernames[i] : usernames[i] + "_" + tenantId;

            if (appUserRepository.findByUsername(username).isEmpty()) {
                UserCreateDTO dto = new UserCreateDTO();
                dto.setUsername(username);
                dto.setPassword(usernames[i] + "123");
                dto.setRole(roles[i]);
                dto.setPrimaryTenant(tenantId);
                dto.setAccessibleTenants(tenantId);

                appUserService.saveUser(dto);
                log.info("✅ Created user: {} for tenant: {}", username, tenantId);
            }
        }
        
    }

    private void createSampleProducts(String tenantId) {
        // Create 3 sample products per tenant
        String[][] products = {
                { "PROD-001", "Wireless Headphones", "High-quality wireless headphones", "149.99" },
                { "PROD-002", "USB-C Cable", "Fast charging USB-C cable", "19.99" },
                { "PROD-003", "Phone Stand", "Adjustable smartphone stand", "29.99" }
        };

        for (String[] product : products) {
            String sku = product[0];
            if (productRepository.findBySku(sku).isEmpty()) {
                ProductCreateDTO dto = new ProductCreateDTO();
                dto.setSku(sku);
                dto.setTitle(product[1]);
                dto.setDescription(product[2]);
                dto.setCategory("Electronics");
                dto.setPrice(new BigDecimal(product[3]));
                dto.setStockLevel(50);
                dto.setLowStockThreshold(10);
                dto.setEtsyProductId("EXT_" + tenantId.toUpperCase() + "_" + sku);

                productService.saveProduct(dto);
                log.info("✅ Created product: {} for tenant: {}", product[1], tenantId);
            }
        }
    }

    private void createSampleStockMovements(String tenantId) {
        List<Product> products = productRepository.findAll();
        if (products.isEmpty()) {
            return;
        }

        // Create 5 sample stock movements
        StockMovement.MovementType[] types = {
                StockMovement.MovementType.IN,
                StockMovement.MovementType.OUT,
                StockMovement.MovementType.ADJUSTMENT,
                StockMovement.MovementType.IN,
                StockMovement.MovementType.RETURN
        };

        Random random = new Random();

        for (int i = 0; i < Math.min(5, products.size()); i++) {
            Product product = products.get(i);

            StockMovement movement = new StockMovement();
            movement.setProduct(product);
            movement.setMovementType(types[i]);
            movement.setQuantity(
                    types[i] == StockMovement.MovementType.OUT ? -random.nextInt(10) - 1 : random.nextInt(20) + 1);
            movement.setPreviousStock(product.getStockLevel());
            movement.setNewStock(product.getStockLevel() + movement.getQuantity());
            movement.setReferenceId("SAMPLE_" + tenantId.toUpperCase() + "_" + (i + 1));
            movement.setNotes("Sample stock " + (types[i] == StockMovement.MovementType.IN ? "addition" : "movement")
                    + " for product: " + product.getTitle());
            movement.setCreatedBy(1L); // Assuming first user

            stockMovementRepository.save(movement);
        }

        log.info("✅ Created {} sample stock movements for tenant: {}", Math.min(5, products.size()), tenantId);
    }

    private void createSampleNotifications(String tenantId) {
        List<Product> products = productRepository.findAll();
        if (products.isEmpty()) {
            return;
        }

        // Clear existing notifications
        stockNotificationRepository.deleteAll();

        // Create 3 sample notifications
        String[] types = { "OUT_OF_STOCK", "LOW_STOCK", "LOW_STOCK" };
        String[] priorities = { "HIGH", "MEDIUM", "MEDIUM" };
        Random random = new Random();

        for (int i = 0; i < Math.min(3, products.size()); i++) {
            Product product = products.get(i);

            StockNotification notification = new StockNotification();
            notification.setProduct(product);
            notification.setNotificationType(types[i]);
            notification.setPriority(priorities[i]);
            notification.setCategory("STOCK_ALERT");
            notification.setCreatedAt(LocalDateTime.now().minusDays(random.nextInt(3)));
            notification.setRead(random.nextBoolean());

            if (i == 0) {
                notification.setMessage("🚨 Critical: '" + product.getTitle() + "' is out of stock!");
            } else {
                notification.setMessage("⚠️ Low stock: '" + product.getTitle() + "' needs restocking");
            }

            if (notification.isRead()) {
                notification.setReadAt(LocalDateTime.now());
                notification.setReadBy(1L);
            }

            stockNotificationRepository.save(notification);
        }

        log.info("✅ Created 3 sample notifications for tenant: {}", tenantId);
    }
}
