package dev.oasis.stockify.controller;

import dev.oasis.stockify.model.StockNotification;
import dev.oasis.stockify.service.StockNotificationService;
import dev.oasis.stockify.util.TenantResolutionUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseBody;

import jakarta.servlet.http.HttpServletRequest;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Controller
@RequestMapping("/admin/notifications")
@PreAuthorize("hasAnyRole('ADMIN', 'SUPER_ADMIN')")
@Slf4j
public class AdminNotificationController {
    
    private final StockNotificationService stockNotificationService;
    private final TenantResolutionUtil tenantResolutionUtil;

    public AdminNotificationController(StockNotificationService stockNotificationService, TenantResolutionUtil tenantResolutionUtil) {
        this.stockNotificationService = stockNotificationService;
        this.tenantResolutionUtil = tenantResolutionUtil;
    }
    
    @ModelAttribute
    public void setupTenant(HttpServletRequest request) {
        tenantResolutionUtil.setupTenantContext(request);
    }
    
    @GetMapping
    public String notifications(HttpServletRequest request, Authentication authentication, Model model) {
        String tenantId = tenantResolutionUtil.resolveTenantId(request, authentication, true);
        log.info("🔔 Admin accessing notifications for tenant: {}", tenantId);
        
        List<StockNotification> notifications = stockNotificationService.getAllNotifications();
        long totalNotifications = notifications.size();
        long unreadNotifications = notifications.stream().filter(n -> !n.isRead()).count();
        long criticalAlerts = notifications.stream()
            .filter(n -> "HIGH".equals(n.getPriority()) || "OUT_OF_STOCK".equals(n.getNotificationType()))
            .count();
        
        // Debug logging
        log.info("📊 Notification Debug - Tenant: {}", tenantId);
        log.info("📊 Total notifications found: {}", totalNotifications);
        log.info("📊 Unread notifications: {}", unreadNotifications);
        log.info("📊 Critical alerts: {}", criticalAlerts);
        
        notifications.forEach(notification -> {
            log.debug("🔔 Notification ID: {}, Type: {}, Product: {}, Read: {}, Message: {}", 
                notification.getId(), notification.getNotificationType(), 
                notification.getProduct() != null ? notification.getProduct().getTitle() : "N/A",
                notification.isRead(), notification.getMessage());
        });
        
        model.addAttribute("notifications", notifications);
        model.addAttribute("totalNotifications", totalNotifications);
        model.addAttribute("unreadNotifications", unreadNotifications);
        model.addAttribute("criticalAlerts", criticalAlerts);
        model.addAttribute("tenantId", tenantId);
        model.addAttribute("currentTenantId", tenantId);
        
        log.info("📊 Notification stats for tenant {}: Total={}, Unread={}, Critical={}", 
            tenantId, totalNotifications, unreadNotifications, criticalAlerts);
        
        return "admin/notifications";
    }

    @PostMapping("/mark-all-read")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> markAllAsRead(HttpServletRequest request, Authentication authentication) {
        log.info("📧 markAllAsRead endpoint called by admin");
        try {
            // Ensure we have a valid tenant before proceeding
            String tenantId = tenantResolutionUtil.resolveTenantId(request, authentication, true);
            log.debug("Using tenant ID: {} for mark-all-read operation", tenantId);
            
            int count = stockNotificationService.markAllAsRead();
            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("count", count);
            response.put("message", count + " notifications marked as read");
            log.info("✅ Admin marked {} notifications as read successfully", count);
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            log.error("❌ Error marking all notifications as read", e);
            Map<String, Object> response = new HashMap<>();
            response.put("success", false);
            response.put("message", "Failed to mark notifications as read: " + e.getMessage());
            return ResponseEntity.internalServerError().body(response);
        }
    }

    @PostMapping("/delete-all-read")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> deleteAllRead(HttpServletRequest request, Authentication authentication) {
        log.info("🗑️ deleteAllRead endpoint called by admin");
        try {
            // Ensure we have a valid tenant before proceeding
            String tenantId = tenantResolutionUtil.resolveTenantId(request, authentication, true);
            log.debug("Using tenant ID: {} for delete-all-read operation", tenantId);
            
            int count = stockNotificationService.deleteAllRead();
            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("count", count);
            response.put("message", count + " read notifications deleted");
            log.info("✅ Admin deleted {} read notifications successfully", count);
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            log.error("❌ Error deleting read notifications", e);
            Map<String, Object> response = new HashMap<>();
            response.put("success", false);
            response.put("message", "Failed to delete read notifications: " + e.getMessage());
            return ResponseEntity.internalServerError().body(response);
        }
    }

    @PostMapping("/{id}/delete")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> deleteNotification(@PathVariable Long id, HttpServletRequest request, Authentication authentication) {
        try {
            // Ensure we have a valid tenant before proceeding
            String tenantId = tenantResolutionUtil.resolveTenantId(request, authentication, true);
            log.debug("Using tenant ID: {} for deleting notification ID: {}", tenantId, id);
            
            boolean deleted = stockNotificationService.deleteNotification(id);
            Map<String, Object> response = new HashMap<>();
            response.put("success", deleted);
            response.put("message", deleted ? "Notification deleted successfully" : "Failed to delete notification");
            log.info("Admin deleted notification with ID: {}", id);
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            log.error("Error deleting notification with ID: {}", id, e);
            Map<String, Object> response = new HashMap<>();
            response.put("success", false);
            response.put("message", "Failed to delete notification");
            return ResponseEntity.internalServerError().body(response);
        }
    }

    @GetMapping("/test")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> testEndpoint(HttpServletRequest request, Authentication authentication) {
        // Resolve tenant, but don't fail if missing since this is just a test endpoint
        String tenantId = tenantResolutionUtil.resolveTenantId(request, authentication, false);
        log.info("Admin notification test endpoint called for tenant: {}", tenantId);
        
        Map<String, Object> response = new HashMap<>();
        response.put("success", true);
        response.put("message", "Admin notification endpoint is working!");
        response.put("timestamp", java.time.LocalDateTime.now().toString());
        response.put("tenant", tenantId);
        return ResponseEntity.ok(response);
    }

    @PostMapping("/create-test-data")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> createTestData(HttpServletRequest request, Authentication authentication) {
        try {
            // Resolve tenant ID for test data creation
            String tenantId = tenantResolutionUtil.resolveTenantId(request, authentication, true);
            log.info("🧪 Creating test notification data for tenant: {}", tenantId);
            
            // Bu endpoint test verisi oluşturmak için - gerçek projede kaldırılabilir
            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("message", "Test data creation feature not implemented - please add notifications through normal product operations");
            response.put("note", "Low stock notifications are automatically created when products go below threshold");
            
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            log.error("Error creating test data", e);
            Map<String, Object> response = new HashMap<>();
            response.put("success", false);
            response.put("message", "Failed to create test data: " + e.getMessage());
            return ResponseEntity.internalServerError().body(response);
        }
    }

    @GetMapping("/debug")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> debugNotifications(HttpServletRequest request, Authentication authentication) {
        String tenantId = tenantResolutionUtil.resolveTenantId(request, authentication, true);
        log.info("🐛 Debug notifications for tenant: {}", tenantId);
        
        Map<String, Object> debugInfo = new HashMap<>();
        debugInfo.put("currentTenant", tenantId);
        
        try {
            List<StockNotification> notifications = stockNotificationService.getAllNotifications();
            debugInfo.put("notificationCount", notifications.size());
            debugInfo.put("notifications", notifications.stream().map(n -> {
                Map<String, Object> notifInfo = new HashMap<>();
                notifInfo.put("id", n.getId());
                notifInfo.put("type", n.getNotificationType());
                notifInfo.put("message", n.getMessage());
                notifInfo.put("read", n.isRead());
                notifInfo.put("priority", n.getPriority());
                notifInfo.put("category", n.getCategory());
                notifInfo.put("productTitle", n.getProduct() != null ? n.getProduct().getTitle() : "N/A");
                notifInfo.put("createdAt", n.getCreatedAt());
                return notifInfo;
            }).toList());
            
            debugInfo.put("success", true);
            log.info("🐛 Found {} notifications for tenant: {}", notifications.size(), tenantId);
            
        } catch (Exception e) {
            debugInfo.put("success", false);
            debugInfo.put("error", e.getMessage());
            log.error("🐛 Error getting notifications for tenant {}: {}", tenantId, e.getMessage(), e);
        }
        
        return ResponseEntity.ok(debugInfo);
    }

    @PostMapping("/delete-all-debug")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> deleteAllNotificationsDebug(HttpServletRequest request, Authentication authentication) {
        String tenantId = tenantResolutionUtil.resolveTenantId(request, authentication, true);
        log.info("🗑️ Debug: Deleting ALL notifications for tenant: {}", tenantId);
        
        Map<String, Object> response = new HashMap<>();
        
        try {
            List<StockNotification> allNotifications = stockNotificationService.getAllNotifications();
            int deletedCount = allNotifications.size();
            
            // Delete all notifications
            for (StockNotification notification : allNotifications) {
                stockNotificationService.deleteNotification(notification.getId());
            }
            
            response.put("success", true);
            response.put("message", "All " + deletedCount + " notifications deleted successfully");
            response.put("deletedCount", deletedCount);
            
            log.info("🗑️ Successfully deleted {} notifications for tenant: {}", deletedCount, tenantId);
            
        } catch (Exception e) {
            response.put("success", false);
            response.put("error", e.getMessage());
            log.error("🗑️ Error deleting all notifications for tenant {}: {}", tenantId, e.getMessage(), e);
        }
        
        return ResponseEntity.ok(response);
    }
}
