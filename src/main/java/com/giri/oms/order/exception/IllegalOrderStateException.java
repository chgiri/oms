package com.giri.oms.order.exception;

import com.giri.oms.common.exception.ErrorCode;
import com.giri.oms.common.exception.ErrorCoded;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

/**
 * Thrown for any operation that conflicts with an order's current status —
 * an illegal status transition, or deleting an order that's already past the
 * point where deletion makes sense.
 */
@ResponseStatus(value = HttpStatus.CONFLICT)
public class IllegalOrderStateException extends RuntimeException implements ErrorCoded {

    public IllegalOrderStateException(String message) {
        super(message);
    }

    @Override
    public ErrorCode getErrorCode() {
        return ErrorCode.ILLEGAL_ORDER_STATE;
    }
}
