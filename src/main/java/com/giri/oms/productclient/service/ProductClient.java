package com.giri.oms.productclient.service;

import com.giri.oms.productclient.dto.ProductClientResponse;

/**
 * The monolith's client to product-service. Deliberately shaped like
 * {@code ProductService.getProductById} (same single method, same
 * throws-on-not-found contract via {@link com.giri.oms.product.exception.ProductNotFoundException})
 * so that swapping a call site from the in-process ProductService to this
 * (Stage 4 of the microservices-prep plan) is close to a one-line change,
 * not a rewrite of the caller's error handling.
 */
public interface ProductClient {

    /**
     * @throws com.giri.oms.product.exception.ProductNotFoundException product-service
     *         returned 404 — the product genuinely doesn't exist. Never retried,
     *         never counted against the circuit breaker (see ProductClientImpl).
     * @throws com.giri.oms.productclient.exception.ProductServiceUnavailableException
     *         product-service could not be reached in time — a timeout, a 5xx, or
     *         the circuit breaker is currently open. Per Stage 0's resilience
     *         decision, there is deliberately no fallback/stale-data return here —
     *         the caller (an order/inventory write) fails and must be retried later.
     */
    ProductClientResponse getProduct(Long productId);
}
