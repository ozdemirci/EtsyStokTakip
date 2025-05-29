package dev.oasis.stockify.model;

import jakarta.persistence.*;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;

@Entity
public class Product {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @NotBlank(message = "Başlık boş olamaz")
    @Size(min = 3, max = 50, message = "Başlık 3 ile 50 karakter arasında olmalıdır")
    private String title;

    @NotBlank(message = "Kategori boş olamaz")
    private String category;

    @NotNull(message = "Fiyat boş olamaz")
    @Min(value = 0, message = "Fiyat negatif olamaz")
    private BigDecimal price;

    @Min(value = 0, message = "Stok negatif olamaz")
    private int stockLevel;

    @Min(value = 1, message = "Düşük stok eşiği en az 1 olmalıdır")
    private int lowStockThreshold = 5; // Default value of 5

    private String etsyProductId;

    // Getters and Setters
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }

    public String getCategory() { return category; }
    public void setCategory(String category) { this.category = category; }

    public BigDecimal getPrice() { return price; }
    public void setPrice(BigDecimal price) { this.price = price; }

    public int getStockLevel() { return stockLevel; }
    public void setStockLevel(int stockLevel) { this.stockLevel = stockLevel; }

    public int getLowStockThreshold() { return lowStockThreshold; }
    public void setLowStockThreshold(int lowStockThreshold) { this.lowStockThreshold = lowStockThreshold; }

    public boolean isLowStock() {
        return stockLevel <= lowStockThreshold;
    }

    public String getEtsyProductId() { return etsyProductId; }
    public void setEtsyProductId(String etsyProductId) { this.etsyProductId = etsyProductId; }
}
