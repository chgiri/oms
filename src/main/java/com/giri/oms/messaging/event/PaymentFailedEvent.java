package com.giri.oms.messaging.event;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Published by the payment module (see PaymentServiceImpl.updatePaymentStatus)
 * when a payment transitions to FAILED. Consumed by OrderSagaEventConsumer to
 * move the order from AWAITING_PAYMENT to CANCELLED (Phase 4), which in turn
 * triggers OrderCancelledEvent to release the inventory reserved in Phase 2.
 */
public record PaymentFailedEvent(
        UUID eventId,
        Long orderId,
        Long paymentId,
        LocalDateTime occurredAt
) {
}