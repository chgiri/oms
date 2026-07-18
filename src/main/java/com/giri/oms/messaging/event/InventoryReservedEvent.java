package com.giri.oms.messaging.event;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Published by the inventory module (see InventoryReservationServiceImpl) once
 * every line item on an order has been successfully reserved. Consumed by
 * OrderSagaEventConsumer to move the order from PENDING to AWAITING_PAYMENT.
 */
public record InventoryReservedEvent(
        UUID eventId,
        Long orderId,
        LocalDateTime occurredAt
) {
}
