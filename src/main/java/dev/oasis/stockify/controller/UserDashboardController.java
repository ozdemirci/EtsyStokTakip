package dev.oasis.stockify.controller;

import dev.oasis.stockify.config.tenant.TenantContext;
import dev.oasis.stockify.dto.DashboardMetricsDTO;
import dev.oasis.stockify.dto.StockMovementResponseDTO;
import dev.oasis.stockify.dto.TenantDTO;
import dev.oasis.stockify.model.StockNotification;
import dev.oasis.stockify.service.DashboardService;
import dev.oasis.stockify.service.StockMovementService;
import dev.oasis.stockify.service.TenantManagementService;
import dev.oasis.stockify.service.StockNotificationService;
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
@RequestMapping("/user/dashboard")
@PreAuthorize("hasRole('USER')")
@RequiredArgsConstructor
@Slf4j
public class UserDashboardController {

    private final DashboardService dashboardService;
    private final TenantManagementService tenantManagementService;
    private final StockNotificationService stockNotificationService;

    @GetMapping
    public String showDashboard(Model model, HttpServletRequest request, Authentication authentication) {
        // Get current tenant info
        String currentTenantId = getCurrentTenantId(request);
        
        log.debug("User Dashboard - Current tenant ID: {}", currentTenantId);
        
        TenantDTO currentTenant = null;
        try {
            currentTenant = tenantManagementService.getTenant(currentTenantId);

        } catch (Exception e) {
            log.warn("Could not get tenant info for: {}, error: {}", currentTenantId, e.getMessage());
        }        // Get dashboard metrics
        DashboardMetricsDTO metrics = dashboardService.getDashboardMetrics();
        
        // Get notification data
        List<StockNotification> notifications = stockNotificationService.getAllNotifications();
        long unreadNotifications = notifications.stream().filter(n -> !n.isRead()).count();
        
        // Get stock movement data for user dashboard
        try {
            List<StockMovementResponseDTO> recentStockMovements = dashboardService.getRecentStockMovements();
            StockMovementService.StockMovementStats stockStats = dashboardService.getStockMovementStats();
            
            model.addAttribute("recentStockMovements", recentStockMovements);
            model.addAttribute("totalStockMovements", stockStats.getTotalMovements());
            model.addAttribute("stockInMovements", stockStats.getInMovements());
            model.addAttribute("stockOutMovements", stockStats.getOutMovements());
        } catch (Exception e) {
            log.warn("Could not get stock movement data for user dashboard: {}", e.getMessage());
            model.addAttribute("recentStockMovements", List.of());
            model.addAttribute("totalStockMovements", 0);
            model.addAttribute("stockInMovements", 0);
            model.addAttribute("stockOutMovements", 0);
        }
        
        log.debug("Found metrics for tenant: {} - Products: {}, Stock Value: {}", 
                currentTenantId, metrics.getTotalProducts(), metrics.getTotalInventoryValue());

        // Add individual metrics for template
        model.addAttribute("totalProducts", metrics.getTotalProducts());
        model.addAttribute("activeProducts", Math.max(0, metrics.getTotalProducts() - metrics.getLowStockProducts()));
        model.addAttribute("lowStockProducts", metrics.getLowStockProducts());
        model.addAttribute("outOfStockProducts", 0L); // TODO: Add to DashboardMetricsDTO
        model.addAttribute("totalInventoryValue", String.format("%.2f", metrics.getTotalInventoryValue()));
        model.addAttribute("unreadNotifications", unreadNotifications);
        
        // Add collections
        model.addAttribute("metrics", metrics);
        model.addAttribute("currentTenant", currentTenant);
        model.addAttribute("currentTenantId", currentTenantId);
        model.addAttribute("currentUser", authentication.getName());
        
        return "user/dashboard";
    }
    
    /**
     * Ensure tenant context is set for all requests in this controller
     */
    @ModelAttribute
    public void setupTenantContext(HttpServletRequest request) {
        String currentTenantId = getCurrentTenantId(request);
        TenantContext.setCurrentTenant(currentTenantId);
        log.debug("Set tenant context to: {}", currentTenantId);
    }

    private String getCurrentTenantId(HttpServletRequest request) {
        // First, try to get from current tenant context
        String currentTenantId = TenantContext.getCurrentTenant();
        log.debug("1. From context: '{}'", currentTenantId);
        if (currentTenantId != null && !currentTenantId.isEmpty()) {
            return currentTenantId.toLowerCase();
        }
        
        // Try to get from session (stored during login)
        currentTenantId = (String) request.getSession().getAttribute("tenantId");
        log.debug("2. From session: '{}'", currentTenantId);
        if (currentTenantId != null && !currentTenantId.isEmpty()) {
            return currentTenantId.toLowerCase();
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
