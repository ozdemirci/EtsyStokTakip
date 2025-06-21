package dev.oasis.stockify.repository;

import dev.oasis.stockify.model.ProductCategory;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * Repository for ProductCategory entity
 */
@Repository
public interface ProductCategoryRepository extends JpaRepository<ProductCategory, Long> {
    
    /**
     * Find all active categories ordered by sortOrder and name
     */
    List<ProductCategory> findByIsActiveTrueOrderBySortOrderAscNameAsc();
    
    /**
     * Find all categories ordered by sortOrder and name
     */
    List<ProductCategory> findAllByOrderBySortOrderAscNameAsc();
    
    /**
     * Find category by name (case insensitive)
     */
    Optional<ProductCategory> findByNameIgnoreCase(String name);
    
    /**
     * Check if category name exists (case insensitive) excluding given id
     */
    boolean existsByNameIgnoreCaseAndIdNot(String name, Long id);
    
    /**
     * Check if category name exists (case insensitive)
     */
    boolean existsByNameIgnoreCase(String name);
    
    /**
     * Get category with product count
     */
    @Query("SELECT c FROM ProductCategory c LEFT JOIN Product p ON p.category = c.name WHERE c.id = :id")
    Optional<ProductCategory> findByIdWithProductCount(Long id);
}
