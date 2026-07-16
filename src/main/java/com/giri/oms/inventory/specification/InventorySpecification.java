package com.giri.oms.inventory.specification;

import com.giri.oms.inventory.entity.Inventory;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.jpa.domain.Specification;

import java.util.ArrayList;
import java.util.List;

/**
 * Each method returns a Specification<Inventory> — essentially a lambda that builds
 * one WHERE condition. They're combined with .and()/.or() at the call site, so
 * filters compose instead of being hardcoded into one big query string.
 */
public class InventorySpecification {

    private InventorySpecification() {
        // utility class — no instances
    }

    public static Specification<Inventory> hasProductId(Long productId) {
        return (root, query, cb) ->
                productId == null ? null : cb.equal(root.get("product").get("id"), productId);
    }

    public static Specification<Inventory> hasLocation(String location) {
        return (root, query, cb) ->
                location == null ? null : cb.like(cb.lower(root.get("location")), "%" + location.toLowerCase() + "%");
    }

    public static Specification<Inventory> isLowStock(boolean lowStockOnly) {
        return (root, query, cb) ->
                !lowStockOnly ? null : cb.lessThanOrEqualTo(root.get("quantityAvailable"), root.get("reorderLevel"));
    }

    /**
     * Combines all filters, skipping any that are null (Specification.and() treats
     * a null Specification as a no-op condition automatically).
     */
    public static Specification<Inventory> buildSearchSpec(Long productId, String location, boolean lowStockOnly) {
        return Specification.where(hasProductId(productId))
                .and(hasLocation(location))
                .and(isLowStock(lowStockOnly));
    }

    /**
     * Alternative style: build the predicate list manually inside one Specification.
     * Useful when conditions need to reference each other or when you want a single
     * entry point instead of several small static methods.
     */
    public static Specification<Inventory> searchInventory(Long productId, String location, boolean lowStockOnly) {
        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();

            if (productId != null) {
                predicates.add(cb.equal(root.get("product").get("id"), productId));
            }
            if (location != null && !location.isBlank()) {
                predicates.add(cb.like(cb.lower(root.get("location")), "%" + location.toLowerCase() + "%"));
            }
            if (lowStockOnly) {
                predicates.add(cb.lessThanOrEqualTo(root.get("quantityAvailable"), root.get("reorderLevel")));
            }

            return cb.and(predicates.toArray(new Predicate[0]));
        };
    }
}
