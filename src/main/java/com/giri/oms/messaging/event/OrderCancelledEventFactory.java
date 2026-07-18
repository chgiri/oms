package com.giri.oms.messaging.event;

import com.giri.oms.messaging.config.KafkaAppProperties;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Builds the Phase 4 OrderCancelled event and its outbox routing metadata.
 * Kept on the same topic and aggregate/partition key convention as
 * OrderCreatedEventFactory/OrderConfirmedEventFactory — see the
 * topic-strategy note on OrderSagaEventConsumer.
 */
@Component
public class OrderCancelledEventFactory {

    private static final String ORDER_AGGREGATE_TYPE = "Order";

    private final KafkaAppProperties kafkaAppProperties;

    public OrderCancelledEventFactory(KafkaAppProperties kafkaAppProperties) {
        this.kafkaAppProperties = kafkaAppProperties;
    }

    public OrderCancelledEvent cancelled(Long orderId, UUID eventId) {
        return new OrderCancelledEvent(eventId, orderId, LocalDateTime.now());
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
