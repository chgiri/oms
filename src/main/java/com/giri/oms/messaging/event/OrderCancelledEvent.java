package com.giri.oms.messaging.event;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Published by the order module (see OrderServiceImpl.updateOrderStatus) when
 * an order transitions to CANCELLED — whether that's PaymentFailed driving it
 * automatically or a manual cancel via the REST API. Consumed by the
 * inventory module's OrderCreatedInventoryConsumer to release any stock that
 * had been reserved for the order (Phase 4).
 *
 * Deliberately carries no detail about what was reserved: the release side
 * looks up the order's own InventoryReservation rows rather than trusting a
 * snapshot on this event, the same way OrderConfirmedEvent doesn't carry line
 * items either.
 */
public record OrderCancelledEvent(
        UUID eventId,
        Long orderId,
        LocalDateTime occurredAt
) {
}
