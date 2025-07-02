package dev.oasis.stockify.util;

import dev.oasis.stockify.config.tenant.TenantContext;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Component;
import lombok.extern.slf4j.Slf4j;

/**
 * Utility class for centralized tenant resolution.
 * Provides methods to consistently resolve the current tenant ID from various sources.
 */
@Component
@Slf4j
public class ControllerTenantUtil {

    // Standard header name for tenant ID
    private static final String TENANT_HEADER = "X-TenantId";
    
    /**
     * Resolves the current tenant ID from various sources in a consistent order:
     * 1. Current tenant context
     * 2. Session attribute
     * 3. Request header
     * 4. Request parameter
     * 
     * @param request HTTP request
     * @param authentication Authentication object (can be null)
     * @param failOnMissing If true, throws IllegalStateException when tenant cannot be determined
     * @return The resolved tenant ID in lowercase
     * @throws IllegalStateException if failOnMissing is true and tenant ID cannot be determined
     */
    public String resolveTenantId(HttpServletRequest request, Authentication authentication, boolean failOnMissing) {
        // First, try to get from current tenant context
        String currentTenantId = TenantContext.getCurrentTenant();
        log.debug("1. From TenantContext: '{}'", currentTenantId);
        if (currentTenantId != null && !currentTenantId.isEmpty()) {
            return currentTenantId.toLowerCase();
        }
        
        // Try to get from session (stored during login)
        currentTenantId = (String) request.getSession().getAttribute("tenantId");
        log.debug("2. From session: '{}'", currentTenantId);
        if (currentTenantId != null && !currentTenantId.isEmpty()) {
            return currentTenantId.toLowerCase();
        }
        
        // Try to get from header - use the standard header name consistently
        currentTenantId = request.getHeader(TENANT_HEADER);
        log.debug("3. From header: '{}'", currentTenantId);
        if (currentTenantId != null && !currentTenantId.isEmpty()) {
            return currentTenantId.toLowerCase();
        }
        
        // Try to get from parameter
        currentTenantId = request.getParameter("tenant_id");
        log.debug("4. From parameter: '{}'", currentTenantId);
        if (currentTenantId != null && !currentTenantId.isEmpty()) {
            return currentTenantId.toLowerCase();
        }
        
        // Handle missing tenant ID based on failOnMissing flag
        if (failOnMissing) {
            log.error("❌ Could not determine tenant ID from any source, and failOnMissing=true");
            throw new IllegalStateException("Tenant ID is required but could not be determined");
        } else {
            // Default to public tenant only for non-sensitive operations
            log.warn("⚠️ Could not determine tenant ID from any source, using default 'public'");
            return "public";
        }
    }
    
    /**
     * Convenience method that defaults to not failing on missing tenant ID.
     * Suitable for public-facing or non-sensitive operations.
     */
    public String resolveTenantId(HttpServletRequest request, Authentication authentication) {
        return resolveTenantId(request, authentication, false);
    }
    
    /**
     * Gets the current tenant from the context.
     * 
     * @return The current tenant ID or null if not set
     */
    public String getCurrentTenant() {
        return TenantContext.getCurrentTenant();
    }
    
    /**
     * Sets the current tenant context based on the resolved tenant ID.
     * Useful for setting up tenant context for all requests in a controller.
     */
    public void setupTenantContext(HttpServletRequest request) {
        String currentTenantId = resolveTenantId(request, null, false);
        TenantContext.setCurrentTenant(currentTenantId);
        log.debug("Set tenant context to: {}", currentTenantId);
    }
    
    /**
     * Directly sets the current tenant in the context.
     * Useful for specific tenant operations.
     * 
     * @param tenantId The tenant ID to set
     */
    public void setCurrentTenant(String tenantId) {
        TenantContext.setCurrentTenant(tenantId);
        log.debug("Explicitly set tenant context to: {}", tenantId);
    }
    
    /**
     * Clears the current tenant context.
     * Should be called after operations to prevent tenant context leaking.
     */
    public void clearCurrentTenant() {
        TenantContext.clear();
        log.debug("Cleared tenant context");
    }
}
