package dev.oasis.stockify.model;

import jakarta.persistence.*;
import lombok.Data;
import java.math.BigDecimal;

@Data
@Entity
@Table(name = "product")
public class Product {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private String sku;

    @Column(nullable = false)
    private String title;

    private String description;

    @Column(nullable = false)
    private String category;


    private BigDecimal price;


    private Integer stockLevel;


    private Integer lowStockThreshold;

    public boolean isLowStock() {
        return stockLevel <= lowStockThreshold;
    }
}
