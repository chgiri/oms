package com.giri.oms.inventory.exception;

import com.giri.oms.inventory.constants.InventoryConstants;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

@ResponseStatus(value = HttpStatus.CONFLICT)
public class InventoryAlreadyExistsException extends RuntimeException {

    public InventoryAlreadyExistsException(Long productId, String location) {
        super(String.format(InventoryConstants.INVENTORY_ALREADY_EXISTS_MESSAGE, productId, location));
    }
}
