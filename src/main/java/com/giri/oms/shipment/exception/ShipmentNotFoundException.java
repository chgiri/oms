package com.giri.oms.shipment.exception;

import com.giri.oms.shipment.constants.ShipmentConstants;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

@ResponseStatus(value = HttpStatus.NOT_FOUND)
public class ShipmentNotFoundException extends RuntimeException {

    public ShipmentNotFoundException(Long id) {
        super(String.format(ShipmentConstants.SHIPMENT_NOT_FOUND_MESSAGE, id));
    }
}
