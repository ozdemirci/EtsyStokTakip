package dev.oasis.stockify.repository;

import dev.oasis.stockify.model.Product;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import javax.sql.DataSource;
import java.math.BigDecimal;
import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@ActiveProfiles("test")
public class ProductRepositoryTest {

    @Autowired
    private ProductRepository productRepository;

    @Autowired
    private DataSource dataSource;

    @BeforeEach
    void setUp() {
        // Her test için yeni bir test şeması oluştur
        String schemaName = "test_schema_" + System.currentTimeMillis();
        JdbcTemplate jdbcTemplate = new JdbcTemplate(dataSource);
        jdbcTemplate.execute("CREATE SCHEMA " + schemaName);
        jdbcTemplate.execute("SET SCHEMA " + schemaName);
    }

    @Test
    void shouldCreateAndFindProduct() {
        // Test ürünü oluştur
        Product product = new Product();
        product.setTitle("Test Ürün");
        product.setSku("TEST-001");
        product.setCategory("Test Kategori");
        product.setPrice(new BigDecimal("99.99"));
        product.setStockLevel(10);
        product.setLowStockThreshold(5);

        // Ürünü kaydet
        Product savedProduct = productRepository.save(product);

        // Kaydedilen ürünü bul ve kontrol et
        Product foundProduct = productRepository.findById(savedProduct.getId()).orElse(null);
        assertThat(foundProduct).isNotNull();
        assertThat(foundProduct.getSku()).isEqualTo("TEST-001");
    }

    @Test
    void shouldFindProductBySku() {
        // Test ürünü oluştur
        Product product = new Product();
        product.setTitle("Test Ürün");
        product.setSku("TEST-002");
        product.setCategory("Test Kategori");
        product.setPrice(new BigDecimal("149.99"));
        product.setStockLevel(20);
        product.setLowStockThreshold(5);
        productRepository.save(product);

        // SKU ile ürünü bul
        Product foundProduct = productRepository.findBySku("TEST-002").orElse(null);
        assertThat(foundProduct).isNotNull();
        assertThat(foundProduct.getTitle()).isEqualTo("Test Ürün");
    }
}
