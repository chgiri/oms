package com.giri.oms.inventory.dto;

import com.giri.oms.inventory.constants.InventoryConstants;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@Schema(description = "Request payload for creating or updating an inventory record")
public class InventoryRequest {

    @Schema(description = "ID of the product this stock record belongs to", example = "1", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotNull(message = InventoryConstants.PRODUCT_ID_REQUIRED_MESSAGE)
    private Long productId;

    @Schema(description = "Warehouse or location code this stock is held at", example = "WH-EAST-01", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotBlank(message = InventoryConstants.LOCATION_REQUIRED_MESSAGE)
    @Size(max = 100, message = InventoryConstants.LOCATION_SIZE_MESSAGE)
    private String location;

    @Schema(description = "Units physically available at this location", example = "120", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotNull(message = InventoryConstants.QUANTITY_AVAILABLE_REQUIRED_MESSAGE)
    @PositiveOrZero(message = InventoryConstants.QUANTITY_AVAILABLE_POSITIVE_MESSAGE)
    private Integer quantityAvailable;

    @Schema(description = "Units reserved for pending orders at this location", example = "15", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotNull(message = InventoryConstants.QUANTITY_RESERVED_REQUIRED_MESSAGE)
    @PositiveOrZero(message = InventoryConstants.QUANTITY_RESERVED_POSITIVE_MESSAGE)
    private Integer quantityReserved;

    @Schema(description = "Threshold below which this location is considered low on stock", example = "20", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotNull(message = InventoryConstants.REORDER_LEVEL_REQUIRED_MESSAGE)
    @PositiveOrZero(message = InventoryConstants.REORDER_LEVEL_POSITIVE_MESSAGE)
    private Integer reorderLevel;
}
