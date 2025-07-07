package dev.oasis.stockify.config.security;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.test.context.TestPropertySource;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@TestPropertySource(properties = {
    "jwt.secret=dGhpcyBpcyBhIHNlY3VyZSBrZXkgZm9yIEpXVCB0b2tlbiBnZW5lcmF0aW9uIGFuZCB2YWxpZGF0aW9uIHdpdGggSFM1MTIgYWxnb3JpdGht",
    "jwt.expiration=3600",
    "jwt.issuer=test-issuer",
    "jwt.audience=test-audience"
})
class JwtTokenProviderTest {

    @Autowired
    private JwtTokenProvider jwtTokenProvider;

    @Test
    void testGenerateAndValidateToken() {
        // Given
        UserDetails userDetails = User.builder()
            .username("testuser")
            .password("password")
            .authorities(List.of(new SimpleGrantedAuthority("ROLE_USER")))
            .build();
        
        Authentication authentication = new UsernamePasswordAuthenticationToken(
            userDetails, null, userDetails.getAuthorities());
        
        String tenantId = "test-tenant";
        
        // When
        String token = jwtTokenProvider.generateToken(authentication, tenantId);
        System.out.println("Generated token: " + token);
        
        // Then
        assertNotNull(token);
        assertTrue(jwtTokenProvider.validateToken(token));
        
        // Verify token contents
        String extractedUsername = jwtTokenProvider.getUsernameFromToken(token);
        System.out.println("Extracted username: " + extractedUsername);
        assertEquals("testuser", extractedUsername);
        assertEquals(tenantId, jwtTokenProvider.getTenantIdFromToken(token));
        
        List<String> roles = jwtTokenProvider.getRolesFromToken(token);
        assertNotNull(roles);
        assertTrue(roles.contains("ROLE_USER"));
    }

    @Test
    void testInvalidToken() {
        // Given
        String invalidToken = "invalid.token.here";
        
        // When & Then
        assertFalse(jwtTokenProvider.validateToken(invalidToken));
    }

    @Test
    void testExtractingFromValidToken() {
        // Given
        UserDetails userDetails = User.builder()
            .username("admin")
            .password("password")
            .authorities(List.of(
                new SimpleGrantedAuthority("ROLE_ADMIN"),
                new SimpleGrantedAuthority("ROLE_USER")
            ))
            .build();
        
        Authentication authentication = new UsernamePasswordAuthenticationToken(
            userDetails, null, userDetails.getAuthorities());
        
        String tenantId = "admin-tenant";
        String token = jwtTokenProvider.generateToken(authentication, tenantId);
        
        // When & Then
        assertEquals("admin", jwtTokenProvider.getUsernameFromToken(token));
        assertEquals(tenantId, jwtTokenProvider.getTenantIdFromToken(token));
        
        List<String> roles = jwtTokenProvider.getRolesFromToken(token);
        assertEquals(2, roles.size());
        assertTrue(roles.contains("ROLE_ADMIN"));
        assertTrue(roles.contains("ROLE_USER"));
        
        assertNotNull(jwtTokenProvider.getExpirationDateFromToken(token));
    }
}
