package com.giri.oms.inventory.repository;

import com.giri.oms.inventory.entity.Inventory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface InventoryRepository extends JpaRepository<Inventory, Long>, JpaSpecificationExecutor<Inventory> {

    // Derived query methods — Spring parses the method name into SQL.
    // "ProductId" navigates the product relation's id field automatically.

    Optional<Inventory> findByProductIdAndLocation(Long productId, String location);

    boolean existsByProductIdAndLocation(Long productId, String location);

    List<Inventory> findByLocation(String location);

    List<Inventory> findByProductId(Long productId);

    // JPQL @Query — for combining multiple optional filters in one query.
    // lowStockOnly filters to rows where available stock has dropped to/below the reorder threshold.
    @Query("""
            SELECT i FROM Inventory i
            WHERE (:productId IS NULL OR i.product.id = :productId)
              AND (:location IS NULL OR LOWER(i.location) LIKE LOWER(CONCAT('%', CAST(:location AS string), '%')))
              AND (:lowStockOnly = FALSE OR i.quantityAvailable <= i.reorderLevel)
            """)
    Page<Inventory> searchInventory(
            @Param("productId") Long productId,
            @Param("location") String location,
            @Param("lowStockOnly") boolean lowStockOnly,
            Pageable pageable
    );

}
