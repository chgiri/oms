package com.giri.oms.order.dto;

import com.giri.oms.order.constants.OrderConstants;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@Schema(description = "A single line item within an order request")
public class OrderItemRequest {

    @Schema(description = "ID of the product being ordered", example = "1", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotNull(message = OrderConstants.ITEM_PRODUCT_ID_REQUIRED_MESSAGE)
    private Long productId;

    @Schema(description = "Number of units of this product being ordered", example = "3", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotNull(message = OrderConstants.ITEM_QUANTITY_REQUIRED_MESSAGE)
    @Positive(message = OrderConstants.ITEM_QUANTITY_POSITIVE_MESSAGE)
    private Integer quantity;
}
