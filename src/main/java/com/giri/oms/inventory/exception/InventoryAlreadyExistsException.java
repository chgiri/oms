package com.giri.oms.inventory.exception;

import com.giri.oms.common.exception.ErrorCode;
import com.giri.oms.common.exception.ErrorCoded;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

@ResponseStatus(value = HttpStatus.CONFLICT)
public class InventoryAlreadyExistsException extends RuntimeException implements ErrorCoded {

    public InventoryAlreadyExistsException(Long productId, String location) {
        super(ErrorCode.INVENTORY_ALREADY_EXISTS.formatMessage(productId, location));
    }

    @Override
    public ErrorCode getErrorCode() {
        return ErrorCode.INVENTORY_ALREADY_EXISTS;
    }
}
