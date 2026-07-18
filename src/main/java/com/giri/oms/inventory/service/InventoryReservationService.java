package com.giri.oms.inventory.service;

import com.giri.oms.messaging.event.OrderCancelledEvent;
import com.giri.oms.messaging.event.OrderCreatedEvent;

/**
 * Reserves stock in response to an OrderCreated event (Phase 2 of the Kafka
 * rollout — see OrderCreatedInventoryConsumer), and releases it back in
 * response to an OrderCancelled event (Phase 4's compensating flow). Kept
 * separate from InventoryService: that interface is CRUD over inventory
 * records driven by REST callers, this one is event-driven with its own
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

    /**
     * Releases every reservation held for this order back to available stock.
     * A no-op if nothing was ever reserved for the order (e.g. it was
     * cancelled while still PENDING) — deleting each reservation row as it's
     * released is also what makes a redelivery of the same OrderCancelled
     * event idempotent: the second delivery finds nothing left to release.
     */
    void releaseForOrder(OrderCancelledEvent event);
}