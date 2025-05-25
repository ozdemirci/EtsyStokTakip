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
            logger.debug("Authentication request received for username: {}", username);
            writeLogToFile("Authentication request received for username: " + username);

            if (username == null || username.trim().isEmpty()) {
                String errorMsg = "Username cannot be empty";
                logger.error(errorMsg);
                writeLogToFile(errorMsg);
                throw new UsernameNotFoundException(errorMsg);
            }

            logger.info("Searching for user in database: {}", username);
            writeLogToFile("Searching for user in database: " + username);
            
            AppUser appUser = appUserRepository.findByUsername(username)
                    .orElseThrow(() -> {
                        String errorMsg = "User not found in database: " + username;
                        logger.warn(errorMsg);
                        writeLogToFile(errorMsg);
                        return new UsernameNotFoundException(errorMsg);
                    });

            logger.info("User found successfully: {}", appUser.getUsername());
            logger.debug("User details - ID: {}, Role: {}", appUser.getId(), appUser.getRole());
            logger.debug("Password hash length: {}", appUser.getPassword().length());
            
            writeLogToFile("User found successfully: " + appUser.getUsername());
            writeLogToFile("User details - Role: " + appUser.getRole());
            writeLogToFile("Password hash present: " + (appUser.getPassword() != null && !appUser.getPassword().isEmpty()));

            UserDetails userDetails = User.withUsername(appUser.getUsername())
                    .password(appUser.getPassword())
                    .roles(appUser.getRole().toUpperCase())
                    .build();

            logger.info("UserDetails object created successfully for user: {}", username);
            writeLogToFile("UserDetails object created successfully for user: " + username);
            
            return userDetails;
        } catch (Exception e) {
            String errorMsg = "Error during authentication for user '" + username + "': " + e.getMessage();
            logger.error(errorMsg, e);
            writeLogToFile(errorMsg);
            throw e;
        }
    }

    private void writeLogToFile(String log) {
        try {
            String timestamp = java.time.LocalDateTime.now().toString();
            String logEntry = timestamp + " - " + log + System.lineSeparator();
            
            java.nio.file.Path logPath = java.nio.file.Paths.get("/app/logs/auth-debug.txt");
            java.nio.file.Files.createDirectories(logPath.getParent());
            
            java.nio.file.Files.write(
                logPath,
                logEntry.getBytes(),
                java.nio.file.StandardOpenOption.CREATE,
                java.nio.file.StandardOpenOption.APPEND
            );
        } catch (Exception ex) {
            logger.error("Failed to write log to file: {}", ex.getMessage(), ex);
        }
    }
}
