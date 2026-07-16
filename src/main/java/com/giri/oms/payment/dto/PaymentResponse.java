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

    @Schema(description = "Unique payment ID", example = "1")
    private Long id;

    @Schema(description = "ID of the order this payment is for", example = "1")
    private Long orderId;

    @Schema(description = "Amount paid", example = "77.97")
    private BigDecimal amount;

    @Schema(description = "Method used to make the payment", example = "CREDIT_CARD")
    private PaymentMethod method;

    @Schema(description = "Current status of the payment", example = "PENDING")
    private PaymentStatus status;

    @Schema(description = "Gateway/processor reference, populated once the payment is confirmed", example = "txn_9f8c3d2a")
    private String transactionReference;

    @Schema(description = "Timestamp the payment was created", example = "2026-07-01T10:15:30")
    private LocalDateTime createdAt;

    @Schema(description = "Timestamp the payment was last updated", example = "2026-07-01T10:16:05")
    private LocalDateTime updatedAt;
}
