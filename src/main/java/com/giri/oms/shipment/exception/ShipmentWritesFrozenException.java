package com.giri.oms.shipment.exception;

import com.giri.oms.common.exception.ErrorCode;
import com.giri.oms.common.exception.ErrorCoded;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

/**
 * Stage 3 of the microservices-prep plan (Phase 4, Shipment extraction —
 * data cutover). Thrown by ShipmentServiceImpl's write methods
 * (create/updateStatus/delete) when {@code app.shipment.writes-frozen=true}
 * — the deliberate freeze this runbook uses to guarantee the row set copied
 * to shipment-service is complete and won't be missing anything written
 * after the copy started. See {@code docs/stage3-data-cutover-runbook-shipment.md}.
 * <p>
 * Also thrown by ShipmentAutoCreationServiceImpl for the same reason, on the
 * SAME flag — Shipment is the first of the three extracted modules with a
 * write path reachable from Kafka as well as REST, so one flag guards both.
 * This flag check is defense-in-depth, not the primary safeguard for the
 * Kafka side: the runbook's real guarantee there is stopping
 * OrderConfirmedShipmentConsumer's consumer group membership entirely before
 * shipment-service's own copy of that consumer starts (see the runbook) —
 * this check exists in case that step is ever missed.
 * <p>
 * Deliberately NOT reused for anything else — this is a narrow,
 * time-boxed maintenance-window signal, not a general "service busy"
 * response.
 */
@ResponseStatus(value = HttpStatus.SERVICE_UNAVAILABLE)
public class ShipmentWritesFrozenException extends RuntimeException implements ErrorCoded {

    public ShipmentWritesFrozenException() {
        super(ErrorCode.SHIPMENT_WRITES_FROZEN.formatMessage());
    }

    @Override
    public ErrorCode getErrorCode() {
        return ErrorCode.SHIPMENT_WRITES_FROZEN;
    }
}
