package com.giri.oms.messaging.event;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Published by the product module (see ProductServiceImpl.createProduct).
 * No consumer yet — this is Phase 1 of the microservices-prep plan, adding
 * the events before anything downstream needs them, same as V16's
 * name/price snapshots landed before the modules that read them were split
 * out. The Inventory read-replica consumer (product_ref table, Phase 1 step
 * 3) will be the first consumer of this and ProductUpdatedEvent.
 */
public record ProductCreatedEvent(
        UUID eventId,
        Long productId,
        String name,
        BigDecimal price,
        LocalDateTime occurredAt
) {
}
