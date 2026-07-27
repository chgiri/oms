package com.giri.oms.messaging.event;

import com.giri.oms.messaging.config.KafkaAppProperties;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/**
 * Builds the OrderCreated event and its outbox routing metadata. Kept on the
 * same topic and aggregate/partition key convention as
 * OrderConfirmedEventFactory/OrderCancelledEventFactory — see the
 * topic-strategy note on OrderSagaEventConsumer.
 *
 * Takes only primitives/DTOs, never the order module's Order/OrderItem
 * entities — see ModularityTests. The order module (which owns those
 * entities) is responsible for unpacking an Order into the plain values this
 * factory needs (see OrderServiceImpl.enqueueOrderCreatedEvent), the same way
 * it already does for OrderConfirmedEventFactory/OrderCancelledEventFactory.
 */
@Component
public class OrderCreatedEventFactory {

    private static final String ORDER_AGGREGATE_TYPE = "Order";

    private final KafkaAppProperties kafkaAppProperties;
    private final Clock clock;

    public OrderCreatedEventFactory(KafkaAppProperties kafkaAppProperties, Clock clock) {
        this.kafkaAppProperties = kafkaAppProperties;
        this.clock = clock;
    }

    public OrderCreatedEvent create(Long orderId, Long customerId, String status, BigDecimal totalAmount,
                                    List<OrderCreatedEvent.OrderItemEvent> items, UUID eventId) {
        return new OrderCreatedEvent(
                eventId,
                orderId,
                customerId,
                status,
                totalAmount,
                items,
                LocalDateTime.now(clock));
    }

    public String aggregateType() {
        return ORDER_AGGREGATE_TYPE;
    }

    public String topic() {
        return kafkaAppProperties.topics().orderEvents();
    }

    public String partitionKey(Long orderId) {
        return orderId.toString();
    }

    public String aggregateId(Long orderId) {
        return orderId.toString();
    }
}