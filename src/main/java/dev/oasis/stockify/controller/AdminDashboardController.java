package dev.oasis.stockify.controller;

import dev.oasis.stockify.config.tenant.TenantContext;
import dev.oasis.stockify.dto.DashboardMetricsDTO;
import dev.oasis.stockify.dto.TenantDTO;
import dev.oasis.stockify.dto.UserResponseDTO;
import dev.oasis.stockify.service.DashboardService;
import dev.oasis.stockify.service.TenantManagementService;
import dev.oasis.stockify.service.AppUserService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.RequestMapping;

import jakarta.servlet.http.HttpServletRequest;
import java.util.List;

@Controller
@RequestMapping("/admin/dashboard")
@PreAuthorize("hasRole('ADMIN')")
@RequiredArgsConstructor
@Slf4j
public class AdminDashboardController {

    private final DashboardService dashboardService;
    private final TenantManagementService tenantManagementService;
    private final AppUserService appUserService;    @GetMapping

    public String showDashboard(Model model, HttpServletRequest request, Authentication authentication) {
        // Get current tenant info
        String currentTenantId = getCurrentTenantId(request, authentication);
        
        log.debug("Dashboard - Current tenant ID: {}", currentTenantId);
        
        TenantDTO currentTenant = null;
        try {
            currentTenant = tenantManagementService.getTenant(currentTenantId);
        } catch (Exception e) {
            log.warn("Could not get tenant info for: {}, error: {}", currentTenantId, e.getMessage());
        }
          // Get dashboard metrics
        DashboardMetricsDTO metrics = dashboardService.getDashboardMetrics();
        
        // Get all users in current tenant
        List<UserResponseDTO> tenantUsers = appUserService.getAllUsers();
        log.debug("Found {} users for tenant: {}", tenantUsers.size(), currentTenantId);
          // Add individual metrics for template
        model.addAttribute("totalProducts", metrics.getTotalProducts());
        model.addAttribute("activeProducts", Math.max(0, metrics.getTotalProducts() - metrics.getLowStockProducts()));
        model.addAttribute("lowStockProducts", metrics.getLowStockProducts());
        model.addAttribute("outOfStockProducts", 0L); // TODO: Add to DashboardMetricsDTO
        model.addAttribute("totalUsers", tenantUsers.size());
        model.addAttribute("adminUsers", tenantUsers.stream().mapToInt(u -> 
            u.getRole() != null && "ADMIN".equals(u.getRole().toString()) ? 1 : 0).sum());
        model.addAttribute("totalInventoryValue", String.format("%.2f", metrics.getTotalInventoryValue()));
        
        // Add collections
        model.addAttribute("metrics", metrics);
        model.addAttribute("currentTenant", currentTenant);
        model.addAttribute("tenantUsers", tenantUsers);
        model.addAttribute("currentTenantId", currentTenantId);
        
        return "admin/dashboard";
    }
    
    /**
     * Ensure tenant context is set for all requests in this controller
     */
    @ModelAttribute
    public void setupTenantContext(HttpServletRequest request) {
        String currentTenantId = getCurrentTenantId(request, null);
        TenantContext.setCurrentTenant(currentTenantId);
        log.debug("Set tenant context to: {}", currentTenantId);
    }
      private String getCurrentTenantId(HttpServletRequest request, Authentication authentication) {
        // First, try to get from current tenant context
        String currentTenantId = TenantContext.getCurrentTenant();
        log.debug("1. From TenantContext: '{}'", currentTenantId);
        if (currentTenantId != null && !currentTenantId.isEmpty()) {
            return currentTenantId;
        }
        
        // Try to get from session (stored during login)
        currentTenantId = (String) request.getSession().getAttribute("tenantId");
        log.debug("2. From session: '{}'", currentTenantId);
        if (currentTenantId != null && !currentTenantId.isEmpty()) {
            return currentTenantId;
        }
        
        // Try to get from header
        currentTenantId = request.getHeader("X-TenantId");
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
        
        // Default to public tenant for testing
        log.warn("⚠️ Could not determine tenant ID from any source, using default 'public'");
        return "public";
    }
}
