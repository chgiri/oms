package com.giri.oms.shipment.exception;

import com.giri.oms.common.exception.ErrorCode;
import com.giri.oms.common.exception.ErrorCoded;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

@ResponseStatus(value = HttpStatus.NOT_FOUND)
public class ShipmentNotFoundException extends RuntimeException implements ErrorCoded {

    public ShipmentNotFoundException(Long id) {
        super(ErrorCode.SHIPMENT_NOT_FOUND.formatMessage(id));
    }

    @Override
    public ErrorCode getErrorCode() {
        return ErrorCode.SHIPMENT_NOT_FOUND;
    }
}
