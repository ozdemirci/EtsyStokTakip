package dev.oasis.stockify.config.security;

import io.jsonwebtoken.*;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Component;

import java.security.Key;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.stream.Collectors;

@Component
@Slf4j
public class JwtTokenProvider {

    @Value("${jwt.secret}")
    private String jwtSecret;

    @Value("${jwt.expiration}")
    private int jwtExpirationInMs;

    @Value("${jwt.issuer}")
    private String jwtIssuer;

    @Value("${jwt.audience}")
    private String jwtAudience;

    public String generateToken(Authentication authentication, String tenantId) {
        UserDetails userPrincipal = (UserDetails) authentication.getPrincipal();
        Date expiryDate = new Date(System.currentTimeMillis() + jwtExpirationInMs * 1000L);

        Map<String, Object> claims = new HashMap<>();
        claims.put("tenant_id", tenantId);
        claims.put("roles", userPrincipal.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .collect(Collectors.toList()));

        return Jwts.builder()
                .setSubject(userPrincipal.getUsername())
                .addClaims(claims)  // Use addClaims instead of setClaims to preserve subject
                .setIssuer(jwtIssuer)
                .setAudience(jwtAudience)
                .setIssuedAt(new Date())
                .setExpiration(expiryDate)
                .signWith(getSigningKey(), SignatureAlgorithm.HS512)
                .compact();
    }

    public String getUsernameFromToken(String token) {
        try {
            Claims claims = Jwts.parserBuilder()
                    .setSigningKey(getSigningKey())
                    .build()
                    .parseClaimsJws(token)
                    .getBody();

            return claims.getSubject();
        } catch (Exception e) {
            log.error("Error extracting username from token: {}", e.getMessage());
            return null;
        }
    }

    public String getTenantIdFromToken(String token) {
        try {
            Claims claims = Jwts.parserBuilder()
                    .setSigningKey(getSigningKey())
                    .build()
                    .parseClaimsJws(token)
                    .getBody();

            return claims.get("tenant_id", String.class);
        } catch (Exception e) {
            log.error("Error extracting tenant ID from token: {}", e.getMessage());
            return null;
        }
    }

    @SuppressWarnings("unchecked")
    public java.util.List<String> getRolesFromToken(String token) {
        try {
            Claims claims = Jwts.parserBuilder()
                    .setSigningKey(getSigningKey())
                    .build()
                    .parseClaimsJws(token)
                    .getBody();

            return (java.util.List<String>) claims.get("roles");
        } catch (Exception e) {
            log.error("Error extracting roles from token: {}", e.getMessage());
            return new java.util.ArrayList<>();
        }
    }

    public Date getExpirationDateFromToken(String token) {
        try {
            Claims claims = Jwts.parserBuilder()
                    .setSigningKey(getSigningKey())
                    .build()
                    .parseClaimsJws(token)
                    .getBody();

            return claims.getExpiration();
        } catch (Exception e) {
            log.error("Error extracting expiration date from token: {}", e.getMessage());
            return null;
        }
    }

    public boolean validateToken(String token) {
        try {
            Jwts.parserBuilder()
                    .setSigningKey(getSigningKey())
                    .build()
                    .parseClaimsJws(token);
            return true;
        } catch (SecurityException ex) {
            log.error("Invalid JWT signature: {}", ex.getMessage());
        } catch (MalformedJwtException ex) {
            log.error("Invalid JWT token: {}", ex.getMessage());
        } catch (ExpiredJwtException ex) {
            log.error("Expired JWT token: {}", ex.getMessage());
        } catch (UnsupportedJwtException ex) {
            log.error("Unsupported JWT token: {}", ex.getMessage());
        } catch (IllegalArgumentException ex) {
            log.error("JWT claims string is empty: {}", ex.getMessage());
        }
        return false;
    }

    private Key getSigningKey() {
        byte[] keyBytes;
        try {
            keyBytes = Decoders.BASE64.decode(jwtSecret);
        } catch (Exception e) {
            log.warn("JWT secret is not base64 encoded, using raw bytes");
            keyBytes = jwtSecret.getBytes();
        }
        
        // Ensure the key is at least 512 bits (64 bytes) for HS512
        if (keyBytes.length < 64) {
            // Pad the key to 64 bytes for HS512
            byte[] paddedKey = new byte[64];
            System.arraycopy(keyBytes, 0, paddedKey, 0, keyBytes.length);
            keyBytes = paddedKey;
        }
        
        return Keys.hmacShaKeyFor(keyBytes);
    }
}
