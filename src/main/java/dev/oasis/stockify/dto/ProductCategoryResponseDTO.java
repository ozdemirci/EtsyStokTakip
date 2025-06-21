package dev.oasis.stockify.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * DTO for displaying product category information
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ProductCategoryResponseDTO {
    
    private Long id;
    private String name;
    private String description;
    private Boolean isActive;
    private Integer sortOrder;
    private String hexColor;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private Long productCount; // Number of products in this category
}
