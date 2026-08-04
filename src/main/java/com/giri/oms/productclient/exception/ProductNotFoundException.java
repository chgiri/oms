package com.giri.oms.productclient.exception;

import com.giri.oms.common.exception.ErrorCode;
import com.giri.oms.common.exception.ErrorCoded;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

/**
 * Relocated here from {@code product.exception.ProductNotFoundException} as
 * part of Stage 5 (Phase 4 microservices-prep plan) — the {@code product}
 * package itself is gone, but this exception couldn't go with it: it's part
 * of {@link com.giri.oms.productclient.service.ProductClient}'s own
 * contract now, not the deleted module's.
 * {@link com.giri.oms.productclient.service.impl.ProductClientImpl} still
 * throws this on a 404 from product-service — see that class and
 * {@code ProductClient}'s Javadoc for why the not-found contract is
 * deliberately preserved unchanged across the Stage 4 swap. Same
 * {@code ErrorCode.PRODUCT_NOT_FOUND} (still {@code EPR100}, unchanged) —
 * moving the Java class doesn't change the wire-level error code a client
 * sees.
 */
@ResponseStatus(value = HttpStatus.NOT_FOUND)
public class ProductNotFoundException extends RuntimeException implements ErrorCoded {

    public ProductNotFoundException(Long id) {
        super(ErrorCode.PRODUCT_NOT_FOUND.formatMessage(id));
    }

    @Override
    public ErrorCode getErrorCode() {
        return ErrorCode.PRODUCT_NOT_FOUND;
    }
}
