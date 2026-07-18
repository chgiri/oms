package com.giri.oms.messaging.event;

public final class EventType {

    public static final String ORDER_CREATED = "OrderCreated";

    // Phase 2 outcomes, published by the inventory module once it has processed
    // an OrderCreated event — consumed by OrderSagaEventConsumer to move the
    // order to AWAITING_PAYMENT or CANCELLED.
    public static final String INVENTORY_RESERVED = "InventoryReserved";
    public static final String INVENTORY_RESERVATION_FAILED = "InventoryReservationFailed";

    private EventType() {
    }
}
