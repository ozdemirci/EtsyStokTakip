package dev.oasis.stockify.dto;

import lombok.Data;
import java.util.List;

@Data
public class BulkStockMovementCreateDTO {
    private List<StockMovementCreateDTO> movements;
    
}