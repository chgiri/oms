package com.giri.oms.messaging.event;

public final class EventType {

    public static final String ORDER_CREATED = "OrderCreated";

    // Phase 2 outcomes, published by the inventory module once it has processed
    // an OrderCreated event — consumed by OrderSagaEventConsumer to move the
    // order to AWAITING_PAYMENT or CANCELLED.
    public static final String INVENTORY_RESERVED = "InventoryReserved";
    public static final String INVENTORY_RESERVATION_FAILED = "InventoryReservationFailed";

    // Phase 3 (happy path): payment confirms, order confirms, shipment is created.
    // PaymentFailed is published now (mirroring Phase 1's "publish now, react
    // later" approach) but nothing consumes it yet — the order moving to
    // CANCELLED in response is Phase 4's compensating flow.
    public static final String PAYMENT_CONFIRMED = "PaymentConfirmed";
    public static final String PAYMENT_FAILED = "PaymentFailed";
    public static final String ORDER_CONFIRMED = "OrderConfirmed";

    private EventType() {
    }
}