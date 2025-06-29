package dev.oasis.stockify.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;
import lombok.Builder;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * DTO for displaying product information
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class ProductResponseDTO {

    private Long id;
    private String title;
    private String description;
    private String sku;
    private String category;
    private BigDecimal price;
    private Integer stockLevel;
    private Integer lowStockThreshold;
    private String etsyProductId;
    private Boolean isActive;
    private Boolean isFeatured;

    // Audit fields for display
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private Long createdBy;
    private Long updatedBy;

    public boolean isLowStock() {
        if (stockLevel == null || lowStockThreshold == null) {
            return false;
        }
        return stockLevel <= lowStockThreshold;
    }
}

