package dev.oasis.stockify.config;

import dev.oasis.stockify.model.AppUser;
import dev.oasis.stockify.model.Product;
import dev.oasis.stockify.repository.AppUserRepository;
import dev.oasis.stockify.repository.ProductRepository;
import dev.oasis.stockify.config.tenant.TenantContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.DependsOn;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.support.TransactionTemplate;
import javax.sql.DataSource;
import java.math.BigDecimal;
import java.util.Arrays;
import java.util.List;

@Configuration
public class DataLoader {

    private static final Logger log = LoggerFactory.getLogger(DataLoader.class);
    private final List<String> TENANTS = Arrays.asList("tenant1", "tenant2", "tenant3");
    private final DataSource dataSource;
    private final PasswordEncoder passwordEncoder;
    private final JdbcTemplate jdbcTemplate;
    private final TransactionTemplate transactionTemplate;

    public DataLoader(DataSource dataSource,
                     PasswordEncoder passwordEncoder,
                     TransactionTemplate transactionTemplate) {
        this.dataSource = dataSource;
        this.passwordEncoder = passwordEncoder;
        this.jdbcTemplate = new JdbcTemplate(dataSource);
        this.transactionTemplate = transactionTemplate;
    }

    @Bean
    @DependsOn("flywayInitializer")
    public CommandLineRunner initData(AppUserRepository userRepository,
                                    ProductRepository productRepository) {
        log.debug("DataLoader.initData() başlatılıyor...");
        return args -> {
            log.info("DataLoader başlatıldı, örnek verileri yüklemeye başlıyor...");
            for (String tenant : TENANTS) {
                log.info("Tenant için işlemler başlatılıyor: {}", tenant);
                try {
                    // Şemayı oluştur
                    createSchemaWithTables(tenant);

                    // TenantContext'i ayarla ve verileri oluştur
                    transactionTemplate.execute(status -> {
                        try {
                            TenantContext.setCurrentTenant(tenant);

                            // Admin kullanıcısı
                            AppUser admin = new AppUser();
                            admin.setUsername(tenant + "_admin"); // Tenant'a özgü admin kullanıcı adı
                            admin.setPassword(passwordEncoder.encode("admin123"));
                            admin.setRole("ADMIN");
                            userRepository.save(admin);
                            log.info("Admin kullanıcısı oluşturuldu - Tenant: {} - ID: {}", tenant, admin.getId());

                            // Normal kullanıcı
                            AppUser user = new AppUser();
                            user.setUsername(tenant + "_user"); // Tenant'a özgü normal kullanıcı adı
                            user.setPassword(passwordEncoder.encode("user123"));
                            user.setRole("USER");
                            userRepository.save(user);
                            log.info("Normal kullanıcı oluşturuldu - Tenant: {} - ID: {}", tenant, user.getId());

                            // Örnek ürünleri oluştur
                            createSampleProducts(productRepository, tenant);

                            return null;
                        } finally {
                            TenantContext.clear();
                        }
                    });

                    log.info("Tenant için işlemler tamamlandı: {}", tenant);
                } catch (Exception e) {
                    log.error("Tenant işlemlerinde hata: {} - {}", tenant, e.getMessage(), e);
                }
            }
        };
    }

    private void createSchemaWithTables(String schema) {
        try {
            // Şema oluştur
            jdbcTemplate.execute("CREATE SCHEMA IF NOT EXISTS " + schema);

            // Tablo SQL'lerini direkt olarak çalıştır
            jdbcTemplate.execute(String.format("""
                CREATE TABLE IF NOT EXISTS %s.app_user (
                    id BIGINT AUTO_INCREMENT PRIMARY KEY,
                    username VARCHAR(20) NOT NULL UNIQUE,
                    password VARCHAR(255) NOT NULL,
                    role VARCHAR(50) NOT NULL
                )
            """, schema));

            jdbcTemplate.execute(String.format("""
                CREATE TABLE IF NOT EXISTS %s.product (
                    id BIGINT AUTO_INCREMENT PRIMARY KEY,
                    title VARCHAR(50) NOT NULL,
                    description VARCHAR(1000),
                    sku VARCHAR(50) NOT NULL UNIQUE,
                    category VARCHAR(255) NOT NULL,
                    price DECIMAL(10,2) NOT NULL,
                    stock_level INT NOT NULL,
                    low_stock_threshold INT NOT NULL,
                    etsy_product_id VARCHAR(255)
                )
            """, schema));

            jdbcTemplate.execute(String.format("""
                CREATE TABLE IF NOT EXISTS %s.stock_notification (
                    id BIGINT AUTO_INCREMENT PRIMARY KEY,
                    product_id BIGINT NOT NULL,
                    created_at TIMESTAMP NOT NULL,
                    read BOOLEAN NOT NULL DEFAULT FALSE,
                    message VARCHAR(255) NOT NULL,
                    FOREIGN KEY (product_id) REFERENCES %s.product(id)
                )
            """, schema, schema));

            log.info("Şema ve tablolar oluşturuldu: {}", schema);
        } catch (Exception e) {
            log.error("Şema oluşturma hatası: {} - {}", schema, e.getMessage());
            throw new RuntimeException("Şema oluşturulamadı: " + schema, e);
        }
    }

    private void createSampleProducts(ProductRepository productRepository, String tenant) {
        try {
            // Örnek ürün 1
            Product product1 = new Product();
            product1.setTitle("Örnek Ürün 1");
            product1.setSku(tenant + "-SKU-001");
            product1.setDescription("Bu bir örnek üründür - " + tenant);
            product1.setCategory("Elektronik");
            product1.setPrice(new BigDecimal("999.99"));
            product1.setStockLevel(100);
            product1.setLowStockThreshold(20);
            productRepository.save(product1);

            // Örnek ürün 2
            Product product2 = new Product();
            product2.setTitle("Örnek Ürün 2");
            product2.setSku(tenant + "-SKU-002");
            product2.setDescription("Bu bir başka örnek üründür - " + tenant);
            product2.setCategory("Aksesuar");
            product2.setPrice(new BigDecimal("149.99"));
            product2.setStockLevel(50);
            product2.setLowStockThreshold(10);
            productRepository.save(product2);

            log.info("Örnek ürünler oluşturuldu - Tenant: {}", tenant);
        } catch (Exception e) {
            log.error("Ürün oluşturma hatası - Tenant: {} - {}", tenant, e.getMessage());
            throw e;
        }
    }
}
