package com.giri.oms.messaging.event;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Published by the product module (see ProductServiceImpl.createProduct).
 * Consumed by the inventory module's ProductEventInventoryConsumer (Phase 1
 * step 3) to upsert the product_ref read replica.
 *
 * {@code schemaVersion} is {@link EventSchemaVersion#V1} for every event
 * published today — see that class for the compatibility policy this field
 * and every other field on this record are held to.
 */
public record ProductCreatedEvent(
        UUID eventId,
        Long productId,
        String name,
        BigDecimal price,
        LocalDateTime occurredAt,
        int schemaVersion
) {
}
