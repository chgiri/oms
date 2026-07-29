package com.giri.oms.productclient.dto;

import java.math.BigDecimal;

/**
 * Deliberately NOT a reuse of product-service's own {@code ProductResponse} —
 * sharing that class would be a compile-time coupling between two separately
 * deployed, separately versioned services, which defeats the entire point of
 * the split (product-service could never change its response shape without
 * a coordinated release). This carries only the three fields the monolith's
 * two call sites actually use (see OrderServiceImpl.createOrder's snapshot
 * and InventoryServiceImpl.doCreateInventory/doUpdateInventory's validation)
 * — not product-service's full response shape (status, createdAt, updatedAt
 * are all left out; add them here only if and when a caller actually needs
 * one).
 */
public record ProductClientResponse(
        Long id,
        String name,
        BigDecimal price
) {
}
