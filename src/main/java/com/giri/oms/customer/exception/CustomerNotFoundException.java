package com.giri.oms.customer.exception;

import com.giri.oms.customer.constants.CustomerConstants;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

@ResponseStatus(value = HttpStatus.NOT_FOUND)
public class CustomerNotFoundException extends RuntimeException {

    public CustomerNotFoundException(Long id) {
        super(String.format(CustomerConstants.CUSTOMER_NOT_FOUND_MESSAGE, id));
    }
}