package com.giri.oms.messaging.event;

import com.giri.oms.messaging.config.KafkaAppProperties;
import com.giri.oms.order.entity.Order;
import com.giri.oms.order.entity.OrderItem;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Component
public class OrderCreatedEventFactory {

    private static final String ORDER_AGGREGATE_TYPE = "Order";

    private final KafkaAppProperties kafkaAppProperties;

    public OrderCreatedEventFactory(KafkaAppProperties kafkaAppProperties) {
        this.kafkaAppProperties = kafkaAppProperties;
    }

    public OrderCreatedEvent create(Order order, UUID eventId) {
        List<OrderCreatedEvent.OrderItemEvent> items = order.getItems().stream()
                .map(this::toItemEvent)
                .toList();

        return new OrderCreatedEvent(
                eventId,
                order.getId(),
                order.getCustomer().getId(),
                order.getStatus().name(),
                order.getTotalAmount(),
                items,
                LocalDateTime.now());
    }

    public String aggregateType() {
        return ORDER_AGGREGATE_TYPE;
    }

    public String topic() {
        return kafkaAppProperties.topics().orderEvents();
    }

    public String partitionKey(Order order) {
        return order.getId().toString();
    }

    public String aggregateId(Order order) {
        return order.getId().toString();
    }

    private OrderCreatedEvent.OrderItemEvent toItemEvent(OrderItem item) {
        return new OrderCreatedEvent.OrderItemEvent(
                item.getProduct().getId(),
                item.getProduct().getName(),
                item.getQuantity(),
                item.getUnitPrice(),
                item.getSubtotal());
    }
}
