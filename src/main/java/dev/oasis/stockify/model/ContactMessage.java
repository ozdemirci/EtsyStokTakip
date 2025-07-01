package dev.oasis.stockify.model;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

/**
 * Contact entity for storing contact form submissions
 */
@Entity
@Table(name = "contact_messages")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ContactMessage {
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    @Column(name = "first_name", nullable = false, length = 100)
    private String firstName;
    
    @Column(name = "last_name", nullable = false, length = 100)
    private String lastName;
    
    @Column(name = "email", nullable = false, length = 255)
    private String email;
    
    @Column(name = "phone", length = 20)
    private String phone;
    
    @Column(name = "company", length = 255)
    private String company;
    
    @Column(name = "subject", nullable = false, length = 100)
    private String subject;
    
    @Column(name = "message", nullable = false, columnDefinition = "TEXT")
    private String message;
    
    @Column(name = "is_read", nullable = false)
    private Boolean isRead = false;
    
    @Column(name = "responded", nullable = false)
    private Boolean responded = false;
    
    @Column(name = "ip_address", length = 45)
    private String ipAddress;
    
    @Column(name = "user_agent", length = 500)
    private String userAgent;
    
    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;
    
    @Column(name = "responded_at")
    private LocalDateTime respondedAt;
    
    @Column(name = "responded_by")
    private Long respondedBy;
    
    @PrePersist
    protected void onCreate() {
        if (createdAt == null) {
            createdAt = LocalDateTime.now();
        }
        if (isRead == null) {
            isRead = false;
        }
        if (responded == null) {
            responded = false;
        }
    }
    
    /**
     * Get full name
     */
    public String getFullName() {
        return firstName + " " + lastName;
    }
    
    /**
     * Mark as read
     */
    public void markAsRead() {
        this.isRead = true;
    }
    
    /**
     * Mark as responded
     */
    public void markAsResponded(Long respondedByUserId) {
        this.responded = true;
        this.respondedAt = LocalDateTime.now();
        this.respondedBy = respondedByUserId;
    }
}
