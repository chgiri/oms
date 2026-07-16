package com.giri.oms.payment.dto;

import com.giri.oms.payment.constants.PaymentConstants;
import com.giri.oms.payment.entity.PaymentMethod;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@Schema(description = "Request payload for recording a new payment against an order")
public class PaymentRequest {

    @Schema(description = "ID of the order this payment is for", example = "1", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotNull(message = PaymentConstants.ORDER_ID_REQUIRED_MESSAGE)
    private Long orderId;

    @Schema(description = "Amount being paid", example = "77.97", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotNull(message = PaymentConstants.AMOUNT_REQUIRED_MESSAGE)
    @Positive(message = PaymentConstants.AMOUNT_POSITIVE_MESSAGE)
    private BigDecimal amount;

    @Schema(description = "Method used to make the payment", example = "CREDIT_CARD", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotNull(message = PaymentConstants.METHOD_REQUIRED_MESSAGE)
    private PaymentMethod method;
}
