package dev.oasis.stockify.controller.api;

import dev.oasis.stockify.config.security.JwtTokenProvider;
import dev.oasis.stockify.config.tenant.TenantContext;
import dev.oasis.stockify.dto.auth.JwtAuthenticationRequest;
import dev.oasis.stockify.dto.auth.JwtAuthenticationResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.web.bind.annotation.*;

import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
@Slf4j
public class JwtAuthenticationController {

    private final AuthenticationManager authenticationManager;
    private final JwtTokenProvider jwtTokenProvider;
    
    @Value("${jwt.expiration}")
    private long jwtExpiration;

    @PostMapping("/login")
    public ResponseEntity<?> authenticateUser(@Valid @RequestBody JwtAuthenticationRequest loginRequest) {
        try {
            // Set tenant context before authentication
            TenantContext.setCurrentTenant(loginRequest.getTenantId());
            log.info("🔒 API Authentication attempt for user: {} in tenant: {}", 
                    loginRequest.getUsername(), loginRequest.getTenantId());
            
            Authentication authentication = authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(
                    loginRequest.getUsername(),
                    loginRequest.getPassword()
                )
            );
            
            // Generate JWT token
            String jwt = jwtTokenProvider.generateToken(authentication, loginRequest.getTenantId());
            
            // Extract roles
            java.util.List<String> roles = authentication.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .collect(Collectors.toList());
            
            log.info("✅ API Authentication successful for user: {} in tenant: {}", 
                    loginRequest.getUsername(), loginRequest.getTenantId());
            
            return ResponseEntity.ok(new JwtAuthenticationResponse(
                jwt,
                loginRequest.getUsername(),
                loginRequest.getTenantId(),
                roles,
                jwtExpiration
            ));
            
        } catch (AuthenticationException ex) {
            log.error("❌ API Authentication failed for user: {} in tenant: {}, Error: {}", 
                    loginRequest.getUsername(), loginRequest.getTenantId(), ex.getMessage());
            return ResponseEntity.badRequest()
                .body(new ErrorResponse("Invalid credentials", "Authentication failed"));
        } catch (Exception ex) {
            log.error("❌ API Authentication error for user: {} in tenant: {}, Error: {}", 
                    loginRequest.getUsername(), loginRequest.getTenantId(), ex.getMessage());
            return ResponseEntity.internalServerError()
                .body(new ErrorResponse("Authentication error", "Internal server error"));
        } finally {
            // Clear tenant context
            TenantContext.clear();
        }
    }

    @PostMapping("/refresh")
    public ResponseEntity<?> refreshToken(@RequestHeader("Authorization") String bearerToken) {
        try {
            if (bearerToken != null && bearerToken.startsWith("Bearer ")) {
                String token = bearerToken.substring(7);
                
                if (jwtTokenProvider.validateToken(token)) {
                    String username = jwtTokenProvider.getUsernameFromToken(token);
                    String tenantId = jwtTokenProvider.getTenantIdFromToken(token);
                    java.util.List<String> roles = jwtTokenProvider.getRolesFromToken(token);
                    
                    // Set tenant context
                    TenantContext.setCurrentTenant(tenantId);
                    
                    // Create a mock authentication object for token generation
                    Authentication auth = new UsernamePasswordAuthenticationToken(username, null, 
                        roles.stream().map(role -> (GrantedAuthority) () -> role).collect(Collectors.toList()));
                    
                    String newToken = jwtTokenProvider.generateToken(auth, tenantId);
                    
                    log.info("✅ Token refreshed for user: {} in tenant: {}", username, tenantId);
                    
                    return ResponseEntity.ok(new JwtAuthenticationResponse(
                        newToken,
                        username,
                        tenantId,
                        roles,
                        jwtExpiration
                    ));
                }
            }
            
            return ResponseEntity.badRequest()
                .body(new ErrorResponse("Invalid token", "Token refresh failed"));
                
        } catch (Exception ex) {
            log.error("❌ Token refresh error: {}", ex.getMessage());
            return ResponseEntity.internalServerError()
                .body(new ErrorResponse("Token refresh error", "Internal server error"));
        } finally {
            TenantContext.clear();
        }
    }

    // Simple error response class
    private static class ErrorResponse {
        private final String error;
        private final String message;
        
        public ErrorResponse(String error, String message) {
            this.error = error;
            this.message = message;
        }
        
        public String getError() {
            return error;
        }
        
        public String getMessage() {
            return message;
        }
    }
}
