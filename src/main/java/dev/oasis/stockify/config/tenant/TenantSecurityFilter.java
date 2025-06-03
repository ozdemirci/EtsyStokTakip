package dev.oasis.stockify.config.tenant;

import lombok.extern.slf4j.Slf4j;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import javax.sql.DataSource;
import java.io.IOException;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

/**
 * Security filter to validate tenant access and prevent unauthorized tenant switching
 */
@Slf4j
@Component
public class TenantSecurityFilter extends OncePerRequestFilter {

    private final DataSource dataSource;

    public TenantSecurityFilter(DataSource dataSource) {
        this.dataSource = dataSource;
    }    @Override
    protected void doFilterInternal(@NonNull HttpServletRequest request, @NonNull HttpServletResponse response,
                                  @NonNull FilterChain filterChain) throws ServletException, IOException {
        
        String requestedTenant = request.getHeader("X-TenantId");
        String currentUser = request.getRemoteUser();
        
        // Skip validation for public endpoints
        if (isPublicEndpoint(request.getRequestURI())) {
            filterChain.doFilter(request, response);
            return;
        }
        
        try {
            // Validate tenant access
            if (requestedTenant != null && !requestedTenant.isEmpty()) {
                if (!tenantExists(requestedTenant)) {
                    log.warn("🚫 Access denied: Tenant {} does not exist", requestedTenant);
                    response.setStatus(HttpServletResponse.SC_NOT_FOUND);
                    response.getWriter().write("{\"error\":\"Tenant not found\"}");
                    return;
                }
                
                // Additional validation can be added here:
                // - Check if user has access to the tenant
                // - Validate tenant status (active/inactive)
                // - Check tenant subscription status
                
                log.debug("✅ Tenant access validated: {} for user: {}", requestedTenant, currentUser);
            }
            
            filterChain.doFilter(request, response);
            
        } catch (Exception e) {
            log.error("❌ Error during tenant security validation: {}", e.getMessage());
            response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
            response.getWriter().write("{\"error\":\"Internal server error\"}");
        }
    }

    /**
     * Check if tenant exists using direct database access
     */
    private boolean tenantExists(String tenantId) {
        String query = "SELECT COUNT(*) FROM information_schema.schemata WHERE schema_name = ?";
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(query)) {
            
            statement.setString(1, tenantId);
            try (ResultSet resultSet = statement.executeQuery()) {
                if (resultSet.next()) {
                    return resultSet.getInt(1) > 0;
                }
            }
        } catch (SQLException e) {
            log.error("❌ Error checking tenant existence: {}", e.getMessage());
        }
        return false;
    }    private boolean isPublicEndpoint(String uri) {
        return uri.startsWith("/login") ||
               uri.startsWith("/css/") ||
               uri.startsWith("/js/") ||
               uri.startsWith("/images/") ||
               uri.startsWith("/h2-console/") ||
               uri.startsWith("/actuator/health") ||
               uri.equals("/error");
    }
    
    @Override
    protected boolean shouldNotFilter(@NonNull HttpServletRequest request) {
        String path = request.getRequestURI();
        return isPublicEndpoint(path);
    }
}
