package com.giri.oms.messaging.event;

import com.giri.oms.messaging.config.KafkaAppProperties;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Builds the OrderConfirmed event and its outbox routing metadata. Kept on the
 * same topic and aggregate/partition key convention as OrderCreatedEventFactory —
 * see the topic-strategy note on OrderSagaEventConsumer.
 */
@Component
public class OrderConfirmedEventFactory {

    private static final String ORDER_AGGREGATE_TYPE = "Order";

    private final KafkaAppProperties kafkaAppProperties;
    private final Clock clock;

    public OrderConfirmedEventFactory(KafkaAppProperties kafkaAppProperties, Clock clock) {
        this.kafkaAppProperties = kafkaAppProperties;
        this.clock = clock;
    }

    public OrderConfirmedEvent confirmed(Long orderId, UUID eventId) {
        return new OrderConfirmedEvent(eventId, orderId, LocalDateTime.now(clock));
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