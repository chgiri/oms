package com.giri.oms.messaging.event;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Published by the inventory module (see OrderCreatedInventoryConsumer) when an
 * order's inventory reservation fails — currently only for InsufficientStockException,
 * a business failure rather than an infrastructure one. Consumed by
 * OrderSagaEventConsumer to move the order from PENDING to CANCELLED.
 *
 * {@code reason} is a human-readable message for logging/support, not a machine-
 * parseable failure code — nothing downstream should branch on its contents.
 */
public record InventoryReservationFailedEvent(
        UUID eventId,
        Long orderId,
        String reason,
        LocalDateTime occurredAt
) {
}
