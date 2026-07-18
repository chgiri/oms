package com.giri.oms.messaging.event;

import com.giri.oms.messaging.config.KafkaAppProperties;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Builds the Phase 3 payment-outcome events and their outbox routing metadata.
 * Kept on the same topic and aggregate/partition key convention as
 * OrderCreatedEventFactory/InventoryReservationEventFactory — see the
 * topic-strategy note on OrderSagaEventConsumer for why every order-lifecycle
 * event stays on one topic keyed by order id, even ones raised by other
 * modules like this one.
 */
@Component
public class PaymentEventFactory {

    private static final String ORDER_AGGREGATE_TYPE = "Order";

    private final KafkaAppProperties kafkaAppProperties;

    public PaymentEventFactory(KafkaAppProperties kafkaAppProperties) {
        this.kafkaAppProperties = kafkaAppProperties;
    }

    public PaymentConfirmedEvent confirmed(
            Long orderId, Long paymentId, UUID eventId, BigDecimal amount, String transactionReference) {
        return new PaymentConfirmedEvent(eventId, orderId, paymentId, amount, transactionReference, LocalDateTime.now());
    }

    public PaymentFailedEvent failed(Long orderId, Long paymentId, UUID eventId) {
        return new PaymentFailedEvent(eventId, orderId, paymentId, LocalDateTime.now());
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