package dev.oasis.stockify.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * DTO for barcode scan response
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BarcodeScanResponseDTO {
    
    private boolean success;
    private String message;
    private ProductResponseDTO product;
    private Integer previousStock;
    private Integer newStock;
    private LocalDateTime timestamp;
    private String scanCode;
    private BarcodeScanRequestDTO.ScanType scanType;
    private BarcodeScanRequestDTO.ScanAction action;
    private Integer quantity;
    private String notes;
    
    /**
     * Create a success response
     */
    public static BarcodeScanResponseDTO success(String message, ProductResponseDTO product) {
        return BarcodeScanResponseDTO.builder()
                .success(true)
                .message(message)
                .product(product)
                .timestamp(LocalDateTime.now())
                .build();
    }
    
    /**
     * Create an error response
     */
    public static BarcodeScanResponseDTO error(String message) {
        return BarcodeScanResponseDTO.builder()
                .success(false)
                .message(message)
                .timestamp(LocalDateTime.now())
                .build();
    }
}
