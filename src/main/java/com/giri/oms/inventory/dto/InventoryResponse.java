package com.giri.oms.inventory.dto;

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
@Schema(description = "Inventory record returned by the API")
public class InventoryResponse {

    @Schema(description = "Unique inventory record ID", example = "1")
    private Long id;

    @Schema(description = "ID of the associated product", example = "1")
    private Long productId;

    @Schema(description = "Name of the associated product (convenience field, avoids a second lookup)", example = "Wireless Mouse")
    private String productName;

    @Schema(description = "Warehouse or location code", example = "WH-EAST-01")
    private String location;

    @Schema(description = "Units physically available at this location", example = "120")
    private int quantityAvailable;

    @Schema(description = "Units reserved for pending orders at this location", example = "15")
    private int quantityReserved;

    @Schema(description = "Threshold below which this location is considered low on stock", example = "20")
    private int reorderLevel;

    @Schema(description = "Timestamp the record was created", example = "2026-07-01T10:15:30")
    private LocalDateTime createdAt;

    @Schema(description = "Timestamp the record was last updated", example = "2026-07-10T08:42:11")
    private LocalDateTime updatedAt;
}
