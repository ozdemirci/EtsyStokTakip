package dev.oasis.stockify.config;

import dev.oasis.stockify.model.Product;
import dev.oasis.stockify.repository.ProductRepository;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import javax.sql.DataSource;
import java.math.BigDecimal;
import java.sql.Connection;

@Configuration
@Profile("disabled-test") // Disabled - using DataLoader instead
public class TestDataConfig {

    private final DataSource dataSource;

    public TestDataConfig(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    @Bean
    public CommandLineRunner loadData(ProductRepository productRepository) {
        return args -> {
            // Test şemalarını oluştur
            createSchema("tenant1");
            createSchema("tenant2");

            // Test verilerini ekle
            Product product1 = new Product();
            product1.setTitle("Test Ürün 1");
            product1.setSku("SKU-001");
            product1.setDescription("Test ürünü 1");
            product1.setCategory("Test");
            product1.setPrice(new BigDecimal("99.99"));
            product1.setStockLevel(50);
            product1.setLowStockThreshold(10);
            productRepository.save(product1);
        };
    }

    private void createSchema(String schemaName) {
        try (Connection conn = dataSource.getConnection()) {
            conn.createStatement().execute("CREATE SCHEMA IF NOT EXISTS " + schemaName);
        } catch (Exception e) {
            throw new RuntimeException("Schema oluşturma hatası: " + schemaName, e);
        }
    }
}
