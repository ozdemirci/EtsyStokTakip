package com.yourcompany.etsystoktakip.service;

import com.yourcompany.etsystoktakip.model.AppUser;
import com.yourcompany.etsystoktakip.repository.AppUserRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Service
public class AppUserDetailsService implements UserDetailsService {
    private static final Logger logger = LoggerFactory.getLogger(AppUserDetailsService.class);
    @Autowired
    private AppUserRepository appUserRepository;

    @Override
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        try {
            logger.info("Trying to load user by username: {}", username);
            AppUser appUser = appUserRepository.findByUsername(username)
                    .orElseThrow(() -> {
                        logger.warn("User not found: {}", username);
                        writeLogToFile("User not found: " + username);
                        return new UsernameNotFoundException("Kullanıcı bulunamadı: " + username);
                    });
            logger.info("User found: {} (role: {})", appUser.getUsername(), appUser.getRole());
            logger.info("User password hash: {}", appUser.getPassword());
            writeLogToFile("User found: " + appUser.getUsername() + " (role: " + appUser.getRole() + ")");
            writeLogToFile("User password hash: " + appUser.getPassword());
            return User.withUsername(appUser.getUsername())
                    .password(appUser.getPassword())
                    .roles(appUser.getRole().toUpperCase())
                    .build();
        } catch (Exception e) {
            writeLogToFile("Exception in loadUserByUsername: " + e.getMessage());
            throw e;
        }
    }

    private void writeLogToFile(String log) {
        try {
            java.nio.file.Files.write(
                java.nio.file.Paths.get("/app/logs/auth-debug.txt"),
                (java.time.LocalDateTime.now() + " - " + log + System.lineSeparator()).getBytes(),
                java.nio.file.StandardOpenOption.CREATE, java.nio.file.StandardOpenOption.APPEND
            );
        } catch (Exception ex) {
            logger.error("Failed to write log to file: {}", ex.getMessage());
        }
    }
}
