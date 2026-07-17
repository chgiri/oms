package com.giri.oms.auth.exception;

import com.giri.oms.auth.constants.AuthConstants;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

@ResponseStatus(value = HttpStatus.CONFLICT)
public class EmailAlreadyExistsException extends RuntimeException {

    public EmailAlreadyExistsException(String email) {
        super(String.format(AuthConstants.EMAIL_ALREADY_EXISTS_MESSAGE, email));
    }
}
