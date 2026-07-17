package com.giri.oms.auth.exception;

import com.giri.oms.auth.constants.AuthConstants;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

@ResponseStatus(value = HttpStatus.CONFLICT)
public class UsernameAlreadyExistsException extends RuntimeException {

    public UsernameAlreadyExistsException(String username) {
        super(String.format(AuthConstants.USERNAME_ALREADY_EXISTS_MESSAGE, username));
    }
}
