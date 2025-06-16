package dev.oasis.stockify.controller;

import dev.oasis.stockify.config.tenant.TenantContext;
import dev.oasis.stockify.dto.DashboardMetricsDTO;
import dev.oasis.stockify.dto.TenantDTO;
import dev.oasis.stockify.dto.UserResponseDTO;
import dev.oasis.stockify.service.DashboardService;
import dev.oasis.stockify.service.TenantManagementService;
import dev.oasis.stockify.service.AppUserService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;

import java.util.List;

@Controller
@RequestMapping("/admin/dashboard")
@PreAuthorize("hasRole('ADMIN')")
@RequiredArgsConstructor
public class AdminDashboardController {

    private final DashboardService dashboardService;
    private final TenantManagementService tenantManagementService;
    private final AppUserService appUserService;

    @GetMapping
    public String showDashboard(Model model) {
        // Get current tenant info
        String currentTenantId = TenantContext.getCurrentTenant();
        TenantDTO currentTenant = null;
        if (currentTenantId != null) {
            currentTenant = tenantManagementService.getTenant(currentTenantId);
        }
        
        // Get dashboard metrics
        DashboardMetricsDTO metrics = dashboardService.getDashboardMetrics();
        
        // Get all users in current tenant
        List<UserResponseDTO> tenantUsers = appUserService.getAllUsers();
        
        model.addAttribute("metrics", metrics);
        model.addAttribute("currentTenant", currentTenant);
        model.addAttribute("tenantUsers", tenantUsers);
        model.addAttribute("currentTenantId", currentTenantId);
        
        return "admin/dashboard";
    }
}
