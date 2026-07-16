package com.giri.oms.order.dto;

import com.giri.oms.order.constants.OrderConstants;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.List;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@Schema(description = "Request payload for placing a new order")
public class OrderRequest {

    @Schema(description = "ID of the customer placing the order", example = "1", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotNull(message = OrderConstants.CUSTOMER_ID_REQUIRED_MESSAGE)
    private Long customerId;

    @Schema(description = "Line items in this order — at least one is required", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotEmpty(message = OrderConstants.ITEMS_REQUIRED_MESSAGE)
    @Valid
    private List<OrderItemRequest> items;
}
