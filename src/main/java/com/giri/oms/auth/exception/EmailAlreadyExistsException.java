package com.giri.oms.auth.exception;

import com.giri.oms.common.exception.ErrorCode;
import com.giri.oms.common.exception.ErrorCoded;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

@ResponseStatus(value = HttpStatus.CONFLICT)
public class EmailAlreadyExistsException extends RuntimeException implements ErrorCoded {

    public EmailAlreadyExistsException(String email) {
        super(ErrorCode.EMAIL_ALREADY_EXISTS.formatMessage(email));
    }

    @Override
    public ErrorCode getErrorCode() {
        return ErrorCode.EMAIL_ALREADY_EXISTS;
    }
}
