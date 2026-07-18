package com.giri.oms.payment.exception;

import com.giri.oms.common.exception.ErrorCode;
import com.giri.oms.common.exception.ErrorCoded;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

@ResponseStatus(value = HttpStatus.NOT_FOUND)
public class PaymentNotFoundException extends RuntimeException implements ErrorCoded {

    public PaymentNotFoundException(Long id) {
        super(ErrorCode.PAYMENT_NOT_FOUND.formatMessage(id));
    }

    @Override
    public ErrorCode getErrorCode() {
        return ErrorCode.PAYMENT_NOT_FOUND;
    }
}
