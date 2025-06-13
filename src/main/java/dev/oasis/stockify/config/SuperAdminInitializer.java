package dev.oasis.stockify.config;

import dev.oasis.stockify.config.tenant.TenantContext;
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
 * Creates the super admin user for tenant management in the 'stockify' tenant schema
 * This runs before the main DataLoader to ensure super admin exists
 */
@Slf4j
@Component
@Profile("dev")
@Order(1) // Run before DataLoader
@RequiredArgsConstructor
public class SuperAdminInitializer implements CommandLineRunner {

    private final AppUserRepository appUserRepository;
    private final PasswordEncoder passwordEncoder;    @Override
    @Transactional
    public void run(String... args) {
        createSuperAdminIfNotExists();
    }

    private void createSuperAdminIfNotExists() {
        try {
            // Set tenant context to 'stockify' for super admin
            TenantContext.setCurrentTenant("stockify");
            
            // Check if super admin already exists in stockify schema
            boolean superAdminExists = appUserRepository.findByUsername("superadmin").isPresent();
            
            if (!superAdminExists) {
                log.info("🔧 Creating super admin user in 'stockify' tenant...");
                
                AppUser superAdmin = new AppUser();
                superAdmin.setUsername("superadmin");
                superAdmin.setPassword(passwordEncoder.encode("superadmin123"));
                superAdmin.setRole("SUPER_ADMIN");
                superAdmin.setIsActive(true);
                
                appUserRepository.save(superAdmin);
                
                log.info("✅ Super admin user created successfully in 'stockify' tenant");
                log.info("📋 Super Admin Credentials:");
                log.info("   Tenant: stockify");
                log.info("   Username: superadmin");
                log.info("   Password: superadmin123");
                log.info("   Role: SUPER_ADMIN");
                log.info("⚠️  Please change the password after first login!");
            } else {
                log.info("✓ Super admin user already exists in 'stockify' tenant");
            }
            
        } catch (Exception e) {
            log.error("❌ Failed to create super admin user in 'stockify' tenant: {}", e.getMessage(), e);
            throw new RuntimeException("Failed to initialize super admin", e);
        } finally {
            // Always clear tenant context
            TenantContext.clear();
        }
    }
}
