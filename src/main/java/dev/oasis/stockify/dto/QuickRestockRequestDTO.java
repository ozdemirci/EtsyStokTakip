package dev.oasis.stockify.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

/**
 * DTO for quick restock requests from the admin panel
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class QuickRestockRequestDTO {
    
    @NotNull(message = "Product ID is required")
    private Long productId;
    
    @NotNull(message = "Quantity is required")
    @Positive(message = "Quantity must be positive")
    private Integer quantity;
    
    @NotNull(message = "Operation is required")
    private String operation; // "ADD" or "SET"
}
