package com.giri.oms.order.dto;

import com.giri.oms.order.entity.OrderStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@Schema(description = "Order details returned by the API")
public class OrderResponse {

    @Schema(description = "Unique order ID", example = "1", requiredMode = Schema.RequiredMode.REQUIRED)
    private Long id;

    @Schema(description = "ID of the customer who placed the order", example = "1", requiredMode = Schema.RequiredMode.REQUIRED)
    private Long customerId;

    @Schema(description = "Name of the customer who placed the order (convenience field, avoids a second lookup)", example = "Ada Lovelace",
            requiredMode = Schema.RequiredMode.REQUIRED)
    private String customerName;

    @Schema(description = "Current status of the order", example = "PENDING", requiredMode = Schema.RequiredMode.REQUIRED)
    private OrderStatus status;

    @Schema(description = "Sum of all line item subtotals", example = "155.94", requiredMode = Schema.RequiredMode.REQUIRED)
    private BigDecimal totalAmount;

    @Schema(description = "Line items in this order", requiredMode = Schema.RequiredMode.REQUIRED)
    private List<OrderItemResponse> items;

    @Schema(description = "Timestamp the order was created", example = "2026-07-01T10:15:30", requiredMode = Schema.RequiredMode.REQUIRED)
    private LocalDateTime createdAt;

    @Schema(description = "Timestamp the order was last updated", example = "2026-07-10T08:42:11", requiredMode = Schema.RequiredMode.REQUIRED)
    private LocalDateTime updatedAt;
}
