package com.giri.oms.messaging.event;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/**
 * {@code schemaVersion} is {@link EventSchemaVersion#V1} for every event
 * published today — see that class for the compatibility policy this field
 * and every other field on this record are held to.
 */
public record OrderCreatedEvent(
        UUID eventId,
        Long orderId,
        Long customerId,
        String status,
        BigDecimal totalAmount,
        List<OrderItemEvent> items,
        LocalDateTime occurredAt,
        int schemaVersion
) {

    public record OrderItemEvent(
            Long productId,
            String productName,
            int quantity,
            BigDecimal unitPrice,
            BigDecimal subtotal
    ) {
    }
}
