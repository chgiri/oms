package com.giri.oms.product.exception;

import com.giri.oms.product.constants.ProductConstants;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

@ResponseStatus(value = HttpStatus.NOT_FOUND)
public class ProductNotFoundException extends RuntimeException {

    public ProductNotFoundException(Long id) {
        super(String.format(ProductConstants.PRODUCT_NOT_FOUND_MESSAGE, id));
    }

}
