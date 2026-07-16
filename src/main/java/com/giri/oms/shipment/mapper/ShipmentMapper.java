package com.giri.oms.shipment.mapper;

import com.giri.oms.shipment.dto.ShipmentResponse;
import com.giri.oms.shipment.entity.Shipment;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring")
public interface ShipmentMapper {

    @Mapping(target = "orderId", source = "order.id")
    ShipmentResponse mapToShipmentResponse(Shipment shipment);

    // Shipment is intentionally NOT built from ShipmentRequest here — resolving the
    // Order requires a repository lookup (to validate it exists), which is business
    // logic that belongs in the service layer, not the mapper.
}
