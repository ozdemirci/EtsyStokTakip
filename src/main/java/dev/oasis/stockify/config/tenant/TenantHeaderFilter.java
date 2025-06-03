package dev.oasis.stockify.config.tenant;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.annotation.Order;
import org.springframework.security.web.util.matcher.AntPathRequestMatcher;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

@Component
@Order(1)
public class TenantHeaderFilter extends OncePerRequestFilter {

    private static final Logger logger = LoggerFactory.getLogger(TenantHeaderFilter.class);
    private static final String TENANT_HEADER = "X-TenantId";
    private static final String TENANT_PARAM = "tenant_id";
    private static final String DEFAULT_TENANT = "public";
    private final AntPathRequestMatcher loginRequestMatcher = new AntPathRequestMatcher("/login", "POST");

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                  FilterChain filterChain) throws ServletException, IOException {
        try {
            // Tenant ID'yi header'dan al
            String tenantId = request.getHeader(TENANT_HEADER);

            // Eğer header'da yoksa, form parametresinden al (login işlemi için)
            if ((tenantId == null || tenantId.isEmpty()) && loginRequestMatcher.matches(request)) {
                tenantId = request.getParameter(TENANT_PARAM);
                logger.debug("Login request detected, using tenant_id from form parameter: {}", tenantId);
            }

            // Tenant ID boşsa veya null ise varsayılan tenant'ı kullan
            if (tenantId == null || tenantId.isEmpty()) {
                tenantId = DEFAULT_TENANT;
            }

            // Tenant ID'yi küçük harfe çevir
            tenantId = tenantId.toLowerCase();

            // TenantContext'e tenant bilgisini set et
            TenantContext.setCurrentTenant(tenantId);
            logger.debug("Set tenant context to: {}", tenantId);

            filterChain.doFilter(request, response);
        } finally {
            // İşlem bittikten sonra TenantContext'i temizle
            // Login işlemi için TenantContext'i temizleme, çünkü AppUserDetailsService'in kullanması gerekiyor
            if (!loginRequestMatcher.matches(request)) {
                TenantContext.clear();
                logger.debug("Cleared tenant context after non-login request");
            }
        }
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getRequestURI();
        return path.startsWith("/css/") ||
               path.startsWith("/js/") ||
               path.startsWith("/images/") ||
               path.startsWith("/h2-console/") ||
               path.equals("/error");
    }
}
