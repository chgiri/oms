package com.giri.oms.customerclient.exception;

import com.giri.oms.common.exception.ErrorCode;
import com.giri.oms.common.exception.ErrorCoded;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

/**
 * Thrown by CustomerClientImpl for everything that means "customer-service
 * could not be reached or did not answer in time" — same reasoning as
 * {@code productclient.exception.ProductServiceUnavailableException}.
 * Deliberately distinct from
 * {@link com.giri.oms.customerclient.exception.CustomerNotFoundException}, which
 * CustomerClientImpl throws instead for a 404 — a legitimate business
 * rejection, not a service-health problem, and the two must stay
 * distinguishable to the caller and to the circuit breaker (a 404 must never
 * count as a failure toward opening the circuit).
 */
@ResponseStatus(value = HttpStatus.SERVICE_UNAVAILABLE)
public class CustomerServiceUnavailableException extends RuntimeException implements ErrorCoded {

    public CustomerServiceUnavailableException(Long customerId, Throwable cause) {
        super(ErrorCode.CUSTOMER_SERVICE_UNAVAILABLE.formatMessage(customerId), cause);
    }

    @Override
    public ErrorCode getErrorCode() {
        return ErrorCode.CUSTOMER_SERVICE_UNAVAILABLE;
    }
}
