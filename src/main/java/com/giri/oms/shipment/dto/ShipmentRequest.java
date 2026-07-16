package com.giri.oms.shipment.dto;

import com.giri.oms.shipment.constants.ShipmentConstants;
import com.giri.oms.shipment.entity.ShippingCarrier;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@Schema(description = "Request payload for creating a new shipment for an order")
public class ShipmentRequest {

    @Schema(description = "ID of the order being shipped", example = "1", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotNull(message = ShipmentConstants.ORDER_ID_REQUIRED_MESSAGE)
    private Long orderId;

    @Schema(description = "Carrier that will deliver this shipment", example = "UPS", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotNull(message = ShipmentConstants.CARRIER_REQUIRED_MESSAGE)
    private ShippingCarrier carrier;
}
