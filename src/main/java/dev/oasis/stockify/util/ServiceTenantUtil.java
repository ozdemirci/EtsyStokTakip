package dev.oasis.stockify.util;

import dev.oasis.stockify.config.tenant.TenantContext;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * Utility class for centralized tenant operations in service layer.
 * Provides methods to consistently manage tenant context in services.
 */
@Component
@Slf4j
public class ServiceTenantUtil {
    
    /**
     * Gets the current tenant ID from TenantContext.
     * 
     * @return Current tenant ID or null if not set
     */
    public String getCurrentTenant() {
        return TenantContext.getCurrentTenant();
    }
    
    /**
     * Gets the current tenant ID from TenantContext with fallback to default.
     * 
     * @param defaultTenant Default tenant ID to return if current is not set
     * @param failOnMissing If true, throws exception when tenant is not set
     * @return Current tenant ID or defaultTenant if not set (unless failOnMissing is true)
     * @throws IllegalStateException if failOnMissing is true and tenant ID is not set
     */
    public String getCurrentTenant(String defaultTenant, boolean failOnMissing) {
        String currentTenant = TenantContext.getCurrentTenant();
        
        if (currentTenant != null && !currentTenant.isEmpty()) {
            return currentTenant;
        }
        
        if (failOnMissing) {
            log.error("❌ Could not determine tenant ID, and failOnMissing=true");
            throw new IllegalStateException("Tenant ID is required but could not be determined");
        }
        
        log.warn("⚠️ Could not determine tenant ID, using default '{}'", defaultTenant);
        return defaultTenant;
    }
    
    /**
     * Sets the current tenant in the context.
     * 
     * @param tenantId The tenant ID to set
     */
    public void setCurrentTenant(String tenantId) {
        TenantContext.setCurrentTenant(tenantId);
        log.debug("Set tenant context to: {}", tenantId);
    }
    
    /**
     * Clears the current tenant context.
     * Should be called after operations to prevent tenant context leaking.
     */
    public void clearCurrentTenant() {
        TenantContext.clear();
        log.debug("Cleared tenant context");
    }
    
    /**
     * Executes an operation in the context of a specific tenant,
     * then restores the previous tenant context (or clears it if there was none).
     * 
     * @param tenantId Tenant ID to use during execution
     * @param operation Operation to execute in tenant context
     * @param <T> Return type of the operation
     * @return Result of the operation
     */
    public <T> T executeInTenant(String tenantId, TenantOperation<T> operation) {
        // Save current tenant to restore later
        String previousTenant = TenantContext.getCurrentTenant();
        
        try {
            // Set the specified tenant
            TenantContext.setCurrentTenant(tenantId);
            log.debug("Temporarily switched tenant context to: {}", tenantId);
            
            // Execute the operation
            return operation.execute();
            
        } finally {
            // Restore previous tenant or clear if there was none
            if (previousTenant != null) {
                TenantContext.setCurrentTenant(previousTenant);
                log.debug("Restored tenant context to: {}", previousTenant);
            } else {
                TenantContext.clear();
                log.debug("Cleared tenant context after operation");
            }
        }
    }
    
    /**
     * Functional interface for operations to execute within a tenant context.
     */
    @FunctionalInterface
    public interface TenantOperation<T> {
        T execute();
    }
}
