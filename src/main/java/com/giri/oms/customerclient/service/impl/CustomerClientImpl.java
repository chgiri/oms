package com.giri.oms.customerclient.service.impl;

import com.giri.oms.customer.exception.CustomerNotFoundException;
import com.giri.oms.customerclient.dto.CustomerClientResponse;
import com.giri.oms.customerclient.exception.CustomerServiceUnavailableException;
import com.giri.oms.customerclient.service.CustomerClient;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import io.github.resilience4j.retry.Retry;
import io.github.resilience4j.retry.RetryRegistry;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;

import java.util.function.Supplier;

/**
 * Stage 2 of the microservices-prep plan (Customer extraction). Identical
 * shape and reasoning to {@code productclient.service.impl.ProductClientImpl}
 * — see that class's Javadoc for the full explanation of why resilience4j's
 * programmatic API is used instead of the annotation form, and why there is
 * deliberately no fallback method anywhere in this class.
 */
@Slf4j
@Component
public class CustomerClientImpl implements CustomerClient {

    private static final String RESILIENCE_INSTANCE_NAME = "customerClient";

    private final RestClient customerServiceRestClient;
    private final CircuitBreaker circuitBreaker;
    private final Retry retry;

    public CustomerClientImpl(RestClient customerServiceRestClient,
                               CircuitBreakerRegistry circuitBreakerRegistry,
                               RetryRegistry retryRegistry) {
        this.customerServiceRestClient = customerServiceRestClient;
        this.circuitBreaker = circuitBreakerRegistry.circuitBreaker(RESILIENCE_INSTANCE_NAME);
        this.retry = retryRegistry.retry(RESILIENCE_INSTANCE_NAME);
    }

    @Override
    public CustomerClientResponse getCustomer(Long customerId) {
        Supplier<CustomerClientResponse> call = () -> doGetCustomer(customerId);
        Supplier<CustomerClientResponse> resilient =
                CircuitBreaker.decorateSupplier(circuitBreaker, Retry.decorateSupplier(retry, call));

        try {
            return resilient.get();
        } catch (CustomerNotFoundException ex) {
            // Already translated from a 404 by doGetCustomer — surfacing it
            // unchanged is the point (see CustomerClient's Javadoc). Never
            // reaches Retry/CircuitBreaker's exception-counting logic below,
            // since CustomerNotFoundException isn't in either's record/retry
            // list (see application.properties resilience4j.*.instances.customerClient).
            throw ex;
        } catch (Exception ex) {
            log.warn("customer-service call failed for customer id {}: {}", customerId, ex.getMessage());
            throw new CustomerServiceUnavailableException(customerId, ex);
        }
    }

    private CustomerClientResponse doGetCustomer(Long customerId) {
        try {
            return customerServiceRestClient.get()
                    .uri("/customers/{id}", customerId)
                    .retrieve()
                    .body(CustomerClientResponse.class);
        } catch (HttpClientErrorException.NotFound ex) {
            throw new CustomerNotFoundException(customerId);
        }
        // Any other HttpClientErrorException, HttpServerErrorException, or
        // ResourceAccessException propagates as-is — resilience4j's instance
        // config (application.properties) decides retryable/circuit-breaking.
    }
}
