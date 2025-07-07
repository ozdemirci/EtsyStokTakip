package dev.oasis.stockify.controller.api;

import dev.oasis.stockify.config.tenant.TenantContext;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/user")
@Slf4j
public class ApiUserController {

    @GetMapping("/profile")
    public ResponseEntity<Map<String, Object>> getUserProfile() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        String currentTenant = TenantContext.getCurrentTenant();
        
        log.info("API Profile request for user: {} in tenant: {}", 
                authentication.getName(), currentTenant);
        
        Map<String, Object> profile = new HashMap<>();
        profile.put("username", authentication.getName());
        profile.put("tenant", currentTenant);
        profile.put("roles", authentication.getAuthorities());
        profile.put("authenticated", authentication.isAuthenticated());
        
        return ResponseEntity.ok(profile);
    }

    @GetMapping("/dashboard")
    public ResponseEntity<Map<String, Object>> getDashboard() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        String currentTenant = TenantContext.getCurrentTenant();
        
        log.info("API Dashboard request for user: {} in tenant: {}", 
                authentication.getName(), currentTenant);
        
        Map<String, Object> dashboard = new HashMap<>();
        dashboard.put("message", "Welcome to Stockify API Dashboard");
        dashboard.put("user", authentication.getName());
        dashboard.put("tenant", currentTenant);
        dashboard.put("timestamp", System.currentTimeMillis());
        
        return ResponseEntity.ok(dashboard);
    }
}
