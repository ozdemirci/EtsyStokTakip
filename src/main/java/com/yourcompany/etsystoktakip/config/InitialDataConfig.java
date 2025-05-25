package com.yourcompany.etsystoktakip.config;

import com.yourcompany.etsystoktakip.model.AppUser;
import com.yourcompany.etsystoktakip.repository.AppUserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
public class InitialDataConfig implements CommandLineRunner {
    private static final Logger logger = LoggerFactory.getLogger(InitialDataConfig.class);

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
            logger.info("Varsayılan admin kullanıcısı bulunamadı. Yeni admin kullanıcısı oluşturuluyor...");
            
            AppUser adminUser = new AppUser();
            adminUser.setUsername(adminUsername);
            String password = "admin123";
            adminUser.setPassword(passwordEncoder.encode(password));
            adminUser.setRole("ADMIN");
            
            try {
                AppUser savedAdmin = appUserRepository.save(adminUser);
                logger.info("Varsayılan admin kullanıcısı başarıyla oluşturuldu:");
                logger.info("Kullanıcı adı: {}", savedAdmin.getUsername());
                logger.info("Rol: {}", savedAdmin.getRole());
                logger.info("Şifre: {}", password);
                logger.debug("Password hash: {}", savedAdmin.getPassword());
            } catch (Exception e) {
                logger.error("Varsayılan admin kullanıcısı oluşturulurken hata: {}", e.getMessage());
                throw new RuntimeException("Admin kullanıcısı oluşturulamadı", e);
            }
        } else {
            logger.info("Admin kullanıcısı zaten mevcut. Yeni admin kullanıcısı oluşturulmadı.");
        }
    }
}
