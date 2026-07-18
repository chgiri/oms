package com.giri.oms.payment.exception;

import com.giri.oms.common.exception.ErrorCode;
import com.giri.oms.common.exception.ErrorCoded;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

/**
 * Thrown for any operation that conflicts with a payment's current status —
 * an illegal status transition, or deleting a payment that's already past the
 * point where deletion makes sense.
 */
@ResponseStatus(value = HttpStatus.CONFLICT)
public class IllegalPaymentStateException extends RuntimeException implements ErrorCoded {

    public IllegalPaymentStateException(String message) {
        super(message);
    }

    @Override
    public ErrorCode getErrorCode() {
        return ErrorCode.ILLEGAL_PAYMENT_STATE;
    }
}
