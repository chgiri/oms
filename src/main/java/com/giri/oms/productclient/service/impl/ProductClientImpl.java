package com.giri.oms.productclient.service.impl;

import com.giri.oms.product.exception.ProductNotFoundException;
import com.giri.oms.productclient.dto.ProductClientResponse;
import com.giri.oms.productclient.exception.ProductServiceUnavailableException;
import com.giri.oms.productclient.service.ProductClient;
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
 * Stage 2 of the microservices-prep plan. Uses resilience4j's programmatic
 * API (CircuitBreaker/Retry obtained from their registries and composed by
 * hand) rather than the {@code @CircuitBreaker}/{@code @Retry} annotations —
 * this keeps the 404-vs-everything-else distinction from Stage 0's
 * resilience decision explicit in code (see {@link #doGetProduct}) instead
 * of depending on getting {@code recordExceptions}/{@code ignoreExceptions}
 * / a fallback-method signature exactly right via annotation configuration.
 * <p>
 * Deliberately NO fallback method / fallback value anywhere in this class —
 * per Stage 0, product-service being unreachable is not something this
 * client papers over with stale data; {@link #getProduct} either returns a
 * real, current answer or throws.
 */
@Slf4j
@Component
public class ProductClientImpl implements ProductClient {

    private static final String RESILIENCE_INSTANCE_NAME = "productClient";

    private final RestClient productServiceRestClient;
    private final CircuitBreaker circuitBreaker;
    private final Retry retry;

    public ProductClientImpl(RestClient productServiceRestClient,
                              CircuitBreakerRegistry circuitBreakerRegistry,
                              RetryRegistry retryRegistry) {
        this.productServiceRestClient = productServiceRestClient;
        this.circuitBreaker = circuitBreakerRegistry.circuitBreaker(RESILIENCE_INSTANCE_NAME);
        this.retry = retryRegistry.retry(RESILIENCE_INSTANCE_NAME);
    }

    @Override
    public ProductClientResponse getProduct(Long productId) {
        Supplier<ProductClientResponse> call = () -> doGetProduct(productId);
        Supplier<ProductClientResponse> resilient =
                CircuitBreaker.decorateSupplier(circuitBreaker, Retry.decorateSupplier(retry, call));

        try {
            return resilient.get();
        } catch (ProductNotFoundException ex) {
            // doGetProduct already translated this from a 404 — surfacing it
            // unchanged is the whole point of Stage 0's "404 is not a
            // service-health signal" decision. It never even reaches
            // Retry/CircuitBreaker's exception-counting logic below because
            // ProductNotFoundException isn't in either's record/retry list
            // (see application.properties resilience4j.*.instances.productClient) —
            // this catch is just where it surfaces to the caller.
            throw ex;
        } catch (Exception ex) {
            // Everything else — a real timeout/connection failure
            // (ResourceAccessException), a 5xx (HttpServerErrorException),
            // retries exhausted, or io.github.resilience4j.circuitbreaker.CallNotPermittedException
            // when the breaker is open — all mean the same thing to a caller:
            // product-service could not give a real answer right now.
            log.warn("product-service call failed for product id {}: {}", productId, ex.getMessage());
            throw new ProductServiceUnavailableException(productId, ex);
        }
    }

    private ProductClientResponse doGetProduct(Long productId) {
        try {
            return productServiceRestClient.get()
                    .uri("/products/{id}", productId)
                    .retrieve()
                    .body(ProductClientResponse.class);
        } catch (HttpClientErrorException.NotFound ex) {
            // Translated here, BEFORE this exception ever reaches the
            // Retry/CircuitBreaker decorators wrapping this supplier in
            // getProduct() above — see the class Javadoc.
            throw new ProductNotFoundException(productId);
        }
        // Any other HttpClientErrorException (4xx other than 404),
        // HttpServerErrorException (5xx), or ResourceAccessException
        // (timeout/connection failure) propagates as-is — resilience4j's
        // instance config (application.properties) decides which of those
        // count as retryable / circuit-breaking failures.
    }
}
