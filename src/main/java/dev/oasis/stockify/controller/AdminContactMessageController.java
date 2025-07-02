package dev.oasis.stockify.controller;

import dev.oasis.stockify.model.ContactMessage;
import dev.oasis.stockify.service.ContactMessageService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.List;

/**
 * Admin controller for managing contact messages
 */
@Slf4j
@Controller
@RequestMapping("/admin/contact-messages")
@PreAuthorize("hasAnyRole('SUPER_ADMIN')")
@RequiredArgsConstructor
public class AdminContactMessageController {
    
    private final ContactMessageService contactMessageService;
    
    /**
     * Display contact messages page
     */
    @GetMapping
    public String contactMessagesPage(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String email,
            @RequestParam(required = false) String subject,
            @RequestParam(required = false) String name,
            @RequestParam(required = false) Boolean isRead,
            @RequestParam(required = false) Boolean responded,
            Model model) {
        
        try {
            Page<ContactMessage> messages;
            
            // If any search parameters are provided, use search, otherwise get all
            if (email != null || subject != null || name != null || isRead != null || responded != null) {
                messages = contactMessageService.searchMessages(email, subject, name, isRead, responded, page, size);
            } else {
                messages = contactMessageService.getAllContactMessages(page, size);
            }
            
            // Add pagination and data to model
            model.addAttribute("messages", messages.getContent());
            model.addAttribute("currentPage", messages.getNumber());
            model.addAttribute("totalPages", messages.getTotalPages());
            model.addAttribute("totalElements", messages.getTotalElements());
            model.addAttribute("pageSize", size);
            
            // Add search parameters back to model
            model.addAttribute("email", email);
            model.addAttribute("subject", subject);
            model.addAttribute("name", name);
            model.addAttribute("isRead", isRead);
            model.addAttribute("responded", responded);
            
            // Add statistics
            model.addAttribute("unreadCount", contactMessageService.countUnreadMessages());
            model.addAttribute("unrepliedCount", contactMessageService.countUnrepliedMessages());
            
            // Get recent unread messages for quick overview
            List<ContactMessage> recentUnread = contactMessageService.getUnreadMessages();
            model.addAttribute("recentUnread", recentUnread.stream().limit(5).toList());
            
            return "admin/contact-messages";
            
        } catch (Exception e) {
            log.error("❌ Error loading contact messages page: {}", e.getMessage(), e);
            model.addAttribute("error", "Failed to load contact messages: " + e.getMessage());
            return "admin/contact-messages";
        }
    }
    
    /**
     * View single contact message
     */
    @GetMapping("/{id}")
    public String viewContactMessage(@PathVariable Long id, Model model, RedirectAttributes redirectAttributes) {
        try {
            var messageOpt = contactMessageService.getContactMessage(id);
            if (messageOpt.isPresent()) {
                ContactMessage message = messageOpt.get();
                
                // Mark as read if not already read
                if (!message.getIsRead()) {
                    contactMessageService.markAsRead(id);
                    message.setIsRead(true); // Update local object for display
                }
                
                model.addAttribute("message", message);
                return "admin/contact-message-detail";
            } else {
                redirectAttributes.addFlashAttribute("errorMessage", "Contact message not found");
                return "redirect:/admin/contact-messages";
            }
        } catch (Exception e) {
            log.error("❌ Error viewing contact message {}: {}", id, e.getMessage(), e);
            redirectAttributes.addFlashAttribute("errorMessage", "Failed to load contact message");
            return "redirect:/admin/contact-messages";
        }
    }
    
    /**
     * Mark message as read
     */
    @PostMapping("/{id}/mark-read")
    public String markAsRead(@PathVariable Long id, RedirectAttributes redirectAttributes) {
        try {
            contactMessageService.markAsRead(id);
            redirectAttributes.addFlashAttribute("successMessage", "Message marked as read");
        } catch (Exception e) {
            log.error("❌ Error marking message as read: {}", e.getMessage(), e);
            redirectAttributes.addFlashAttribute("errorMessage", "Failed to mark message as read");
        }
        return "redirect:/admin/contact-messages";
    }
    
    /**
     * Mark message as responded
     */
    @PostMapping("/{id}/mark-responded")
    public String markAsResponded(@PathVariable Long id, RedirectAttributes redirectAttributes) {
        try {           
            Long currentUserId = 1L; // Placeholder
            contactMessageService.markAsResponded(id, currentUserId);
            redirectAttributes.addFlashAttribute("successMessage", "Message marked as responded");
        } catch (Exception e) {
            log.error("❌ Error marking message as responded: {}", e.getMessage(), e);
            redirectAttributes.addFlashAttribute("errorMessage", "Failed to mark message as responded");
        }
        return "redirect:/admin/contact-messages";
    }
    
    /**
     * Delete contact message
     */
    @PostMapping("/{id}/delete")
    public String deleteContactMessage(@PathVariable Long id, RedirectAttributes redirectAttributes) {
        try {
            contactMessageService.deleteContactMessage(id);
            redirectAttributes.addFlashAttribute("successMessage", "Contact message deleted successfully");
        } catch (Exception e) {
            log.error("❌ Error deleting contact message {}: {}", id, e.getMessage(), e);
            redirectAttributes.addFlashAttribute("errorMessage", "Failed to delete contact message");
        }
        return "redirect:/admin/contact-messages";
    }
    
    /**
     * Get unread messages count for AJAX requests
     */
    @GetMapping("/unread-count")
    @ResponseBody
    public long getUnreadCount() {
        return contactMessageService.countUnreadMessages();
    }
    
    /**
     * Get pending messages count for AJAX requests
     */
    @GetMapping("/pending-count")
    @ResponseBody
    public long getPendingCount() {
        return contactMessageService.countUnrepliedMessages();
    }
}
