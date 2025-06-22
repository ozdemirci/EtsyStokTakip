package dev.oasis.stockify.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Response DTO for quick restock operations
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class QuickRestockResponseDTO {
    
    
    private Long productId;
    private String productTitle;   
    private Integer oldStockLevel;
    private Integer newStockLevel;
    private Integer quantityAdded;
    private String operation;
    private String message;
    
    public static QuickRestockResponseDTO success(Long productId, String productTitle, 
                                                Integer oldStock, Integer newStock, 
                                                Integer quantity, String operation) {
        QuickRestockResponseDTO response = new QuickRestockResponseDTO();
        response.setProductId(productId);
        response.setProductTitle(productTitle);
        response.setOldStockLevel(oldStock);
        response.setNewStockLevel(newStock);
        response.setQuantityAdded(quantity);
        response.setOperation(operation);
        response.setMessage("Stock updated successfully");
        return response;
    }
}
