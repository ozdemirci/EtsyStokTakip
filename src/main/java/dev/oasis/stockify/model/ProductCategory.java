package dev.oasis.stockify.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;

/**
 * Product Category entity for tenant-specific categories
 */
@Entity
@Table(name = "product_categories")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ProductCategory {
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    @Column(nullable = false, length = 100)
    private String name;
    
    @Column(length = 500)
    private String description;
      @Column(name = "is_active", nullable = false)
    private Boolean isActive = true;
    
    @Column(name = "sort_order", nullable = false)
    private Integer sortOrder = 0;
    
    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;
    
    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;    // Add unique constraint per tenant (will be enforced by tenant schema isolation)
    @Column(name = "hex_color", length = 20)
    private String hexColor; // For UI display color coding
}
