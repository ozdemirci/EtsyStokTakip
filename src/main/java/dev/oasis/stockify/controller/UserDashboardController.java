package dev.oasis.stockify.controller;

import dev.oasis.stockify.dto.DashboardMetricsDTO;
import dev.oasis.stockify.dto.StockMovementResponseDTO;
import dev.oasis.stockify.dto.TenantDTO;
import dev.oasis.stockify.model.StockNotification;
import dev.oasis.stockify.service.DashboardService;
import dev.oasis.stockify.service.StockMovementService;
import dev.oasis.stockify.service.TenantManagementService;
import dev.oasis.stockify.service.StockNotificationService;
import dev.oasis.stockify.util.TenantResolutionUtil;
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
    private final TenantResolutionUtil tenantResolutionUtil;

    @ModelAttribute
    public void setupTenantContext(HttpServletRequest request) {
        tenantResolutionUtil.setupTenantContext(request);
    }

    @GetMapping
    public String showDashboard(Model model, HttpServletRequest request, Authentication authentication) {
        // Get current tenant info
        String currentTenantId = tenantResolutionUtil.resolveTenantId(request, authentication, true);
        log.debug("User Dashboard - Current tenant ID: {}", currentTenantId);

        TenantDTO currentTenant = null;
        try {
            currentTenant = tenantManagementService.getTenant(currentTenantId);
        } catch (Exception e) {
            log.warn("Could not get tenant info for: {}, error: {}", currentTenantId, e.getMessage());
        }

        // Get dashboard metrics
        DashboardMetricsDTO metrics = dashboardService.getDashboardMetrics();

        // Get notification data
        List<StockNotification> notifications = stockNotificationService.getAllNotifications();
        long unreadNotifications = notifications.stream().filter(n -> !n.isRead()).count();
        long totalNotifications = notifications.size();
        long criticalNotifications = notifications.stream()
            .filter(n -> "HIGH".equals(n.getPriority()) || "OUT_OF_STOCK".equals(n.getNotificationType()))
            .count();

        // Get stock movement data for user dashboard
        try {
            List<StockMovementResponseDTO> recentStockMovements = dashboardService.getRecentStockMovements();
            if (recentStockMovements == null) {
                recentStockMovements = List.of();
            }
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
        model.addAttribute("activeProducts", metrics.getActiveProducts());
        model.addAttribute("lowStockProducts", metrics.getLowStockProducts());
        model.addAttribute("outOfStockProducts", metrics.getOutOfStockProducts()); 
        model.addAttribute("totalInventoryValue", String.format("%.2f", metrics.getTotalInventoryValue()));
        model.addAttribute("unreadNotifications", unreadNotifications);

        // Add collections
        model.addAttribute("metrics", metrics);
        model.addAttribute("currentTenant", currentTenant);
        model.addAttribute("currentTenantId", currentTenantId);
        model.addAttribute("currentUser", authentication.getName());

        // Add notification data
        model.addAttribute("totalNotifications", totalNotifications);
        model.addAttribute("unreadNotifications", unreadNotifications);
        model.addAttribute("criticalNotifications", criticalNotifications);
        
        return "user/dashboard";
    }
}
