package com.giri.oms.shipment.service;

import com.giri.oms.messaging.event.OrderConfirmedEvent;

/**
 * Creates a shipment in response to an OrderConfirmed event (Phase 3 of the
 * Kafka rollout — see OrderConfirmedShipmentConsumer). Kept separate from
 * ShipmentService: that interface is CRUD over shipment records driven by
 * REST callers (including manual reships), this one is a single event-driven
 * use case with its own idempotency concern.
 */
public interface ShipmentAutoCreationService {

    /**
     * Creates the order's initial shipment, unless one already exists for it —
     * see the implementation for why that's the right idempotency check here.
     */
    void createForConfirmedOrder(OrderConfirmedEvent event);
}
