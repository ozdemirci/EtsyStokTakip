package dev.oasis.stockify.config.tenant;

import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.core.Authentication;
import org.springframework.security.web.authentication.SimpleUrlAuthenticationSuccessHandler;
import org.springframework.stereotype.Component;

import java.io.IOException;

@Component
public class TenantAwareAuthenticationSuccessHandler extends SimpleUrlAuthenticationSuccessHandler {    @Override
    public void onAuthenticationSuccess(HttpServletRequest request,
                                      HttpServletResponse response,
                                      Authentication authentication) throws IOException, ServletException {
        String tenantId = request.getParameter("tenant_id");
        if (tenantId != null && !tenantId.isEmpty()) {
            TenantContext.setCurrentTenant(tenantId.toLowerCase());
        }

        // Super Admin için özel dashboard
        if (authentication.getAuthorities().stream()
                .anyMatch(a -> a.getAuthority().equals("ROLE_SUPER_ADMIN"))) {
            getRedirectStrategy().sendRedirect(request, response, "/superadmin/dashboard");
        }
        // Yönetici kullanıcıları için dashboard'a yönlendir
        else if (authentication.getAuthorities().stream()
                .anyMatch(a -> a.getAuthority().equals("ROLE_ADMIN"))) {
            getRedirectStrategy().sendRedirect(request, response, "/admin/dashboard");
        } else {
            // Normal kullanıcılar için ürün listesine yönlendir
            getRedirectStrategy().sendRedirect(request, response, "/products");
        }
    }
}
