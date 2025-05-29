package dev.oasis.stockify;

import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.security.crypto.password.PasswordEncoder;

import dev.oasis.stockify.model.AppUser;
import dev.oasis.stockify.repository.AppUserRepository;


@SpringBootApplication
public class Stockify {
    
    public static void main(String[] args) {

        SpringApplication.run(Stockify.class, args);
    }



    /**
     * Uygulama başlatıldığında çalışacak komut satırı koşucusu.
     * Varsayılan admin kullanıcısını oluşturur.
     */
   // @Bean
    CommandLineRunner commandLineRunner(AppUserRepository appUserRepository, 
                                        PasswordEncoder passwordEncoder) {

        return args -> {            
            
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


        };
    }





    




}
