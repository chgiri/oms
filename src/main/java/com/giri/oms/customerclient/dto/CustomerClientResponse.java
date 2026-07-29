package com.giri.oms.customerclient.dto;

/**
 * Deliberately NOT a reuse of customer-service's own {@code CustomerResponse}
 * — same reasoning as {@code productclient.dto.ProductClientResponse}: reusing
 * that class would compile-couple two separately deployed, separately
 * versioned services. This carries only the fields the monolith's one call
 * site actually uses (see OrderServiceImpl.createOrder's customer-name
 * snapshot) — not customer-service's full response shape (email, phone,
 * address, status, createdAt, updatedAt are all left out; add them here only
 * if and when a caller actually needs one).
 */
public record CustomerClientResponse(
        Long id,
        String firstName,
        String lastName
) {
}
