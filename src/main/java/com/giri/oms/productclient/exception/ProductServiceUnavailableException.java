package com.giri.oms.productclient.exception;

import com.giri.oms.common.exception.ErrorCode;
import com.giri.oms.common.exception.ErrorCoded;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

/**
 * Thrown by ProductClientImpl for everything that means "product-service
 * could not be reached or did not answer in time" — a timeout, a 5xx
 * response, or the circuit breaker being open (see
 * productclient.config.ProductClientConfig and the resilience4j.* properties
 * it's configured from). Deliberately distinct from
 * {@link com.giri.oms.productclient.exception.ProductNotFoundException}, which
 * ProductClientImpl throws instead for a 404 — that's a legitimate business
 * rejection ("this product id doesn't exist"), not a service-health problem,
 * and per Stage 0 of the microservices-prep plan's resilience decision, the
 * two must stay distinguishable to the caller (and to the circuit breaker
 * itself — a 404 must never count as a failure toward opening the circuit).
 */
@ResponseStatus(value = HttpStatus.SERVICE_UNAVAILABLE)
public class ProductServiceUnavailableException extends RuntimeException implements ErrorCoded {

    public ProductServiceUnavailableException(Long productId, Throwable cause) {
        super(ErrorCode.PRODUCT_SERVICE_UNAVAILABLE.formatMessage(productId), cause);
    }

    @Override
    public ErrorCode getErrorCode() {
        return ErrorCode.PRODUCT_SERVICE_UNAVAILABLE;
    }
}
