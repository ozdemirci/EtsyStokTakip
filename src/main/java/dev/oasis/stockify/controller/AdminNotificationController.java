package dev.oasis.stockify.controller;

import dev.oasis.stockify.config.tenant.TenantContext;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;

import jakarta.servlet.http.HttpServletRequest;

@Controller
@RequestMapping("/admin/notifications")
@PreAuthorize("hasRole('ADMIN')")
@Slf4j
public class AdminNotificationController {

    @GetMapping
    public String notifications(HttpServletRequest request, Model model) {
        String tenantId = getCurrentTenantId(request);
        log.info("🔔 Admin accessing notifications for tenant: {}", tenantId);
        
        model.addAttribute("tenantId", tenantId);
        return "admin/notifications";
    }

    private String getCurrentTenantId(HttpServletRequest request) {
        String tenantId = TenantContext.getCurrentTenant();
        if (tenantId != null && !tenantId.trim().isEmpty()) {
            return tenantId.toLowerCase();
        }
        
        tenantId = (String) request.getSession().getAttribute("tenantId");
        if (tenantId != null && !tenantId.trim().isEmpty()) {
            return tenantId.toLowerCase();
        }
        
        return "public";
    }
}
