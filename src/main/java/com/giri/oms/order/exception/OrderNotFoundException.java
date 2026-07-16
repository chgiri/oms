package com.giri.oms.order.exception;

import com.giri.oms.order.constants.OrderConstants;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

@ResponseStatus(value = HttpStatus.NOT_FOUND)
public class OrderNotFoundException extends RuntimeException {

    public OrderNotFoundException(Long id) {
        super(String.format(OrderConstants.ORDER_NOT_FOUND_MESSAGE, id));
    }
}
