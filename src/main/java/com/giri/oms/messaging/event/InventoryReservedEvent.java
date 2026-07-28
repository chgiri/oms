package com.giri.oms.messaging.event;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Published by the inventory module (see InventoryReservationServiceImpl) once
 * every line item on an order has been successfully reserved. Consumed by
 * OrderSagaEventConsumer to move the order from PENDING to AWAITING_PAYMENT.
 *
 * {@code schemaVersion} is {@link EventSchemaVersion#V1} for every event
 * published today — see that class for the compatibility policy this field
 * and every other field on this record are held to.
 */
public record InventoryReservedEvent(
        UUID eventId,
        Long orderId,
        LocalDateTime occurredAt,
        int schemaVersion
) {
}
