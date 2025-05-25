package com.yourcompany.etsystoktakip;

import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.security.crypto.password.PasswordEncoder;

import com.yourcompany.etsystoktakip.model.AppUser;
import com.yourcompany.etsystoktakip.repository.AppUserRepository;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@SpringBootApplication
public class EtsyStokTakipApplication {
    private static final Logger logger = LoggerFactory.getLogger(EtsyStokTakipApplication.class);
    public static void main(String[] args) {
        logger.info("Application starting. Checking database connection and default users...");
        SpringApplication.run(EtsyStokTakipApplication.class, args);
    }

    @Bean
    public CommandLineRunner createDefaultUsers(AppUserRepository userRepository, PasswordEncoder encoder) {
        return args -> {
            logger.info("Checking/creating default admin user...");
            AppUser admin = userRepository.findByUsername("admin").orElse(null);
            if (admin == null) {
                logger.info("Admin user not found. Creating new admin user.");
                admin = new AppUser();
                admin.setUsername("admin");
            } else {
                logger.info("Admin user already exists. Updating password and role.");
            }
            admin.setPassword(encoder.encode("admin123"));
            admin.setRole("ADMIN");
            userRepository.saveAndFlush(admin);
            logger.info("Admin user in DB: username={}, passwordHash={}, role={}", admin.getUsername(), admin.getPassword(), admin.getRole());
        };
    }
}
