package com.giri.oms.customer.exception;

import com.giri.oms.common.exception.ErrorCode;
import com.giri.oms.common.exception.ErrorCoded;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

/**
 * Stage 3 of the microservices-prep plan (Phase 4, Customer extraction —
 * data cutover). Thrown by CustomerServiceImpl's write methods
 * (create/update/delete) when {@code app.customer.writes-frozen=true} — the
 * deliberate freeze this runbook uses to guarantee the row set copied to
 * customer-service is complete and won't be missing anything written after
 * the copy started. See {@code docs/stage3-data-cutover-runbook-customer.md}.
 * Mirrors {@code product.exception.ProductWritesFrozenException} exactly.
 * <p>
 * Deliberately NOT reused for anything else — this is a narrow,
 * time-boxed maintenance-window signal, not a general "service busy"
 * response.
 */
@ResponseStatus(value = HttpStatus.SERVICE_UNAVAILABLE)
public class CustomerWritesFrozenException extends RuntimeException implements ErrorCoded {

    public CustomerWritesFrozenException() {
        super(ErrorCode.CUSTOMER_WRITES_FROZEN.formatMessage());
    }

    @Override
    public ErrorCode getErrorCode() {
        return ErrorCode.CUSTOMER_WRITES_FROZEN;
    }
}
