package com.yourcompany.etsystoktakip.dto;

import java.math.BigDecimal;

/**
 * DTO for displaying product information
 */
public class ProductResponseDTO {
    private Long id;
    private String title;
    private String category;
    private BigDecimal price;
    private int stockLevel;
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

    public String getEtsyProductId() {
        return etsyProductId;
    }

    public void setEtsyProductId(String etsyProductId) {
        this.etsyProductId = etsyProductId;
    }
}