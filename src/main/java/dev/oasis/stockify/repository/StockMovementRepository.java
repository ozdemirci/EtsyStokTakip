package dev.oasis.stockify.repository;

import dev.oasis.stockify.model.StockMovement;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Repository for stock movement operations
 */
@Repository
public interface StockMovementRepository extends JpaRepository<StockMovement, Long> {

    /**
     * Find stock movements by product ID
     */
    @Query("SELECT sm FROM StockMovement sm JOIN FETCH sm.product WHERE sm.product.id = :productId ORDER BY sm.createdAt DESC")
    List<StockMovement> findByProductId(@Param("productId") Long productId);

    /**
     * Find stock movements by product ID with pagination
     */
    @Query("SELECT sm FROM StockMovement sm JOIN FETCH sm.product WHERE sm.product.id = :productId ORDER BY sm.createdAt DESC")
    Page<StockMovement> findByProductId(@Param("productId") Long productId, Pageable pageable);

    /**
     * Find stock movements by movement type
     */
    @Query("SELECT sm FROM StockMovement sm WHERE sm.movementType = :movementType ORDER BY sm.createdAt DESC")
    List<StockMovement> findByMovementType(@Param("movementType") StockMovement.MovementType movementType);

    /**
     * Find stock movements between dates
     */
    @Query("SELECT sm FROM StockMovement sm WHERE sm.createdAt BETWEEN :startDate AND :endDate ORDER BY sm.createdAt DESC")
    List<StockMovement> findByDateRange(@Param("startDate") LocalDateTime startDate, @Param("endDate") LocalDateTime endDate);

    /**
     * Find recent stock movements
     */
    @Query("SELECT sm FROM StockMovement sm JOIN FETCH sm.product ORDER BY sm.createdAt DESC")
    Page<StockMovement> findRecent(Pageable pageable);

    /**
     * Count total stock movements
     */
    @Query("SELECT COUNT(sm) FROM StockMovement sm")
    long countTotal();

    /**
     * Count stock movements by type
     */
    @Query("SELECT COUNT(sm) FROM StockMovement sm WHERE sm.movementType = :movementType")
    long countByMovementType(@Param("movementType") StockMovement.MovementType movementType);

    /**
     * Find stock movements by reference ID
     */
    @Query("SELECT sm FROM StockMovement sm WHERE sm.referenceId = :referenceId")
    List<StockMovement> findByReferenceId(@Param("referenceId") String referenceId);

    /**
     * Find stock movements created by a specific user
     */
    @Query("SELECT sm FROM StockMovement sm JOIN FETCH sm.product WHERE sm.createdBy = :userId ORDER BY sm.createdAt DESC")
    List<StockMovement> findByCreatedBy(@Param("userId") Long userId);

    List<StockMovement> findByCreatedAtBetweenOrderByCreatedAtDesc(LocalDateTime start, LocalDateTime end);

}
