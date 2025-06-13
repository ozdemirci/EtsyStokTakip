package dev.oasis.stockify.config.tenant;

import org.slf4j.MDC;

public class TenantContext {
    
    private static final String TENANT_KEY = "tenantId";
    private static final ThreadLocal<String> currentTenant = new ThreadLocal<>();
    
    public static void setCurrentTenant(String tenant) {
        currentTenant.set(tenant);
        // MDC'ye de ekle ki loglamada görünsün
        MDC.put(TENANT_KEY, tenant);
    }
    
    public static String getCurrentTenant() {
        return currentTenant.get();
    }
    
    public static void clear() {
        currentTenant.remove();
        MDC.remove(TENANT_KEY);
    }
}