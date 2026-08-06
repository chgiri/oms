package com.giri.oms.customerclient.service;

import com.giri.oms.customerclient.dto.CustomerClientResponse;

/**
 * The monolith's client to customer-service. Deliberately shaped like
 * {@code CustomerService.getCustomerById} (same single method, same
 * throws-on-not-found contract via
 * {@link com.giri.oms.customerclient.exception.CustomerNotFoundException}) so that
 * swapping OrderServiceImpl's call site from the in-process CustomerService
 * to this (Stage 4) is close to a one-line change, not a rewrite of the
 * caller's error handling. Mirrors {@code productclient.service.ProductClient}
 * exactly.
 */
public interface CustomerClient {

    /**
     * @throws com.giri.oms.customerclient.exception.CustomerNotFoundException customer-service
     *         returned 404 — the customer genuinely doesn't exist. Never retried,
     *         never counted against the circuit breaker (see CustomerClientImpl).
     * @throws com.giri.oms.customerclient.exception.CustomerServiceUnavailableException
     *         customer-service could not be reached in time — a timeout, a 5xx, or
     *         the circuit breaker is currently open. Same Stage 0 resilience decision
     *         as ProductClient — no fallback/stale-data return; the caller (order
     *         creation) fails and must be retried later.
     */
    CustomerClientResponse getCustomer(Long customerId);
}
