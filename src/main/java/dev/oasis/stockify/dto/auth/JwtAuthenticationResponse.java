package dev.oasis.stockify.dto.auth;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class JwtAuthenticationResponse {
    
    private String token;
    private String type = "Bearer";
    private String username;
    private String tenantId;
    private java.util.List<String> roles;
    private long expiresIn;
    
    public JwtAuthenticationResponse(String token, String username, String tenantId, java.util.List<String> roles, long expiresIn) {
        this.token = token;
        this.username = username;
        this.tenantId = tenantId;
        this.roles = roles;
        this.expiresIn = expiresIn;
    }
}
