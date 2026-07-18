package com.giri.oms.inventory.consumer;

import com.giri.oms.inventory.exception.InsufficientStockException;
import com.giri.oms.inventory.service.InventoryReservationService;
import com.giri.oms.messaging.event.EventType;
import com.giri.oms.messaging.event.OrderCreatedEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.stereotype.Component;
import tools.jackson.databind.json.JsonMapper;

/**
 * Phase 2 of the Kafka rollout: reserves inventory in response to OrderCreated
 * events published via the outbox in Phase 1.
 *
 * Delivery/consistency notes:
 * - At-least-once delivery: Kafka may redeliver a message (rebalance, retry after
 *   a transient failure, consumer restart before offset commit). Reservation is
 *   made idempotent via InventoryReservationServiceImpl, not by anything here.
 * - Ordering: the outbox publishes with the order id as the partition key
 *   (see OrderCreatedEventFactory.partitionKey), so all events for one order land
 *   on the same partition and are processed in order, and no two consumer
 *   instances in this group process the same order concurrently.
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

    @KafkaListener(
            topics = "${app.kafka.topics.order-events}",
            groupId = "${spring.kafka.consumer.group-id}")
    public void onMessage(
            ConsumerRecord<String, String> record,
            @Header(name = "eventType", required = false) String eventType) {

        // The order-events topic is expected to carry more than just OrderCreated
        // once Phase 3/4 land (e.g. PaymentConfirmed, OrderCancelled). The outbox
        // stamps every message with an "eventType" header (see OutboxPublisher), so
        // this filters to the one type this consumer cares about instead of trying
        // (and failing) to parse every message on the topic as an OrderCreatedEvent.
        if (!EventType.ORDER_CREATED.equals(eventType)) {
            log.debug("Ignoring event of type {} on order-events topic (key={})", eventType, record.key());
            return;
        }

        OrderCreatedEvent event = objectMapper.readValue(record.value(), OrderCreatedEvent.class);
        log.debug("Received OrderCreated event id={} for order id={}", event.eventId(), event.orderId());

        try {
            inventoryReservationService.reserveForOrder(event);
        } catch (InsufficientStockException ex) {
            // Business failure, not an infrastructure one — retrying won't produce
            // more stock. Swallow it here (offset still commits) rather than let the
            // error handler retry a message that can never succeed. There's
            // deliberately no compensating action yet — Phase 3/4 introduces the
            // saga machinery (e.g. an InventoryReservationFailed event driving the
            // order to CANCELLED) that reacts to exactly this case.
            log.warn("Could not reserve inventory for order id={}: {}", event.orderId(), ex.getMessage());
        }
    }
}
