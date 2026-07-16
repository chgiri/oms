package com.giri.oms.shipment.dto;

import com.giri.oms.shipment.entity.ShipmentStatus;
import com.giri.oms.shipment.entity.ShippingCarrier;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@Schema(description = "Shipment details returned by the API")
public class ShipmentResponse {

    @Schema(description = "Unique shipment ID", example = "1")
    private Long id;

    @Schema(description = "ID of the order this shipment is for", example = "1")
    private Long orderId;

    @Schema(description = "Carrier delivering this shipment", example = "UPS")
    private ShippingCarrier carrier;

    @Schema(description = "Current status of the shipment", example = "PENDING")
    private ShipmentStatus status;

    @Schema(description = "Carrier-assigned tracking number, populated once the shipment ships", example = "1Z999AA10123456784")
    private String trackingNumber;

    @Schema(description = "Timestamp the shipment was handed to the carrier", example = "2026-07-02T09:00:00")
    private LocalDateTime shippedAt;

    @Schema(description = "Timestamp the shipment was delivered", example = "2026-07-05T14:30:00")
    private LocalDateTime deliveredAt;

    @Schema(description = "Timestamp the shipment record was created", example = "2026-07-01T10:15:30")
    private LocalDateTime createdAt;

    @Schema(description = "Timestamp the shipment record was last updated", example = "2026-07-05T14:30:00")
    private LocalDateTime updatedAt;
}
