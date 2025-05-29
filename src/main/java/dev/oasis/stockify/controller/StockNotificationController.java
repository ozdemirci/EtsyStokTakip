package dev.oasis.stockify.controller;

import dev.oasis.stockify.model.StockNotification;
import dev.oasis.stockify.service.StockNotificationService;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Controller
@RequestMapping("/notifications")
public class StockNotificationController {
    private final StockNotificationService stockNotificationService;

    public StockNotificationController(StockNotificationService stockNotificationService) {
        this.stockNotificationService = stockNotificationService;
    }

    @GetMapping
    public String listNotifications(Model model) {
        List<StockNotification> notifications = stockNotificationService.getAllNotifications();
        model.addAttribute("notifications", notifications);
        return "notification-list";
    }

    @PostMapping("/{id}/read")
    @ResponseBody
    public void markAsRead(@PathVariable Long id) {
        stockNotificationService.markAsRead(id);
    }

    @ModelAttribute("unreadCount")
    public int unreadCount() {
        return stockNotificationService.getUnreadNotifications().size();
    }
}
