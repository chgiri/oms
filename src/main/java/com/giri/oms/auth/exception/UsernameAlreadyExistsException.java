package com.giri.oms.auth.exception;

import com.giri.oms.common.exception.ErrorCode;
import com.giri.oms.common.exception.ErrorCoded;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

@ResponseStatus(value = HttpStatus.CONFLICT)
public class UsernameAlreadyExistsException extends RuntimeException implements ErrorCoded {

    public UsernameAlreadyExistsException(String username) {
        super(ErrorCode.USERNAME_ALREADY_EXISTS.formatMessage(username));
    }

    @Override
    public ErrorCode getErrorCode() {
        return ErrorCode.USERNAME_ALREADY_EXISTS;
    }
}
