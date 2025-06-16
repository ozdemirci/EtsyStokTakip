package dev.oasis.stockify.config.tenant;

import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.core.Authentication;
import org.springframework.security.web.authentication.SimpleUrlAuthenticationSuccessHandler;
import org.springframework.stereotype.Component;

import java.io.IOException;

@Component
public class TenantAwareAuthenticationSuccessHandler extends SimpleUrlAuthenticationSuccessHandler {
    
    private static final Logger logger = LoggerFactory.getLogger(TenantAwareAuthenticationSuccessHandler.class);    @Override
    public void onAuthenticationSuccess(HttpServletRequest request,
                                      HttpServletResponse response,
                                      Authentication authentication) throws IOException, ServletException {
        String tenantId = request.getParameter("tenant_id");
        logger.debug("Login attempt - received tenant_id parameter: '{}'", tenantId);
        
        if (tenantId != null && !tenantId.trim().isEmpty()) {
            tenantId = tenantId.toLowerCase().trim();
            TenantContext.setCurrentTenant(tenantId);
            // Session'a da ekle ki sonraki request'lerde kullanabilelim
            request.getSession().setAttribute("tenantId", tenantId);
            logger.info("✅ Successfully set tenant ID in context and session: {}", tenantId);
        } else {
            // Default tenant for fallback
            String defaultTenant = "public";
            TenantContext.setCurrentTenant(defaultTenant);
            request.getSession().setAttribute("tenantId", defaultTenant);
            logger.warn("⚠️ No tenant_id provided, using default tenant: {}", defaultTenant);
        }

        // Super Admin için özel dashboard
        if (authentication.getAuthorities().stream()
                .anyMatch(a -> a.getAuthority().equals("ROLE_SUPER_ADMIN"))) {
            logger.info("🔑 Super Admin login - redirecting to /superadmin/dashboard");
            getRedirectStrategy().sendRedirect(request, response, "/superadmin/dashboard");
        }
        // Yönetici kullanıcıları için dashboard'a yönlendir
        else if (authentication.getAuthorities().stream()
                .anyMatch(a -> a.getAuthority().equals("ROLE_ADMIN"))) {
            logger.info("👨‍💼 Admin login for tenant: {} - redirecting to /admin/dashboard", tenantId);
            getRedirectStrategy().sendRedirect(request, response, "/admin/dashboard");
        } else {
            // Normal kullanıcılar için ürün listesine yönlendir
            logger.info("👤 User login for tenant: {} - redirecting to /products", tenantId);
            getRedirectStrategy().sendRedirect(request, response, "/products");
        }
    }
}
