package dev.oasis.stockify.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * DTO for creating or updating a product category
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ProductCategoryCreateDTO {
    
    private Long id;
    
    @NotBlank(message = "Category name cannot be empty")
    @Size(min = 2, max = 100, message = "Category name must be between 2 and 100 characters")
    private String name;
    
    @Size(max = 500, message = "Description can be maximum 500 characters")
    private String description;
    
    private Boolean isActive = true;
    
    private Integer sortOrder = 0;
    
    @Size(max = 7, message = "Hex color must be in format #RRGGBB")
    private String hexColor;
}
