package dev.oasis.stockify.dto;

import java.math.BigDecimal;

/**
 * DTO for displaying product information
 */
public class ProductResponseDTO {    private Long id;
    private String title;
    private String description;
    private String sku;
    private String category;
    private BigDecimal price;
    private int stockLevel;
    private int lowStockThreshold;
    private String etsyProductId;

    // Getters and Setters
    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public String getSku() {
        return sku;
    }

    public void setSku(String sku) {
        this.sku = sku;
    }

    public String getCategory() {
        return category;
    }

    public void setCategory(String category) {
        this.category = category;
    }

    public BigDecimal getPrice() {
        return price;
    }

    public void setPrice(BigDecimal price) {
        this.price = price;
    }

    public int getStockLevel() {
        return stockLevel;
    }

    public void setStockLevel(int stockLevel) {
        this.stockLevel = stockLevel;
    }

    public int getLowStockThreshold() {
        return lowStockThreshold;
    }

    public void setLowStockThreshold(int lowStockThreshold) {
        this.lowStockThreshold = lowStockThreshold;
    }

    public boolean isLowStock() {
        return stockLevel <= lowStockThreshold;
    }

    public String getEtsyProductId() {
        return etsyProductId;
    }

    public void setEtsyProductId(String etsyProductId) {
        this.etsyProductId = etsyProductId;
    }
}

