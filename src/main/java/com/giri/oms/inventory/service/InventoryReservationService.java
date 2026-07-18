package com.giri.oms.inventory.service;

import com.giri.oms.messaging.event.OrderCreatedEvent;

/**
 * Reserves stock in response to an OrderCreated event (Phase 2 of the Kafka
 * rollout — see OrderCreatedInventoryConsumer). Kept separate from
 * InventoryService: that interface is CRUD over inventory records driven by
 * REST callers, this one is a single event-driven use case with its own
 * idempotency and locking concerns.
 */
public interface InventoryReservationService {

    /**
     * Reserves stock for every line item on the event. Already-reserved items
     * (from a redelivered event) are skipped rather than double-reserved.
     *
     * @throws com.giri.oms.inventory.exception.InsufficientStockException if any
     *         line item can't be fully covered by available stock — the whole
     *         reservation for this order is rolled back so an order is never
     *         left partially reserved.
     */
    void reserveForOrder(OrderCreatedEvent event);
}
