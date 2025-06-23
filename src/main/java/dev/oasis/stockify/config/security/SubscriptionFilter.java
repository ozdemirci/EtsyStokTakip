package dev.oasis.stockify.config.security;

import dev.oasis.stockify.config.tenant.TenantContext;
import dev.oasis.stockify.service.SubscriptionService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.annotation.Order;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * Filter to check subscription limits and trial expiry
 */
@Component
@Order(3)
@Slf4j
@RequiredArgsConstructor
public class SubscriptionFilter extends OncePerRequestFilter {
    
    private final SubscriptionService subscriptionService;
    
    @Override
    protected void doFilterInternal(@NonNull HttpServletRequest request, 
                                  @NonNull HttpServletResponse response,
                                  @NonNull FilterChain filterChain) throws ServletException, IOException {
        
        String tenantId = TenantContext.getCurrentTenant();
        String requestURI = request.getRequestURI();
        
        // Skip checks for public endpoints and authentication flows
        if (shouldSkipSubscriptionCheck(requestURI) || tenantId == null || "public".equals(tenantId)) {
            filterChain.doFilter(request, response);
            return;
        }
        
        try {
            // Check if trial has expired
            if (subscriptionService.isTrialExpired()) {
                log.warn("⚠️ Trial expired for tenant: {}", tenantId);
                
                // If this is an API call, return error
                if (requestURI.startsWith("/api/")) {
                    response.setStatus(HttpServletResponse.SC_FORBIDDEN);
                    response.setContentType("application/json");
                    response.getWriter().write("{\"error\":\"Deneme süresi sona erdi. Lütfen aboneliğinizi yükseltin.\"}");
                    return;
                }
                
                // Redirect to trial expired page
                if (!requestURI.equals("/trial-expired")) {
                    response.sendRedirect("/trial-expired");
                    return;
                }
            }
            
            filterChain.doFilter(request, response);
            
        } catch (Exception e) {
            log.error("❌ Error in subscription filter for tenant {}: {}", tenantId, e.getMessage());
            // Don't block request on filter errors
            filterChain.doFilter(request, response);
        }
    }
    
    @Override
    protected boolean shouldNotFilter(@NonNull HttpServletRequest request) {
        String path = request.getRequestURI();
        return shouldSkipSubscriptionCheck(path);
    }
    
    private boolean shouldSkipSubscriptionCheck(String path) {
        return path.equals("/login") ||
               path.equals("/logout") ||
               path.equals("/") ||
               path.equals("/register") ||
               path.startsWith("/register/") ||
               path.startsWith("/css/") ||
               path.startsWith("/js/") ||
               path.startsWith("/images/") ||
               path.startsWith("/actuator/") ||
               path.equals("/favicon.ico") ||
               path.equals("/error") ||
               path.equals("/trial-expired") ||
               path.equals("/access-denied");
    }
}
