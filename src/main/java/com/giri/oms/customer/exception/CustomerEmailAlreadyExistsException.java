package com.giri.oms.customer.exception;

import com.giri.oms.customer.constants.CustomerConstants;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

@ResponseStatus(value = HttpStatus.CONFLICT)
public class CustomerEmailAlreadyExistsException extends RuntimeException {

    public CustomerEmailAlreadyExistsException(String email) {
        super(String.format(CustomerConstants.CUSTOMER_EMAIL_ALREADY_EXISTS_MESSAGE, email));
    }
}