package com.giri.oms.messaging.event;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Published by the payment module (see PaymentServiceImpl.updatePaymentStatus)
 * when a payment transitions to COMPLETED. Consumed by OrderSagaEventConsumer
 * to move the order from AWAITING_PAYMENT to CONFIRMED.
 */
public record PaymentConfirmedEvent(
        UUID eventId,
        Long orderId,
        Long paymentId,
        BigDecimal amount,
        String transactionReference,
        LocalDateTime occurredAt
) {
}
