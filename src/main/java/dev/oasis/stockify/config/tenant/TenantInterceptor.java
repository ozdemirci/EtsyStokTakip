package dev.oasis.stockify.config.tenant;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

@Component
public class TenantInterceptor implements HandlerInterceptor {

    private static final Logger log = LoggerFactory.getLogger(TenantInterceptor.class);

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        String tenantId = extractTenantId(request);
        
        if (tenantId != null) {
            TenantContext.setCurrentTenant(tenantId);
            log.debug("🏢 Tenant set to: {}", tenantId);
        } else {
            // Default tenant
            TenantContext.setCurrentTenant("public");
            log.debug("🏢 Using default tenant: public");
        }
        
        return true;
    }

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response, Object handler, Exception ex) {
        TenantContext.clear();
    }

    private String extractTenantId(HttpServletRequest request) {
        // 1. Header'dan tenant ID'yi al
        String tenantId = request.getHeader("X-Tenant-ID");
        if (tenantId != null && !tenantId.trim().isEmpty()) {
            return tenantId.trim().toLowerCase();
        }

        // 2. Subdomain'den tenant ID'yi al (örn: tenant1.yourapp.com)
        String serverName = request.getServerName();
        if (serverName != null && serverName.contains(".")) {
            String[] parts = serverName.split("\\.");
            if (parts.length > 2) { // tenant.yourapp.com formatında
                tenantId = parts[0];
                if (!tenantId.equals("www") && !tenantId.equals("api")) {
                    return tenantId.toLowerCase();
                }
            }
        }

        // 3. URL path'den tenant ID'yi al (/tenant/{tenantId}/...)
        String path = request.getRequestURI();
        if (path.startsWith("/tenant/")) {
            String[] pathParts = path.split("/");
            if (pathParts.length > 2) {
                return pathParts[2].toLowerCase();
            }
        }

        // 4. Query parameter'dan al
        tenantId = request.getParameter("tenantId");
        if (tenantId != null && !tenantId.trim().isEmpty()) {
            return tenantId.trim().toLowerCase();
        }

        return null;
    }
}