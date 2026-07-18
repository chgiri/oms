package com.giri.oms.order.consumer;

import com.giri.oms.messaging.event.EventType;
import com.giri.oms.messaging.event.InventoryReservationFailedEvent;
import com.giri.oms.messaging.event.InventoryReservedEvent;
import com.giri.oms.order.entity.OrderStatus;
import com.giri.oms.order.exception.IllegalOrderStateException;
import com.giri.oms.order.service.OrderService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.stereotype.Component;
import tools.jackson.databind.json.JsonMapper;

/**
 * Drives the order's own status forward as its saga participants (currently
 * just inventory; payment and shipment join in Phase 3) report outcomes.
 *
 * <p>Topic strategy: every order-lifecycle event — OrderCreated, the Phase 2
 * outcomes handled here, and the Phase 3 events to come (PaymentConfirmed,
 * PaymentFailed, ShipmentCreated, ...) — stays on the single
 * {@code app.kafka.topics.order-events} topic, all keyed by order id. Kafka
 * only guarantees ordering within a partition, not across topics, and this
 * saga's correctness depends on each order's events being processed in the
 * order they happened (e.g. a stray InventoryReservationFailed must never be
 * allowed to arrive logically "after" a PaymentConfirmed for the same order).
 * Splitting into per-domain topics (payment-events, shipment-events, ...)
 * would mean reconstructing that ordering across topics by hand; staying on
 * one topic gets it for free. The {@code eventType} header (see
 * OutboxPublisher/OrderCreatedInventoryConsumer) is what lets each consumer
 * pick just the event types it cares about back out of that shared stream.
 *
 * <p>This consumer runs in its own consumer group (a different one from
 * OrderCreatedInventoryConsumer's), so both get their own full copy of every
 * message on the topic rather than competing for partitions — that's what
 * lets one topic serve multiple independent subscribers.
 *
 * <p>Idempotency: at-least-once delivery means a redelivered event is
 * expected, not exceptional. Rather than de-duplicating on the consumer side,
 * this relies on OrderService's own transition validation — a repeat delivery
 * finds the order has already left the state the transition was conditioned
 * on, IllegalOrderStateException is caught below, and the message is logged
 * and dropped rather than treated as a failure.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class OrderSagaEventConsumer {

    private final OrderService orderService;
    private final JsonMapper objectMapper;

    @KafkaListener(
            topics = "${app.kafka.topics.order-events}",
            groupId = "${app.kafka.consumer.order-service-group-id}")
    public void onMessage(
            ConsumerRecord<String, String> record,
            @Header(name = "eventType", required = false) String eventType) {

        if (EventType.INVENTORY_RESERVED.equals(eventType)) {
            InventoryReservedEvent event = objectMapper.readValue(record.value(), InventoryReservedEvent.class);
            log.debug("Received InventoryReserved event id={} for order id={}", event.eventId(), event.orderId());
            applyTransition(event.orderId(), OrderStatus.AWAITING_PAYMENT);
        } else if (EventType.INVENTORY_RESERVATION_FAILED.equals(eventType)) {
            InventoryReservationFailedEvent event =
                    objectMapper.readValue(record.value(), InventoryReservationFailedEvent.class);
            log.debug("Received InventoryReservationFailed event id={} for order id={}: {}",
                    event.eventId(), event.orderId(), event.reason());
            applyTransition(event.orderId(), OrderStatus.CANCELLED);
        } else {
            // Not one of the event types this consumer cares about — OrderCreated,
            // and the Phase 3 events to come, are handled by other consumer groups
            // on this same topic.
            log.debug("Ignoring event of type {} on order-events topic (key={})", eventType, record.key());
        }
    }

    private void applyTransition(Long orderId, OrderStatus newStatus) {
        try {
            orderService.updateOrderStatus(orderId, newStatus);
        } catch (IllegalOrderStateException ex) {
            // Most likely a redelivery of an event this consumer already applied —
            // the order has already moved past the status this transition assumed.
            // Not a failure worth retrying or dead-lettering.
            log.info("Skipping status transition for order id={} to {} — {}", orderId, newStatus, ex.getMessage());
        }
    }
}
