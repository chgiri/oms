package com.giri.oms.payment.dto;

import com.giri.oms.payment.constants.PaymentConstants;
import com.giri.oms.payment.entity.PaymentStatus;
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
@Schema(description = "Request payload for transitioning a payment's status")
public class PaymentStatusUpdateRequest {

    @Schema(description = "The status to transition the payment to", example = "COMPLETED", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotNull(message = PaymentConstants.STATUS_REQUIRED_MESSAGE)
    private PaymentStatus status;

    // Optional — typically supplied when transitioning to COMPLETED, to record the
    // gateway/processor's own reference for this charge. Left null, an existing
    // transactionReference on the payment (if any) is left untouched.
    @Schema(description = "Gateway/processor reference to record alongside this transition", example = "txn_9f8c3d2a")
    private String transactionReference;
}
