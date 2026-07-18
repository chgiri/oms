package com.giri.oms.inventory.exception;

import com.giri.oms.common.exception.ErrorCode;
import com.giri.oms.common.exception.ErrorCoded;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

@ResponseStatus(value = HttpStatus.NOT_FOUND)
public class InventoryNotFoundException extends RuntimeException implements ErrorCoded {

    public InventoryNotFoundException(Long id) {
        super(ErrorCode.INVENTORY_NOT_FOUND.formatMessage(id));
    }

    @Override
    public ErrorCode getErrorCode() {
        return ErrorCode.INVENTORY_NOT_FOUND;
    }
}
