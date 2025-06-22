package dev.oasis.stockify.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * DTO for displaying product category information
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class ProductCategoryResponseDTO {

    private Long id;
    private String name;
    private String description;
    private Boolean active;
    private Integer sortOrder;
    private String hexColor;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private Long productCount; 
    
}
