package com.giri.oms.messaging.event;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Published by the payment module (see PaymentServiceImpl.updatePaymentStatus)
 * when a payment transitions to COMPLETED. Consumed by OrderSagaEventConsumer
 * to move the order from AWAITING_PAYMENT to CONFIRMED.
 *
 * {@code schemaVersion} is {@link EventSchemaVersion#V1} for every event
 * published today — see that class for the compatibility policy this field
 * and every other field on this record are held to.
 */
public record PaymentConfirmedEvent(
        UUID eventId,
        Long orderId,
        Long paymentId,
        BigDecimal amount,
        String transactionReference,
        LocalDateTime occurredAt,
        int schemaVersion
) {
}
