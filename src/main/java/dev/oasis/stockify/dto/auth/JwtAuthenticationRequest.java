package dev.oasis.stockify.dto.auth;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class JwtAuthenticationRequest {
    
    @NotBlank(message = "Username is required")
    private String username;
    
    @NotBlank(message = "Password is required")
    private String password;
    
    @NotBlank(message = "Tenant ID is required")
    private String tenantId;
}
