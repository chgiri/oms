package com.giri.oms.messaging.event;

public final class EventType {

    public static final String ORDER_CREATED = "OrderCreated";

    // Phase 2 outcomes, published by the inventory module once it has processed
    // an OrderCreated event — consumed by OrderSagaEventConsumer to move the
    // order to AWAITING_PAYMENT or CANCELLED.
    public static final String INVENTORY_RESERVED = "InventoryReserved";
    public static final String INVENTORY_RESERVATION_FAILED = "InventoryReservationFailed";

    // Phase 3 (happy path): payment confirms, order confirms, shipment is created.
    public static final String PAYMENT_CONFIRMED = "PaymentConfirmed";
    // Phase 4 consumes this too: OrderSagaEventConsumer reacts to PaymentFailed
    // by cancelling the order.
    public static final String PAYMENT_FAILED = "PaymentFailed";
    public static final String ORDER_CONFIRMED = "OrderConfirmed";

    // Phase 4: published whenever an order transitions to CANCELLED (whether
    // triggered by PaymentFailed above or a manual cancel via the REST API —
    // see OrderServiceImpl.updateOrderStatus). Consumed by the inventory
    // module's OrderCreatedInventoryConsumer to release any stock that had
    // been reserved for the order; a no-op if nothing was ever reserved (e.g.
    // an order cancelled while still PENDING).
    public static final String ORDER_CANCELLED = "OrderCancelled";

    private EventType() {
    }
}