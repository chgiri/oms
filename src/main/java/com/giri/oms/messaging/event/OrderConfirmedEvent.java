package com.giri.oms.messaging.event;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Published by the order module (see OrderServiceImpl.updateOrderStatus) when
 * an order transitions to CONFIRMED. Consumed by the shipment module's
 * OrderConfirmedShipmentConsumer to auto-create the order's shipment.
 */
public record OrderConfirmedEvent(
        UUID eventId,
        Long orderId,
        LocalDateTime occurredAt
) {
}
