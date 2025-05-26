package com.yourcompany.etsystoktakip.config;

import com.yourcompany.etsystoktakip.model.AppUser;
import com.yourcompany.etsystoktakip.repository.AppUserRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
public class InitialDataConfig implements CommandLineRunner {

    @Autowired
    private AppUserRepository appUserRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Override
    @Transactional
    public void run(String... args) {
        createDefaultAdminIfNeeded();
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
                AppUser savedAdmin = appUserRepository.save(adminUser);
            } catch (Exception e) {
                throw new RuntimeException("Admin kullanıcısı oluşturulamadı", e);
            }
        } 
    }
}
