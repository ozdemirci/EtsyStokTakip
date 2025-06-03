package dev.oasis.stockify.config;

import dev.oasis.stockify.model.AppUser;
import dev.oasis.stockify.repository.AppUserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.core.annotation.Order;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Creates the super admin user for tenant management
 * This runs before the main DataLoader to ensure super admin exists
 */
@Slf4j
@Component
@Profile("dev")
@Order(1) // Run before DataLoader
@RequiredArgsConstructor
public class SuperAdminInitializer implements CommandLineRunner {

    private final AppUserRepository appUserRepository;
    private final PasswordEncoder passwordEncoder;

    @Override
    @Transactional
    public void run(String... args) {
        createSuperAdminIfNotExists();
    }    private void createSuperAdminIfNotExists() {
        try {
            // Check if super admin already exists in public schema
            boolean superAdminExists = appUserRepository.findByUsername("superadmin").isPresent();
            
            if (!superAdminExists) {
                log.info("🔧 Creating super admin user...");
                
                AppUser superAdmin = new AppUser();
                superAdmin.setUsername("superadmin");
                superAdmin.setPassword(passwordEncoder.encode("superadmin123"));
                superAdmin.setRole("SUPER_ADMIN");
                superAdmin.setIsActive(true);
                
                appUserRepository.save(superAdmin);
                
                log.info("✅ Super admin user created successfully");
                log.info("📋 Super Admin Credentials:");
                log.info("   Username: superadmin");
                log.info("   Password: superadmin123");
                log.info("   Role: SUPER_ADMIN");
                log.info("⚠️  Please change the password after first login!");
            } else {
                log.info("✓ Super admin user already exists");
            }
            
        } catch (Exception e) {
            log.error("❌ Failed to create super admin user: {}", e.getMessage(), e);
            throw new RuntimeException("Failed to initialize super admin", e);
        }
    }
}
