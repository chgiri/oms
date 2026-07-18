package com.giri.oms.customer.exception;

import com.giri.oms.common.exception.ErrorCode;
import com.giri.oms.common.exception.ErrorCoded;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

@ResponseStatus(value = HttpStatus.CONFLICT)
public class CustomerEmailAlreadyExistsException extends RuntimeException implements ErrorCoded {

    public CustomerEmailAlreadyExistsException(String email) {
        super(ErrorCode.CUSTOMER_EMAIL_ALREADY_EXISTS.formatMessage(email));
    }

    @Override
    public ErrorCode getErrorCode() {
        return ErrorCode.CUSTOMER_EMAIL_ALREADY_EXISTS;
    }
}
