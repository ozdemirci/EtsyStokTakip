package dev.oasis.stockify.repository;

import dev.oasis.stockify.model.Product;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface ProductRepository extends JpaRepository<Product, Long> {
    @Query("SELECT p FROM Product p WHERE " +
           "LOWER(p.title) LIKE LOWER(CONCAT('%', :searchTerm, '%')) OR " +
           "LOWER(p.category) LIKE LOWER(CONCAT('%', :searchTerm, '%'))")
    Page<Product> search(@Param("searchTerm") String searchTerm, Pageable pageable);

    Optional<Product> findBySku(String sku);
    
    @Query("SELECT p FROM Product p WHERE p.stockLevel <= p.lowStockThreshold")
    List<Product> findLowStockProducts();
    
    @Query("SELECT COUNT(p) FROM Product p WHERE p.stockLevel <= p.lowStockThreshold")
    long countLowStockProducts();
    
    @Query("SELECT COUNT(p) FROM Product p WHERE p.stockLevel = 0")
    long countOutOfStockProducts();
    
    @Query("SELECT p FROM Product p WHERE p.isActive = true")
    List<Product> findActiveProducts();

    long countByIsActive(Boolean isActive);

     @Query("SELECT p FROM Product p WHERE p.category = :category")
    List<Product> findByCategory(@Param("category") String category);
    
    @Query("SELECT COUNT(p) FROM Product p WHERE LOWER(p.category) = LOWER(:category)")
    long countByCategoryIgnoreCase(@Param("category") String category);
    
    @Query("SELECT COALESCE(SUM(p.price * p.stockLevel), 0) FROM Product p")
    Double calculateTotalInventoryValue();
}
