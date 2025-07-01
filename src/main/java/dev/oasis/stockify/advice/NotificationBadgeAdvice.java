package dev.oasis.stockify.advice;

import dev.oasis.stockify.service.StockNotificationService;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.ui.Model;
import jakarta.servlet.http.HttpServletRequest;

@ControllerAdvice(basePackages = "dev.oasis.stockify.controller")
public class NotificationBadgeAdvice {

    private final StockNotificationService stockNotificationService;

    public NotificationBadgeAdvice(StockNotificationService stockNotificationService) {
        this.stockNotificationService = stockNotificationService;
    }

    @ModelAttribute
    public void addUnreadNotifications(Model model, HttpServletRequest request) {
        String uri = request.getRequestURI();
        // Only add notifications for admin and user pages
        if (!uri.startsWith("/admin") && !uri.startsWith("/user")) {
            return;
        }
        try {
            long unread = stockNotificationService.getUnreadNotifications().size();
            model.addAttribute("unreadNotifications", unread);
        } catch (Exception e) {
            model.addAttribute("unreadNotifications", 0);
        }
    }
}
