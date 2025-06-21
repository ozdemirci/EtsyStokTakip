package dev.oasis.stockify.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * DTO for creating or updating a product
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ProductCreateDTO {  
    private Long id; 
    
    @NotBlank(message = "Başlık boş olamaz")
    @Size(min = 3, max = 50, message = "Başlık 3 ile 50 karakter arasında olmalıdır")
    private String title;

    @Size(max = 1000, message = "Açıklama en fazla 1000 karakter olabilir")
    private String description; 
    
    @NotBlank(message = "SKU boş olamaz")
    @Size(min = 3, max = 50, message = "SKU 3 ile 50 karakter arasında olmalıdır")
    private String sku;    
    
    @NotBlank(message = "Kategori boş olamaz")
    private String category;

    @NotNull(message = "Fiyat boş olamaz")
    @Min(value = 0, message = "Fiyat negatif olamaz")
    private BigDecimal price;  

    @Min(value = 0, message = "Stok negatif olamaz")
    private int stockLevel;   
    
    @Min(value = 1, message = "Düşük stok eşiği en az 1 olmalıdır")
    private int lowStockThreshold = 5;
      private String etsyProductId;    
    private Boolean isActive = true; 
    private Boolean isFeatured = false;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}

