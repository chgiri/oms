package com.giri.oms.shipment.mapper;

import com.giri.oms.shipment.dto.ShipmentResponse;
import com.giri.oms.shipment.entity.Shipment;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring")
public interface ShipmentMapper {

    ShipmentResponse mapToShipmentResponse(Shipment shipment);

    // Shipment is intentionally NOT built from ShipmentRequest here — validating the
    // order requires a call to OrderService, which is business logic that belongs
    // in the service layer, not the mapper.
}