package dev.oasis.stockify.service;

import dev.oasis.stockify.config.tenant.TenantContext;
import dev.oasis.stockify.dto.ProductCreateDTO;
import dev.oasis.stockify.model.Product;
import dev.oasis.stockify.repository.ProductRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import javax.sql.DataSource;
import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.Statement;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Multi-tenant integration tests for ProductService
 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
class ProductServiceMultiTenantTest {

    @Autowired
    private ProductService productService;

    @Autowired
    private ProductRepository productRepository;

    @Autowired
    private DataSource dataSource;

    private static final String TENANT_1 = "product-test-tenant1";
    private static final String TENANT_2 = "product-test-tenant2";

    @BeforeEach
    void setUp() {
        TenantContext.clear();
        setupTestTenants();
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
        cleanupTestTenants();
    }

    @Test
    void shouldIsolateProductsBetweenTenants() {
        // Given - Create product in tenant 1
        TenantContext.setCurrentTenant(TENANT_1);
        ProductCreateDTO productDTO1 = createTestProductDTO("SKU-001", "Tenant 1 Product");
        Product savedProduct1 = productService.saveProduct(productDTO1);

        // Given - Create product in tenant 2
        TenantContext.setCurrentTenant(TENANT_2);
        ProductCreateDTO productDTO2 = createTestProductDTO("SKU-002", "Tenant 2 Product");
        Product savedProduct2 = productService.saveProduct(productDTO2);

        // When - Check products in tenant 1
        TenantContext.setCurrentTenant(TENANT_1);
        List<Product> tenant1Products = productService.getAllProducts();
        Optional<Product> tenant1Product = productRepository.findBySku("SKU-001");

        // Then - Should only see tenant 1 products
        assertThat(tenant1Products).hasSize(1);
        assertThat(tenant1Products.get(0).getTitle()).isEqualTo("Tenant 1 Product");
        assertThat(tenant1Product).isPresent();
        assertThat(productRepository.findBySku("SKU-002")).isEmpty();

        // When - Check products in tenant 2
        TenantContext.setCurrentTenant(TENANT_2);
        List<Product> tenant2Products = productService.getAllProducts();
        Optional<Product> tenant2Product = productRepository.findBySku("SKU-002");

        // Then - Should only see tenant 2 products
        assertThat(tenant2Products).hasSize(1);
        assertThat(tenant2Products.get(0).getTitle()).isEqualTo("Tenant 2 Product");
        assertThat(tenant2Product).isPresent();
        assertThat(productRepository.findBySku("SKU-001")).isEmpty();
    }

    @Test
    void shouldAllowSameSkuInDifferentTenants() {
        // Given - Same SKU in different tenants
        String sameSku = "DUPLICATE-SKU";

        // When - Create product with same SKU in tenant 1
        TenantContext.setCurrentTenant(TENANT_1);
        ProductCreateDTO productDTO1 = createTestProductDTO(sameSku, "Tenant 1 Product");
        Product savedProduct1 = productService.saveProduct(productDTO1);

        // When - Create product with same SKU in tenant 2
        TenantContext.setCurrentTenant(TENANT_2);
        ProductCreateDTO productDTO2 = createTestProductDTO(sameSku, "Tenant 2 Product");
        Product savedProduct2 = productService.saveProduct(productDTO2);

        // Then - Both products should be created successfully
        assertThat(savedProduct1).isNotNull();
        assertThat(savedProduct2).isNotNull();
        assertThat(savedProduct1.getSku()).isEqualTo(sameSku);
        assertThat(savedProduct2.getSku()).isEqualTo(sameSku);
        assertThat(savedProduct1.getTitle()).isEqualTo("Tenant 1 Product");
        assertThat(savedProduct2.getTitle()).isEqualTo("Tenant 2 Product");
    }

    @Test
    void shouldUpdateProductInCorrectTenant() {
        // Given - Create product in tenant 1
        TenantContext.setCurrentTenant(TENANT_1);
        ProductCreateDTO productDTO = createTestProductDTO("UPDATE-SKU", "Original Title");
        Product savedProduct = productService.saveProduct(productDTO);

        // When - Update product
        savedProduct.setTitle("Updated Title");
        savedProduct.setPrice(new BigDecimal("199.99"));
        Product updatedProduct = productService.updateProduct(savedProduct.getId(), savedProduct);

        // Then - Product should be updated in tenant 1
        assertThat(updatedProduct.getTitle()).isEqualTo("Updated Title");
        assertThat(updatedProduct.getPrice()).isEqualTo(new BigDecimal("199.99"));

        // Then - Product should not exist in tenant 2
        TenantContext.setCurrentTenant(TENANT_2);
        assertThat(productRepository.findBySku("UPDATE-SKU")).isEmpty();
    }

    @Test
    void shouldDeleteProductFromCorrectTenant() {
        // Given - Create products in both tenants
        TenantContext.setCurrentTenant(TENANT_1);
        ProductCreateDTO productDTO1 = createTestProductDTO("DELETE-SKU", "Tenant 1 Product");
        Product savedProduct1 = productService.saveProduct(productDTO1);

        TenantContext.setCurrentTenant(TENANT_2);
        ProductCreateDTO productDTO2 = createTestProductDTO("DELETE-SKU", "Tenant 2 Product");
        Product savedProduct2 = productService.saveProduct(productDTO2);

        // When - Delete product from tenant 1
        TenantContext.setCurrentTenant(TENANT_1);
        productService.deleteProduct(savedProduct1.getId());

        // Then - Product should be deleted from tenant 1 only
        assertThat(productRepository.findBySku("DELETE-SKU")).isEmpty();

        // Then - Product should still exist in tenant 2
        TenantContext.setCurrentTenant(TENANT_2);
        assertThat(productRepository.findBySku("DELETE-SKU")).isPresent();
    }

    @Test
    void shouldHandleLowStockSearchInTenant() {
        // Given - Create products with different stock levels in tenant 1
        TenantContext.setCurrentTenant(TENANT_1);
        ProductCreateDTO lowStockProduct = createTestProductDTO("LOW-STOCK", "Low Stock Product");
        lowStockProduct.setStockLevel(3);
        lowStockProduct.setLowStockThreshold(5);
        productService.saveProduct(lowStockProduct);

        ProductCreateDTO highStockProduct = createTestProductDTO("HIGH-STOCK", "High Stock Product");
        highStockProduct.setStockLevel(20);
        highStockProduct.setLowStockThreshold(5);
        productService.saveProduct(highStockProduct);

        // When - Search for low stock products
        List<Product> lowStockProducts = productService.getLowStockProducts();

        // Then - Should find only low stock product
        assertThat(lowStockProducts).hasSize(1);
        assertThat(lowStockProducts.get(0).getSku()).isEqualTo("LOW-STOCK");

        // Then - Should not find any products in tenant 2
        TenantContext.setCurrentTenant(TENANT_2);
        List<Product> tenant2LowStock = productService.getLowStockProducts();
        assertThat(tenant2LowStock).isEmpty();
    }

    private ProductCreateDTO createTestProductDTO(String sku, String title) {
        ProductCreateDTO dto = new ProductCreateDTO();
        dto.setSku(sku);
        dto.setTitle(title);
        dto.setDescription("Test product description");
        dto.setCategory("Test Category");
        dto.setPrice(new BigDecimal("99.99"));
        dto.setStockLevel(10);
        dto.setLowStockThreshold(5);
        return dto;
    }

    private void setupTestTenants() {
        setupTenantSchema(TENANT_1);
        setupTenantSchema(TENANT_2);
    }

    private void setupTenantSchema(String tenantId) {
        try (Connection connection = dataSource.getConnection();
             Statement statement = connection.createStatement()) {

            String schemaName = tenantId.toUpperCase();

            // Create schema
            statement.execute("CREATE SCHEMA IF NOT EXISTS " + schemaName);
            statement.execute("SET SCHEMA '" + schemaName + "'");

            // Create product table
            statement.execute("""
                CREATE TABLE IF NOT EXISTS product (
                    id BIGINT GENERATED BY DEFAULT AS IDENTITY PRIMARY KEY,
                    sku VARCHAR(100) NOT NULL UNIQUE,
                    title VARCHAR(255) NOT NULL,
                    description TEXT,
                    category VARCHAR(100) NOT NULL,
                    price DECIMAL(15,2) NOT NULL DEFAULT 0.00,
                    stock_level INTEGER NOT NULL DEFAULT 0,
                    low_stock_threshold INTEGER NOT NULL DEFAULT 5,
                    etsy_product_id VARCHAR(100),
                    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                    created_by BIGINT,
                    updated_by BIGINT,
                    is_active BOOLEAN DEFAULT TRUE,
                    is_featured BOOLEAN DEFAULT FALSE
                )
            """);

        } catch (Exception e) {
            // Ignore setup errors
        }
    }

    private void cleanupTestTenants() {
        cleanupTenantSchema(TENANT_1);
        cleanupTenantSchema(TENANT_2);
    }

    private void cleanupTenantSchema(String tenantId) {
        try (Connection connection = dataSource.getConnection();
             Statement statement = connection.createStatement()) {

            statement.execute("DROP SCHEMA IF EXISTS " + tenantId.toUpperCase() + " CASCADE");

        } catch (Exception e) {
            // Ignore cleanup errors
        }
    }
}
