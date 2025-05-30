package dev.oasis.stockify.config;

import dev.oasis.stockify.model.AppUser;
import dev.oasis.stockify.model.Product;
import dev.oasis.stockify.repository.AppUserRepository;
import dev.oasis.stockify.repository.ProductRepository;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.Arrays;
import java.util.List;

@Component
public class DataLoader implements CommandLineRunner {

    private final AppUserRepository appUserRepository;
    private final PasswordEncoder passwordEncoder;
    private final ProductRepository productRepository;

    public DataLoader(AppUserRepository appUserRepository,
                     PasswordEncoder passwordEncoder,
                     ProductRepository productRepository) {
        this.appUserRepository = appUserRepository;
        this.passwordEncoder = passwordEncoder;
        this.productRepository = productRepository;
    }

    @Override
    @Transactional
    public void run(String... args) {
         createDefaultAdminIfNeeded();
         createDefaultProductsIfNeeded();
    }

    private void createDefaultAdminIfNeeded() {
        String adminUsername = "admin";
        
        // Check if admin exists
        boolean adminExists = appUserRepository.findByUsername(adminUsername).isPresent();
        
        if (!adminExists) {
            
            AppUser adminUser = new AppUser();
            adminUser.setUsername(adminUsername);
            String password = "admin123";
            adminUser.setPassword(passwordEncoder.encode(password));
            adminUser.setRole("ADMIN");
            
            try {
                appUserRepository.save(adminUser);
            } catch (Exception e) {
                throw new RuntimeException("Admin kullanıcısı oluşturulamadı", e);
            }
        } 
    }

    private void createDefaultProductsIfNeeded() {
        // Check if we already have products
        if (productRepository.count() == 0) {
            List<Product> defaultProducts = Arrays.asList(
                createProduct("El Yapımı Kolye", "Takı", new BigDecimal("149.99"), 10, 3),
                createProduct("Örgü Bebek", "Oyuncak", new BigDecimal("199.99"), 15, 5),
                createProduct("Ahşap Tepsi", "Ev Dekorasyonu", new BigDecimal("299.99"), 8, 3),
                createProduct("Makrome Duvar Süsü", "Ev Dekorasyonu", new BigDecimal("249.99"), 12, 4),
                createProduct("El Yapımı Sabun Seti", "Kozmetik", new BigDecimal("89.99"), 20, 8),
                createProduct("Keçe Çanta", "Aksesuar", new BigDecimal("179.99"), 7, 3)
            );

            try {
                productRepository.saveAll(defaultProducts);
            } catch (Exception e) {
                throw new RuntimeException("Örnek ürünler oluşturulurken hata oluştu", e);
            }
        }
    }

    private Product createProduct(String title, String category, BigDecimal price, int stockLevel, int lowStockThreshold) {
        Product product = new Product();
        product.setTitle(title);
        product.setCategory(category);
        product.setPrice(price);
        product.setStockLevel(stockLevel);
        product.setLowStockThreshold(lowStockThreshold);
        return product;
    }
}
