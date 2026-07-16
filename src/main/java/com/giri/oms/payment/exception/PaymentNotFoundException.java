package com.giri.oms.payment.exception;

import com.giri.oms.payment.constants.PaymentConstants;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

@ResponseStatus(value = HttpStatus.NOT_FOUND)
public class PaymentNotFoundException extends RuntimeException {

    public PaymentNotFoundException(Long id) {
        super(String.format(PaymentConstants.PAYMENT_NOT_FOUND_MESSAGE, id));
    }
}
