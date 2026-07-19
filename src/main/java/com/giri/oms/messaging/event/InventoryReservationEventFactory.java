package com.giri.oms.messaging.event;

import com.giri.oms.messaging.config.KafkaAppProperties;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Builds the Phase 2 outcome events and their outbox routing metadata. Kept on
 * the same topic and aggregate/partition key convention as
 * OrderCreatedEventFactory — see the topic-strategy note on OrderSagaEventConsumer
 * for why every order-lifecycle event stays on one topic keyed by order id.
 */
@Component
public class InventoryReservationEventFactory {

    private static final String ORDER_AGGREGATE_TYPE = "Order";

    private final KafkaAppProperties kafkaAppProperties;
    private final Clock clock;

    public InventoryReservationEventFactory(KafkaAppProperties kafkaAppProperties, Clock clock) {
        this.kafkaAppProperties = kafkaAppProperties;
        this.clock = clock;
    }

    public InventoryReservedEvent reserved(Long orderId, UUID eventId) {
        return new InventoryReservedEvent(eventId, orderId, LocalDateTime.now(clock));
    }

    public InventoryReservationFailedEvent failed(Long orderId, UUID eventId, String reason) {
        return new InventoryReservationFailedEvent(eventId, orderId, reason, LocalDateTime.now(clock));
    }

    public String aggregateType() {
        return ORDER_AGGREGATE_TYPE;
    }

    public String aggregateId(Long orderId) {
        return orderId.toString();
    }

    public String partitionKey(Long orderId) {
        return orderId.toString();
    }

    public String topic() {
        return kafkaAppProperties.topics().orderEvents();
    }
}
