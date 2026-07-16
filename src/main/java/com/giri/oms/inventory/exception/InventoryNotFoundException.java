package com.giri.oms.inventory.exception;

import com.giri.oms.inventory.constants.InventoryConstants;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

@ResponseStatus(value = HttpStatus.NOT_FOUND)
public class InventoryNotFoundException extends RuntimeException {

    public InventoryNotFoundException(Long id) {
        super(String.format(InventoryConstants.INVENTORY_NOT_FOUND_MESSAGE, id));
    }
}
