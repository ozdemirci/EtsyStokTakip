package dev.oasis.stockify.controller;

import dev.oasis.stockify.model.StockNotification;
import dev.oasis.stockify.service.StockNotificationService;
import dev.oasis.stockify.util.TenantResolutionUtil;
import lombok.RequiredArgsConstructor;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Controller
@RequestMapping("/notifications")
@RequiredArgsConstructor
public class StockNotificationController {

    private final StockNotificationService stockNotificationService;
    private final TenantResolutionUtil tenantResolutionUtil;
    
    @ModelAttribute
    public void setupTenantContext(HttpServletRequest request) {
        tenantResolutionUtil.setupTenantContext(request);
    }
   
    @GetMapping
    public String listNotifications(HttpServletRequest request, Authentication authentication, Model model) {
        // Tenant ID çözümleme - bu işlem şu anda hizmet katmanında ele alınabilir
        String tenantId = tenantResolutionUtil.resolveTenantId(request, authentication, true);
        
        List<StockNotification> notifications = stockNotificationService.getAllNotifications();
        model.addAttribute("notifications", notifications);
        model.addAttribute("currentTenantId", tenantId);
        return "notification-list";
    }

    @PostMapping("/{id}/read")
    @ResponseBody
    public void markAsRead(@PathVariable Long id, HttpServletRequest request, Authentication authentication) {
        // Tenant ID çözümleme - bu işlem şu anda hizmet katmanında ele alınabilir
        tenantResolutionUtil.resolveTenantId(request, authentication, true);
        
        stockNotificationService.markAsRead(id);
    }

    @ModelAttribute("unreadCount")
    public int unreadCount(HttpServletRequest request, Authentication authentication) {
        // Tenant ID çözümleme - bu işlem şu anda hizmet katmanında ele alınabilir
        tenantResolutionUtil.resolveTenantId(request, authentication, true);
        
        return stockNotificationService.getUnreadNotifications().size();
    }
}
