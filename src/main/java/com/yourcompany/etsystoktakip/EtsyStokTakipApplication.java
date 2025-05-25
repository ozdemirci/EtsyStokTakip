package com.yourcompany.etsystoktakip;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@SpringBootApplication
public class EtsyStokTakipApplication {
    private static final Logger logger = LoggerFactory.getLogger(EtsyStokTakipApplication.class);
    
    public static void main(String[] args) {
        logger.info("Application starting...");
        SpringApplication.run(EtsyStokTakipApplication.class, args);
    }
}
