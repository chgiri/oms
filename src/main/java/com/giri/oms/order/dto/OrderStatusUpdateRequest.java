package com.giri.oms.order.dto;

import com.giri.oms.order.constants.OrderConstants;
import com.giri.oms.order.entity.OrderStatus;
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
@Schema(description = "Request payload for transitioning an order's status")
public class OrderStatusUpdateRequest {

    @Schema(description = "The status to transition the order to", example = "CONFIRMED", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotNull(message = OrderConstants.STATUS_REQUIRED_MESSAGE)
    private OrderStatus status;
}
