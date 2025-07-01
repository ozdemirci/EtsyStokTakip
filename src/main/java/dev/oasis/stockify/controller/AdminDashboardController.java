package dev.oasis.stockify.controller;

import dev.oasis.stockify.config.tenant.TenantContext;
import dev.oasis.stockify.dto.DashboardMetricsDTO;
import dev.oasis.stockify.dto.StockMovementResponseDTO;
import dev.oasis.stockify.dto.TenantDTO;
import dev.oasis.stockify.dto.UserResponseDTO;
import dev.oasis.stockify.model.PlanType;
import dev.oasis.stockify.model.StockNotification;
import dev.oasis.stockify.service.DashboardService;
import dev.oasis.stockify.service.StockMovementService;
import dev.oasis.stockify.service.SubscriptionService;
import dev.oasis.stockify.service.TenantManagementService;
import dev.oasis.stockify.service.TenantConfigService;
import dev.oasis.stockify.service.AppUserService;
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
import org.springframework.web.bind.annotation.ResponseBody;

import jakarta.servlet.http.HttpServletRequest;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Controller
@RequestMapping("/admin/dashboard")
@PreAuthorize("hasAnyRole('ADMIN', 'SUPER_ADMIN')")
@RequiredArgsConstructor
@Slf4j
public class AdminDashboardController {    
    
    private final DashboardService dashboardService;
    private final TenantManagementService tenantManagementService;
    private final AppUserService appUserService;
    private final StockNotificationService stockNotificationService;
    private final SubscriptionService subscriptionService;
    private final TenantConfigService tenantConfigService;
    

    @GetMapping
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
        DashboardMetricsDTO metrics = dashboardService.getDashboardMetrics();        // Get all users in current tenant
        List<UserResponseDTO> tenantUsers = appUserService.getAllUsers();
        log.debug("Found {} users for tenant: {}", tenantUsers.size(), currentTenantId);
        
        // Get real notification data
        List<StockNotification> notifications = stockNotificationService.getAllNotifications();
        long totalNotifications = notifications.size();
        long unreadNotifications = notifications.stream().filter(n -> !n.isRead()).count();
        long criticalNotifications = notifications.stream()
            .filter(n -> "HIGH".equals(n.getPriority()) || "OUT_OF_STOCK".equals(n.getNotificationType()))
            .count();

        // Get stock movement data
        try {
            List<StockMovementResponseDTO> recentStockMovements = dashboardService.getRecentStockMovements();
            StockMovementService.StockMovementStats stockStats = dashboardService.getStockMovementStats();
            
            model.addAttribute("recentStockMovements", recentStockMovements);
            model.addAttribute("totalStockMovements", stockStats.getTotalMovements());
            model.addAttribute("stockInMovements", stockStats.getInMovements());
            model.addAttribute("stockOutMovements", stockStats.getOutMovements());
        } catch (Exception e) {
            log.warn("Could not get stock movement data: {}", e.getMessage());
            model.addAttribute("recentStockMovements", List.of());
            model.addAttribute("totalStockMovements", 0);
            model.addAttribute("stockInMovements", 0);
            model.addAttribute("stockOutMovements", 0);
        }
        
        // Add individual metrics for template
        model.addAttribute("totalProducts", metrics.getTotalProducts());
        model.addAttribute("activeProducts", metrics.getActiveProducts());
        model.addAttribute("lowStockProducts", metrics.getLowStockProducts());
        model.addAttribute("outOfStockProducts", 0L); // TODO: Add to DashboardMetricsDTO
        model.addAttribute("totalUsers", tenantUsers.size());
        model.addAttribute("adminUsers", tenantUsers.stream().mapToInt(u -> 
            u.getRole() != null && "ADMIN".equals(u.getRole().toString()) ? 1 : 0).sum());
        model.addAttribute("totalInventoryValue", String.format("%.2f", metrics.getTotalInventoryValue()));
        
        // Add notification data
        model.addAttribute("totalNotifications", totalNotifications);
        model.addAttribute("unreadNotifications", unreadNotifications);
        model.addAttribute("criticalNotifications", criticalNotifications);
        
        // Add subscription information from tenant_config
        try {
            String subscriptionPlanFromConfig = tenantConfigService.getSubscriptionPlan();
            String companyName = tenantConfigService.getCompanyName();
            String tenantStatus = tenantConfigService.getTenantStatus();
            
            model.addAttribute("subscriptionPlanFromConfig", subscriptionPlanFromConfig);
            model.addAttribute("tenantCompanyName", companyName);
            model.addAttribute("tenantStatus", tenantStatus);
            
            // Also get the enum-based plan for compatibility
            PlanType currentPlan = subscriptionService.getTenantPlan();
            model.addAttribute("subscriptionPlan", currentPlan);
            model.addAttribute("planDisplayName", getDisplayNameForPlan(subscriptionPlanFromConfig));
            model.addAttribute("planFeatures", getFeaturesForPlan(subscriptionPlanFromConfig));
            model.addAttribute("planPrice", getPriceForPlan(subscriptionPlanFromConfig));
            model.addAttribute("isTrialPlan", "TRIAL".equals(subscriptionPlanFromConfig));
            
            log.debug("📊 Subscription info - Plan: {}, Company: {}, Status: {}", 
                     subscriptionPlanFromConfig, companyName, tenantStatus);
            
        } catch (Exception e) {
            log.warn("Could not get subscription info from tenant_config: {}", e.getMessage());
            // Fallback to original subscription service
            PlanType currentPlan = subscriptionService.getTenantPlan();
            model.addAttribute("subscriptionPlan", currentPlan);
            model.addAttribute("subscriptionPlanFromConfig", "TRIAL");
            model.addAttribute("planDisplayName", currentPlan.getDisplayName());
            model.addAttribute("planFeatures", currentPlan.getFeaturesDescription());
            model.addAttribute("planPrice", currentPlan.getPriceDescription());
            model.addAttribute("isTrialPlan", currentPlan.isTrial());
        }
        
        // Add collections
        model.addAttribute("metrics", metrics);
        model.addAttribute("currentTenant", currentTenant);
        model.addAttribute("tenantUsers", tenantUsers);
        model.addAttribute("currentTenantId", currentTenantId);
        
        return "admin/dashboard";
    }

    @GetMapping("/metrics")
    @ResponseBody
    public DashboardMetricsDTO getMetrics() {
        return dashboardService.getDashboardMetrics();
    }
    
    /**
     * Get current subscription plan information from database
     */
    @GetMapping("/api/subscription-plan")
    @ResponseBody
    public Map<String, Object> getCurrentSubscriptionPlan() {
        try {
            // Get current tenant ID for debugging
            String currentTenantId = TenantContext.getCurrentTenant();
            log.info("🔍 API: Getting subscription plan for tenant: {}", currentTenantId);
            
            // Get fresh data from database
            String subscriptionPlanFromConfig = tenantConfigService.getSubscriptionPlan();
            String companyName = tenantConfigService.getCompanyName();
            String tenantStatus = tenantConfigService.getTenantStatus();
            
            // Log raw values from database
            log.info("📊 RAW DB VALUES - Plan: '{}', Company: '{}', Status: '{}'", 
                     subscriptionPlanFromConfig, companyName, tenantStatus);
            
            // Build response
            Map<String, Object> response = new HashMap<>();
            // Send normalized uppercase version for consistent frontend handling
            String normalizedPlan = subscriptionPlanFromConfig != null ? 
                                  subscriptionPlanFromConfig.trim().toUpperCase() : "TRIAL";
            response.put("subscriptionPlan", normalizedPlan);
            response.put("rawSubscriptionPlan", subscriptionPlanFromConfig); // Original value for debugging
            response.put("planDisplayName", getDisplayNameForPlan(subscriptionPlanFromConfig));
            response.put("planFeatures", getFeaturesForPlan(subscriptionPlanFromConfig));
            response.put("planPrice", getPriceForPlan(subscriptionPlanFromConfig));
            response.put("isTrialPlan", "TRIAL".equals(normalizedPlan));
            response.put("companyName", companyName);
            response.put("tenantStatus", tenantStatus);
            response.put("success", true);
            
            // Log the transformed response
            log.info("📤 API RESPONSE - Plan: '{}', DisplayName: '{}', Price: '{}'", 
                     response.get("subscriptionPlan"), 
                     response.get("planDisplayName"), 
                     response.get("planPrice"));
            
            return response;
            
        } catch (Exception e) {
            log.error("❌ Error fetching subscription plan info: {}", e.getMessage());
            Map<String, Object> errorResponse = new HashMap<>();
            errorResponse.put("success", false);
            errorResponse.put("error", "Failed to fetch subscription plan information");
            return errorResponse;
        }
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
    
    /**
     * Get display name for subscription plan string
     */
    private String getDisplayNameForPlan(String planString) {
        if (planString == null) return "Trial Plan";
        
        // Normalize to uppercase for consistent comparison
        String normalizedPlan = planString.trim().toUpperCase();
        
        switch (normalizedPlan) {
            case "TRIAL":
                return "Trial Plan";
            case "BASIC":
                return "Basic Plan";
            case "PREMIUM":
                return "Premium Plan";
            case "ENTERPRISE":
                return "Enterprise Plan";
            default:
                // Handle legacy lowercase values
                switch (planString.trim().toLowerCase()) {
                    case "trial":
                        return "Trial Plan";
                    case "basic":
                        return "Basic Plan";
                    case "premium":
                        return "Premium Plan";
                    case "enterprise":
                        return "Enterprise Plan";
                    default:
                        return "Trial Plan";
                }
        }
    }
    
    /**
     * Get features description for subscription plan string
     */
    private String getFeaturesForPlan(String planString) {
        if (planString == null) return "Basic features for trial period";
        
        // Normalize to uppercase for consistent comparison
        String normalizedPlan = planString.trim().toUpperCase();
        
        switch (normalizedPlan) {
            case "TRIAL":
                return "Up to 2 users, 100 products, 30-day trial";
            case "BASIC":
                return "Up to 5 users, 1,000 products, email support";
            case "PREMIUM":
                return "Up to 20 users, unlimited products, priority support";
            case "ENTERPRISE":
                return "Unlimited users, custom features, 24/7 support";
            default:
                // Handle legacy lowercase values
                switch (planString.trim().toLowerCase()) {
                    case "trial":
                        return "Up to 2 users, 100 products, 30-day trial";
                    case "basic":
                        return "Up to 5 users, 1,000 products, email support";
                    case "premium":
                        return "Up to 20 users, unlimited products, priority support";
                    case "enterprise":
                        return "Unlimited users, custom features, 24/7 support";
                    default:
                        return "Basic features for trial period";
                }
        }
    }
    
    /**
     * Get price description for subscription plan string
     */
    private String getPriceForPlan(String planString) {
        if (planString == null) return "Free";
        
        // Normalize to uppercase for consistent comparison
        String normalizedPlan = planString.trim().toUpperCase();
        
        switch (normalizedPlan) {
            case "TRIAL":
                return "Free";
            case "BASIC":
                return "$29/month";
            case "PREMIUM":
                return "$79/month";
            case "ENTERPRISE":
                return "$199/month";
            default:
                // Handle legacy lowercase values
                switch (planString.trim().toLowerCase()) {
                    case "trial":
                        return "Free";
                    case "basic":
                        return "$29/month";
                    case "premium":
                        return "$79/month";
                    case "enterprise":
                        return "$199/month";
                    default:
                        return "Free";
                }
        }
    }
}
