package dev.oasis.stockify.service;

import dev.oasis.stockify.dto.ContactMessageDTO;
import dev.oasis.stockify.mapper.ContactMessageMapper;
import dev.oasis.stockify.model.ContactMessage;
import dev.oasis.stockify.repository.ContactMessageRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import jakarta.servlet.http.HttpServletRequest;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * Service for managing contact messages
 */
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional
public class ContactMessageService {
    
    private final ContactMessageRepository contactMessageRepository;
    private final ContactMessageMapper contactMessageMapper;
    
    /**
     * Save a new contact message
     */
    public ContactMessage saveContactMessage(ContactMessageDTO dto, HttpServletRequest request) {
        try {
            log.info("💬 Saving contact message from: {} {}", dto.getFirstName(), dto.getLastName());
            
            ContactMessage contactMessage = contactMessageMapper.toEntity(dto);
            
            // Set additional tracking information
            contactMessage.setIpAddress(getClientIpAddress(request));
            contactMessage.setUserAgent(request.getHeader("User-Agent"));
            
            ContactMessage savedMessage = contactMessageRepository.save(contactMessage);
            
            log.info("✅ Contact message saved successfully with ID: {}", savedMessage.getId());
            return savedMessage;
            
        } catch (Exception e) {
            log.error("❌ Error saving contact message: {}", e.getMessage(), e);
            throw new RuntimeException("Failed to save contact message", e);
        }
    }
    
    /**
     * Get contact message by ID
     */
    @Transactional(readOnly = true)
    public Optional<ContactMessage> getContactMessage(Long id) {
        return contactMessageRepository.findById(id);
    }
    
    /**
     * Get all contact messages with pagination
     */
    @Transactional(readOnly = true)
    public Page<ContactMessage> getAllContactMessages(int page, int size) {
        Pageable pageable = PageRequest.of(page, size);
        return contactMessageRepository.findAll(pageable);
    }
    
    /**
     * Get unread messages
     */
    @Transactional(readOnly = true)
    public List<ContactMessage> getUnreadMessages() {
        return contactMessageRepository.findByIsReadFalseOrderByCreatedAtDesc();
    }
    
    /**
     * Get unreplied messages
     */
    @Transactional(readOnly = true)
    public List<ContactMessage> getUnrepliedMessages() {
        return contactMessageRepository.findByRespondedFalseOrderByCreatedAtDesc();
    }
    
    /**
     * Count unread messages
     */
    @Transactional(readOnly = true)
    public long countUnreadMessages() {
        return contactMessageRepository.countByIsReadFalse();
    }
    
    /**
     * Count unreplied messages
     */
    @Transactional(readOnly = true)
    public long countUnrepliedMessages() {
        return contactMessageRepository.countByRespondedFalse();
    }
    
    /**
     * Mark message as read
     */
    public void markAsRead(Long messageId) {
        try {
            Optional<ContactMessage> messageOpt = contactMessageRepository.findById(messageId);
            if (messageOpt.isPresent()) {
                ContactMessage message = messageOpt.get();
                message.markAsRead();
                contactMessageRepository.save(message);
                log.info("✅ Message {} marked as read", messageId);
            }
        } catch (Exception e) {
            log.error("❌ Error marking message as read: {}", e.getMessage(), e);
            throw new RuntimeException("Failed to mark message as read", e);
        }
    }
    
    /**
     * Mark message as responded
     */
    public void markAsResponded(Long messageId, Long respondedByUserId) {
        try {
            Optional<ContactMessage> messageOpt = contactMessageRepository.findById(messageId);
            if (messageOpt.isPresent()) {
                ContactMessage message = messageOpt.get();
                message.markAsResponded(respondedByUserId);
                contactMessageRepository.save(message);
                log.info("✅ Message {} marked as responded by user {}", messageId, respondedByUserId);
            }
        } catch (Exception e) {
            log.error("❌ Error marking message as responded: {}", e.getMessage(), e);
            throw new RuntimeException("Failed to mark message as responded", e);
        }
    }
    
    /**
     * Search messages
     */
    @Transactional(readOnly = true)
    public Page<ContactMessage> searchMessages(String email, String subject, String name, 
                                             Boolean isRead, Boolean responded, int page, int size) {
        Pageable pageable = PageRequest.of(page, size);
        return contactMessageRepository.searchMessages(email, subject, name, isRead, responded, pageable);
    }
    
    /**
     * Get recent messages
     */
    @Transactional(readOnly = true)
    public List<ContactMessage> getRecentMessages(int limit) {
        Pageable pageable = PageRequest.of(0, limit);
        return contactMessageRepository.findRecentMessages(pageable);
    }
    
    /**
     * Delete contact message
     */
    public void deleteContactMessage(Long messageId) {
        try {
            contactMessageRepository.deleteById(messageId);
            log.info("✅ Contact message {} deleted successfully", messageId);
        } catch (Exception e) {
            log.error("❌ Error deleting contact message: {}", e.getMessage(), e);
            throw new RuntimeException("Failed to delete contact message", e);
        }
    }
    
    /**
     * Get messages created in the last days
     */
    @Transactional(readOnly = true)
    public Page<ContactMessage> getMessagesInLastDays(int days, int page, int size) {
        LocalDateTime startDate = LocalDateTime.now().minusDays(days);
        LocalDateTime endDate = LocalDateTime.now();
        Pageable pageable = PageRequest.of(page, size);
        return contactMessageRepository.findByCreatedAtBetweenOrderByCreatedAtDesc(startDate, endDate, pageable);
    }
    
    /**
     * Extract client IP address from request
     */
    private String getClientIpAddress(HttpServletRequest request) {
        String[] headerNames = {
            "X-Forwarded-For",
            "X-Real-IP",
            "Proxy-Client-IP",
            "WL-Proxy-Client-IP",
            "HTTP_X_FORWARDED_FOR",
            "HTTP_X_FORWARDED",
            "HTTP_X_CLUSTER_CLIENT_IP",
            "HTTP_CLIENT_IP",
            "HTTP_FORWARDED_FOR",
            "HTTP_FORWARDED",
            "HTTP_VIA",
            "REMOTE_ADDR"
        };
        
        for (String headerName : headerNames) {
            String ip = request.getHeader(headerName);
            if (ip != null && !ip.isEmpty() && !"unknown".equalsIgnoreCase(ip)) {
                // Handle comma-separated IPs (e.g., X-Forwarded-For)
                return ip.split(",")[0].trim();
            }
        }
        
        return request.getRemoteAddr();
    }
}
