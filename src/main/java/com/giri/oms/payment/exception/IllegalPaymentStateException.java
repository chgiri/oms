package com.giri.oms.payment.exception;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

/**
 * Thrown for any operation that conflicts with a payment's current status —
 * an illegal status transition, or deleting a payment that's already past the
 * point where deletion makes sense.
 */
@ResponseStatus(value = HttpStatus.CONFLICT)
public class IllegalPaymentStateException extends RuntimeException {

    public IllegalPaymentStateException(String message) {
        super(message);
    }
}
