package com.giri.oms.customerclient.exception;

import com.giri.oms.common.exception.ErrorCode;
import com.giri.oms.common.exception.ErrorCoded;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

/**
 * Relocated here from {@code customer.exception.CustomerNotFoundException} as
 * part of Stage 5 (Phase 4 microservices-prep plan) — the {@code customer}
 * package itself is gone, but this exception couldn't go with it: it's part
 * of {@link com.giri.oms.customerclient.service.CustomerClient}'s own
 * contract now, not the deleted module's.
 * {@link com.giri.oms.customerclient.service.impl.CustomerClientImpl} still
 * throws this on a 404 from customer-service — see that class and
 * {@code CustomerClient}'s Javadoc for why the not-found contract is
 * deliberately preserved unchanged across the Stage 4 swap. Same
 * {@code ErrorCode.CUSTOMER_NOT_FOUND} (still {@code ECU100}, unchanged) —
 * moving the Java class doesn't change the wire-level error code a client
 * sees. Mirrors {@code productclient.exception.ProductNotFoundException}'s
 * identical relocation at Product's own Stage 5.
 */
@ResponseStatus(value = HttpStatus.NOT_FOUND)
public class CustomerNotFoundException extends RuntimeException implements ErrorCoded {

    public CustomerNotFoundException(Long id) {
        super(ErrorCode.CUSTOMER_NOT_FOUND.formatMessage(id));
    }

    @Override
    public ErrorCode getErrorCode() {
        return ErrorCode.CUSTOMER_NOT_FOUND;
    }
}
