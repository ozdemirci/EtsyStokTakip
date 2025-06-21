package dev.oasis.stockify.controller;

import dev.oasis.stockify.config.tenant.TenantContext;
import dev.oasis.stockify.model.StockNotification;
import dev.oasis.stockify.service.StockNotificationService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseBody;

import jakarta.servlet.http.HttpServletRequest;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Controller
@RequestMapping("/user/notifications")
@PreAuthorize("hasRole('USER')")
@Slf4j
public class UserNotificationController {

    private final StockNotificationService stockNotificationService;

    public UserNotificationController(StockNotificationService stockNotificationService) {
        this.stockNotificationService = stockNotificationService;
    }

    @GetMapping
    public String notifications(HttpServletRequest request, Model model) {
        String tenantId = getCurrentTenantId(request);
        log.info("🔔 User accessing notifications for tenant: {}", tenantId);
        
        List<StockNotification> notifications = stockNotificationService.getAllNotifications();
        model.addAttribute("notifications", notifications);
        model.addAttribute("tenantId", tenantId);
        return "user/notifications";
    }

    @PostMapping("/mark-all-read")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> markAllAsRead() {
        log.info("📧 markAllAsRead endpoint called by user");
        try {
            int count = stockNotificationService.markAllAsRead();
            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("count", count);
            response.put("message", count + " notifications marked as read");
            log.info("✅ User marked {} notifications as read successfully", count);
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
    public ResponseEntity<Map<String, Object>> deleteAllRead() {
        log.info("🗑️ deleteAllRead endpoint called by user");
        try {
            int count = stockNotificationService.deleteAllRead();
            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("count", count);
            response.put("message", count + " read notifications deleted");
            log.info("✅ User deleted {} read notifications successfully", count);
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
    public ResponseEntity<Map<String, Object>> deleteNotification(@PathVariable Long id) {
        try {
            boolean deleted = stockNotificationService.deleteNotification(id);
            Map<String, Object> response = new HashMap<>();
            response.put("success", deleted);
            response.put("message", deleted ? "Notification deleted successfully" : "Failed to delete notification");
            log.info("User deleted notification with ID: {}", id);
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
    public ResponseEntity<Map<String, Object>> testEndpoint() {
        Map<String, Object> response = new HashMap<>();
        response.put("success", true);
        response.put("message", "User notification endpoint is working!");
        response.put("timestamp", java.time.LocalDateTime.now().toString());
        log.info("User notification test endpoint called");
        return ResponseEntity.ok(response);
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
