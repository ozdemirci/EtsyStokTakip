package dev.oasis.stockify.repository;

import dev.oasis.stockify.model.ContactMessage;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Repository for ContactMessage entity
 */
@Repository
public interface ContactMessageRepository extends JpaRepository<ContactMessage, Long> {
    
    /**
     * Find all unread messages
     */
    List<ContactMessage> findByIsReadFalseOrderByCreatedAtDesc();
    
    /**
     * Find all unreplied messages
     */
    List<ContactMessage> findByRespondedFalseOrderByCreatedAtDesc();
    
    /**
     * Count unread messages
     */
    long countByIsReadFalse();
    
    /**
     * Count unreplied messages
     */
    long countByRespondedFalse();
    
    /**
     * Count responded messages
     */
    long countByRespondedTrue();
    
    /**
     * Find messages by subject
     */
    Page<ContactMessage> findBySubjectContainingIgnoreCaseOrderByCreatedAtDesc(String subject, Pageable pageable);
    
    /**
     * Find messages by email
     */
    Page<ContactMessage> findByEmailContainingIgnoreCaseOrderByCreatedAtDesc(String email, Pageable pageable);
    
    /**
     * Find messages by name
     */
    @Query("SELECT cm FROM ContactMessage cm WHERE " +
           "LOWER(CONCAT(cm.firstName, ' ', cm.lastName)) LIKE LOWER(CONCAT('%', :name, '%')) " +
           "ORDER BY cm.createdAt DESC")
    Page<ContactMessage> findByNameContainingIgnoreCase(@Param("name") String name, Pageable pageable);
    
    /**
     * Find messages created between dates
     */
    Page<ContactMessage> findByCreatedAtBetweenOrderByCreatedAtDesc(
        LocalDateTime startDate, 
        LocalDateTime endDate, 
        Pageable pageable
    );
    
    /**
     * Search messages by multiple criteria
     */
    @Query("SELECT cm FROM ContactMessage cm WHERE " +
           "(:email IS NULL OR LOWER(cm.email) LIKE LOWER(CONCAT('%', :email, '%'))) AND " +
           "(:subject IS NULL OR LOWER(cm.subject) LIKE LOWER(CONCAT('%', :subject, '%'))) AND " +
           "(:name IS NULL OR LOWER(CONCAT(cm.firstName, ' ', cm.lastName)) LIKE LOWER(CONCAT('%', :name, '%'))) AND " +
           "(:isRead IS NULL OR cm.isRead = :isRead) AND " +
           "(:responded IS NULL OR cm.responded = :responded) " +
           "ORDER BY cm.createdAt DESC")
    Page<ContactMessage> searchMessages(
        @Param("email") String email,
        @Param("subject") String subject,
        @Param("name") String name,
        @Param("isRead") Boolean isRead,
        @Param("responded") Boolean responded,
        Pageable pageable
    );
    
    /**
     * Find all messages ordered by creation date (newest first)
     */
    List<ContactMessage> findAllByOrderByCreatedAtDesc();
    
    /**
     * Find recent messages
     */
    @Query("SELECT cm FROM ContactMessage cm ORDER BY cm.createdAt DESC")
    List<ContactMessage> findRecentMessages(Pageable pageable);
}
