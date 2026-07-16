package com.giri.oms.shipment.dto;

import com.giri.oms.shipment.constants.ShipmentConstants;
import com.giri.oms.shipment.entity.ShipmentStatus;
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
@Schema(description = "Request payload for transitioning a shipment's status")
public class ShipmentStatusUpdateRequest {

    @Schema(description = "The status to transition the shipment to", example = "SHIPPED", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotNull(message = ShipmentConstants.STATUS_REQUIRED_MESSAGE)
    private ShipmentStatus status;

    // Optional — typically supplied when transitioning to SHIPPED, to record the
    // carrier's tracking number. Left null, an existing trackingNumber on the
    // shipment (if any) is left untouched.
    @Schema(description = "Carrier-assigned tracking number to record alongside this transition", example = "1Z999AA10123456784")
    private String trackingNumber;
}
