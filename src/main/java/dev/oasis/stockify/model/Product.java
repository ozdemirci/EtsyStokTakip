package dev.oasis.stockify.model;

import jakarta.persistence.*;
import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@Entity
@Table(name = "product")
public class Product {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    @Column(name = "sku",  unique = true)
    private String sku;

    @Column(name = "title")
    private String title;

    @Column(name = "description")
    private String description;

    @Column(name = "category")
    private String category;

    @Column(name = "price")
    private BigDecimal price;

    @Column(name = "stock_level")
    private Integer stockLevel;

    @Column(name = "low_stock_threshold")
    private Integer lowStockThreshold;

    @Column(name = "etsy_product_id")
    private String etsyProductId;

    // Audit fields
    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @Column(name = "created_by")
    private Long createdBy;

    @Column(name = "updated_by")
    private Long updatedBy;

    // Status fields
    @Column(name = "is_active")
    private Boolean isActive;

    @Column(name = "is_featured")
    private Boolean isFeatured;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }

    public boolean isLowStock() {
        return stockLevel != null && lowStockThreshold != null && stockLevel <= lowStockThreshold;
    }
}
