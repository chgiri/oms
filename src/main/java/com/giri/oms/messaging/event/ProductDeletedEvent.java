package com.giri.oms.messaging.event;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Published by the product module (see ProductServiceImpl.deleteProduct).
 *
 * NOTE: deleteProduct is still a hard delete as of this event being added —
 * Product doesn't have soft-delete yet (that's Phase 1 step 2 of the
 * microservices-prep plan, not done in this change). A future replica
 * consumer reacting to this event today would be reacting to a delete that
 * Postgres's fk_inventory_product/fk_order_items_product constraints still
 * prevent whenever the product is actually referenced — so in practice this
 * only ever fires for genuinely unreferenced products until those FKs are
 * dropped in Phase 2, at which point soft-delete needs to already be in
 * place per that phase's data-integrity note.
 */
public record ProductDeletedEvent(
        UUID eventId,
        Long productId,
        LocalDateTime occurredAt
) {
}
