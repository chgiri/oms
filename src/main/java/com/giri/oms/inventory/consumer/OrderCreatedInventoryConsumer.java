package com.giri.oms.inventory.consumer;

import com.giri.oms.inventory.exception.InsufficientStockException;
import com.giri.oms.inventory.service.InventoryReservationService;
import com.giri.oms.messaging.event.EventType;
import com.giri.oms.messaging.event.InventoryReservationEventFactory;
import com.giri.oms.messaging.event.InventoryReservationFailedEvent;
import com.giri.oms.messaging.event.OrderCancelledEvent;
import com.giri.oms.messaging.event.OrderCreatedEvent;
import com.giri.oms.messaging.outbox.OutboxService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.stereotype.Component;
import tools.jackson.databind.json.JsonMapper;

import java.util.UUID;

/**
 * Reacts to two order-lifecycle events for the inventory module: OrderCreated
 * (Phase 2 — reserves stock, reports InventoryReserved/InventoryReservationFailed
 * back onto the same topic for OrderSagaEventConsumer to act on) and
 * OrderCancelled (Phase 4 — releases whatever stock was reserved for that
 * order, a no-op if nothing was).
 *
 * Delivery/consistency notes:
 * - At-least-once delivery: Kafka may redeliver a message (rebalance, retry after
 *   a transient failure, consumer restart before offset commit). Both reservation
 *   and release are made idempotent via InventoryReservationServiceImpl, not by
 *   anything here.
 * - Ordering: the outbox publishes with the order id as the partition key
 *   (see OrderCreatedEventFactory.partitionKey / OrderCancelledEventFactory.partitionKey),
 *   so all events for one order land on the same partition and are processed in
 *   order, and no two consumer instances in this group process the same order
 *   concurrently.
 * - Offset commit: with Spring Kafka's default ack mode, the offset only commits
 *   after this method returns normally. A business failure (e.g.
 *   InsufficientStockException) is caught here so it does NOT block the offset —
 *   see the class comment on the reasoning. An infrastructure failure (DB/Redis
 *   blip) is left to propagate so the configured error handler retries it and, if
 *   still failing after retries, routes it to the dead-letter topic instead of
 *   committing an offset for a message that was never actually processed
 *   (see KafkaConfig.kafkaErrorHandler).
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class OrderCreatedInventoryConsumer {

    private final InventoryReservationService inventoryReservationService;
    private final JsonMapper objectMapper;
    private final OutboxService outboxService;
    private final InventoryReservationEventFactory inventoryReservationEventFactory;

    @KafkaListener(
            topics = "${app.kafka.topics.order-events}",
            groupId = "${spring.kafka.consumer.group-id}")
    public void onMessage(
            ConsumerRecord<String, String> record,
            @Header(name = "eventType", required = false) String eventType) {

        if (EventType.ORDER_CREATED.equals(eventType)) {
            OrderCreatedEvent event = objectMapper.readValue(record.value(), OrderCreatedEvent.class);
            log.debug("Received OrderCreated event id={} for order id={}", event.eventId(), event.orderId());
            reserve(event);
        } else if (EventType.ORDER_CANCELLED.equals(eventType)) {
            OrderCancelledEvent event = objectMapper.readValue(record.value(), OrderCancelledEvent.class);
            log.debug("Received OrderCancelled event id={} for order id={}", event.eventId(), event.orderId());
            inventoryReservationService.releaseForOrder(event);
        } else {
            // Not one of the event types this consumer cares about — the other
            // order-lifecycle events (PaymentConfirmed/PaymentFailed/OrderConfirmed)
            // belong to the order-saga and shipment consumer groups.
            log.debug("Ignoring event of type {} on order-events topic (key={})", eventType, record.key());
        }
    }

    private void reserve(OrderCreatedEvent event) {
        try {
            inventoryReservationService.reserveForOrder(event);
        } catch (InsufficientStockException ex) {
            // Business failure, not an infrastructure one — retrying won't produce
            // more stock. Swallow it here (offset still commits) rather than let the
            // error handler retry a message that can never succeed.
            //
            // reserveForOrder's own transaction already rolled back (no partial
            // reservation is left behind), so this enqueue happens in its own,
            // separate transaction via a plain outboxService call — there's
            // nothing left from the failed attempt to be atomic with.
            log.warn("Could not reserve inventory for order id={}: {}", event.orderId(), ex.getMessage());
            enqueueReservationFailedEvent(event.orderId(), ex.getMessage());
        }
    }

    private void enqueueReservationFailedEvent(Long orderId, String reason) {
        UUID eventId = UUID.randomUUID();
        InventoryReservationFailedEvent failedEvent =
                inventoryReservationEventFactory.failed(orderId, eventId, reason);
        outboxService.enqueue(
                eventId,
                inventoryReservationEventFactory.aggregateType(),
                inventoryReservationEventFactory.aggregateId(orderId),
                EventType.INVENTORY_RESERVATION_FAILED,
                inventoryReservationEventFactory.topic(),
                inventoryReservationEventFactory.partitionKey(orderId),
                failedEvent);
    }
}