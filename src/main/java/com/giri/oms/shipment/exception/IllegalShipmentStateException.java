package com.giri.oms.shipment.exception;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

/**
 * Thrown for any operation that conflicts with a shipment's current status —
 * an illegal status transition, or deleting a shipment that's already past the
 * point where deletion makes sense.
 */
@ResponseStatus(value = HttpStatus.CONFLICT)
public class IllegalShipmentStateException extends RuntimeException {

    public IllegalShipmentStateException(String message) {
        super(message);
    }
}
