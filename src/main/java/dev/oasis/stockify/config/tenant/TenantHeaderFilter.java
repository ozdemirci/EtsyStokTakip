package dev.oasis.stockify.config.tenant;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.annotation.Order;
import org.springframework.lang.NonNull;
import org.springframework.security.web.util.matcher.AntPathRequestMatcher;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

@Component
@Order(1)
public class TenantHeaderFilter extends OncePerRequestFilter {

    private static final Logger logger = LoggerFactory.getLogger(TenantHeaderFilter.class);
    private static final String TENANT_HEADER = "X-TenantId";    private static final String TENANT_PARAM = "tenant_id";
    private final AntPathRequestMatcher loginRequestMatcher = new AntPathRequestMatcher("/login", "POST");    @Override
    protected void doFilterInternal(@NonNull HttpServletRequest request, @NonNull HttpServletResponse response,
                                  @NonNull FilterChain filterChain) throws ServletException, IOException {        try {
            String tenantId = null;
            
            // 1. Try to get tenant from header first
            tenantId = request.getHeader(TENANT_HEADER);
            logger.debug("Tenant from header: {}", tenantId);

            // 2. If not in header and this is a login request, get from form parameter
            if ((tenantId == null || tenantId.isEmpty()) && loginRequestMatcher.matches(request)) {
                tenantId = request.getParameter(TENANT_PARAM);
                logger.debug("Login request - tenant from form parameter: {}", tenantId);
            }
            
            // 3. If still not found, try to get from session (login sets this)
            if (tenantId == null || tenantId.isEmpty()) {
                tenantId = (String) request.getSession().getAttribute("tenantId");
                logger.debug("Tenant from session: {}", tenantId);
            }            // 4. If still not found, check if this is a public/system endpoint
            if (tenantId == null || tenantId.isEmpty()) {
                // Public endpoints that don't need tenant context
                if (request.getRequestURI().equals("/login") || 
                    request.getRequestURI().equals("/") || 
                    request.getRequestURI().startsWith("/css/") ||
                    request.getRequestURI().startsWith("/js/") ||
                    request.getRequestURI().startsWith("/images/") ||
                    request.getRequestURI().startsWith("/actuator/") ||
                    request.getRequestURI().equals("/favicon.ico") ||
                    request.getRequestURI().equals("/register") ||
                    request.getRequestURI().startsWith("/register/check-") ||
                    request.getRequestURI().equals("/error")) {
                    tenantId = "public"; // Use public tenant for system endpoints
                    logger.debug("Public/system resource - using public tenant for: {}", request.getRequestURI());
                } else {
                    logger.error("❌ CRITICAL: No tenant found for protected resource: {}", request.getRequestURI());
                    // Don't set any tenant context - let controllers handle the error
                    filterChain.doFilter(request, response);
                    return;
                }
            }

            // Set tenant context
            tenantId = tenantId.toLowerCase();
            TenantContext.setCurrentTenant(tenantId);
            logger.debug("Set tenant context to: {}", tenantId);
            
            // Add tenant to response header for debugging
            response.setHeader("X-Current-Tenant", tenantId);

            filterChain.doFilter(request, response);
        } finally {
            // Context'i her zaman temizle, ancak authentication sonrası biraz daha fazla sakla
            // TenantContext.clear();
            // Note: Clearing context commented out because it can cause issues after login
            // when redirecting to dashboard. Let each controller handle their own context.
        }
    }    @Override
    protected boolean shouldNotFilter(@NonNull HttpServletRequest request) {
        String path = request.getRequestURI();
        return path.startsWith("/css/") ||
               path.startsWith("/js/") ||
               path.startsWith("/images/") ||
               path.startsWith("/h2-console/") ||
               path.startsWith("/actuator/") ||
               path.equals("/favicon.ico") ||
               path.equals("/error");
    }
}
