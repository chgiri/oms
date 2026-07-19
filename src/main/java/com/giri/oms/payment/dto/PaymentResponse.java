package com.giri.oms.payment.dto;

import com.giri.oms.payment.entity.PaymentMethod;
import com.giri.oms.payment.entity.PaymentStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@Schema(description = "Payment details returned by the API")
public class PaymentResponse {

    @Schema(description = "Unique payment ID", example = "1", requiredMode = Schema.RequiredMode.REQUIRED)
    private Long id;

    @Schema(description = "ID of the order this payment is for", example = "1", requiredMode = Schema.RequiredMode.REQUIRED)
    private Long orderId;

    @Schema(description = "Amount paid", example = "77.97", requiredMode = Schema.RequiredMode.REQUIRED)
    private BigDecimal amount;

    @Schema(description = "Method used to make the payment", example = "CREDIT_CARD", requiredMode = Schema.RequiredMode.REQUIRED)
    private PaymentMethod method;

    @Schema(description = "Current status of the payment", example = "PENDING", requiredMode = Schema.RequiredMode.REQUIRED)
    private PaymentStatus status;

    @Schema(description = "Gateway/processor reference — null until the payment is confirmed", example = "txn_9f8c3d2a",
            requiredMode = Schema.RequiredMode.REQUIRED, nullable = true)
    private String transactionReference;

    @Schema(description = "Timestamp the payment was created", example = "2026-07-01T10:15:30", requiredMode = Schema.RequiredMode.REQUIRED)
    private LocalDateTime createdAt;

    @Schema(description = "Timestamp the payment was last updated", example = "2026-07-01T10:16:05", requiredMode = Schema.RequiredMode.REQUIRED)
    private LocalDateTime updatedAt;
}
