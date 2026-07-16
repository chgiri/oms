package com.giri.oms.order.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@Schema(description = "A single line item within an order response")
public class OrderItemResponse {

    @Schema(description = "Unique order item ID", example = "1")
    private Long id;

    @Schema(description = "ID of the ordered product", example = "1")
    private Long productId;

    @Schema(description = "Name of the ordered product (convenience field, avoids a second lookup)", example = "Wireless Mouse")
    private String productName;

    @Schema(description = "Number of units ordered", example = "3")
    private int quantity;

    @Schema(description = "Unit price snapshotted at order time", example = "25.99")
    private BigDecimal unitPrice;

    @Schema(description = "quantity * unitPrice for this line item", example = "77.97")
    private BigDecimal subtotal;
}
