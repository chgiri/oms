package com.giri.oms.order.entity;

/**
 * Order lifecycle, aligned with the Kafka saga phases:
 * <ul>
 *   <li>{@link #PENDING} — order recorded, OrderCreated enqueued to the outbox.
 *       No inventory has been reserved yet.</li>
 *   <li>{@link #AWAITING_PAYMENT} — Phase 2: inventory successfully reserved
 *       (see OrderSagaEventConsumer / InventoryReservedEvent). Waiting on payment.</li>
 *   <li>{@link #CONFIRMED} — Phase 3: payment confirmed for the order.</li>
 *   <li>{@link #SHIPPED} — a shipment has been dispatched.</li>
 *   <li>{@link #DELIVERED} — terminal success state.</li>
 *   <li>{@link #CANCELLED} — terminal failure/compensation state, reached either
 *       from PENDING (inventory couldn't be reserved) or AWAITING_PAYMENT
 *       (payment failed), as well as manual cancellation. See Phase 4.</li>
 * </ul>
 */
public enum OrderStatus {
    PENDING,
    AWAITING_PAYMENT,
    CONFIRMED,
    SHIPPED,
    DELIVERED,
    CANCELLED
}
