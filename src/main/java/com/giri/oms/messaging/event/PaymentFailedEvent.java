package com.giri.oms.messaging.event;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Published by the payment module (see PaymentServiceImpl.updatePaymentStatus)
 * when a payment transitions to FAILED. Nothing consumes this yet — moving the
 * order to CANCELLED in response is Phase 4's compensating flow (it also needs
 * to release the inventory reserved in Phase 2, which this event alone doesn't
 * drive). Published now anyway so the event exists on the topic before Phase 4
 * needs it, the same way Phase 1 published OrderCreated before Phase 2 had a
 * consumer for it.
 */
public record PaymentFailedEvent(
        UUID eventId,
        Long orderId,
        Long paymentId,
        LocalDateTime occurredAt
) {
}
