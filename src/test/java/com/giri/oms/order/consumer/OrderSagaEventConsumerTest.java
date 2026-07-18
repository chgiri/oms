package com.giri.oms.order.consumer;

import com.giri.oms.messaging.event.EventType;
import com.giri.oms.messaging.event.InventoryReservationFailedEvent;
import com.giri.oms.messaging.event.InventoryReservedEvent;
import com.giri.oms.messaging.event.PaymentConfirmedEvent;
import com.giri.oms.messaging.event.PaymentFailedEvent;
import com.giri.oms.order.entity.OrderStatus;
import com.giri.oms.order.exception.IllegalOrderStateException;
import com.giri.oms.order.service.OrderService;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import tools.jackson.databind.json.JsonMapper;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

/**
 * Pure unit tests — no Spring context, no embedded Kafka. OrderService is
 * mocked and a real JsonMapper is used to (de)serialize event payloads, so
 * these exercise exactly what OrderSagaEventConsumer itself does: pick the
 * right event type off the shared order-events topic and drive the
 * corresponding order-status transition (see the class-level javadoc on
 * OrderSagaEventConsumer for the topic/ordering rationale).
 *
 * Phase 2 (InventoryReserved/InventoryReservationFailed) and Phase 3
 * (PaymentConfirmed) are covered here too since they go through the same
 * dispatch method as Phase 4 (PaymentFailed) — but the Phase 4 compensating
 * transition and its redelivery/idempotency behavior are the focus, since
 * that's what previously had no coverage at all.
 */
@ExtendWith(MockitoExtension.class)
class OrderSagaEventConsumerTest {

    @Mock
    private OrderService orderService;

    private JsonMapper objectMapper;
    private OrderSagaEventConsumer consumer;

    private static final Long ORDER_ID = 42L;
    private static final String TOPIC = "oms.order.events";

    @BeforeEach
    void setUp() {
        objectMapper = JsonMapper.builder().build();
        consumer = new OrderSagaEventConsumer(orderService, objectMapper);
    }

    @Test
    void inventoryReserved_movesOrderToAwaitingPayment() {
        InventoryReservedEvent event = new InventoryReservedEvent(UUID.randomUUID(), ORDER_ID, LocalDateTime.now());

        consumer.onMessage(record(event), EventType.INVENTORY_RESERVED, null);

        verify(orderService).updateOrderStatus(ORDER_ID, OrderStatus.AWAITING_PAYMENT);
    }

    @Test
    void inventoryReservationFailed_movesOrderToCancelled() {
        // Phase 4: a reservation failure (no stock available) is a compensating
        // trigger just like a failed payment — the order can't proceed.
        InventoryReservationFailedEvent event =
                new InventoryReservationFailedEvent(UUID.randomUUID(), ORDER_ID, "insufficient stock", LocalDateTime.now());

        consumer.onMessage(record(event), EventType.INVENTORY_RESERVATION_FAILED, null);

        verify(orderService).updateOrderStatus(ORDER_ID, OrderStatus.CANCELLED);
    }

    @Test
    void paymentConfirmed_movesOrderToConfirmed() {
        PaymentConfirmedEvent event = new PaymentConfirmedEvent(
                UUID.randomUUID(), ORDER_ID, 7L, new BigDecimal("50.00"), "txn-123", LocalDateTime.now());

        consumer.onMessage(record(event), EventType.PAYMENT_CONFIRMED, null);

        verify(orderService).updateOrderStatus(ORDER_ID, OrderStatus.CONFIRMED);
    }

    @Test
    void paymentFailed_movesOrderToCancelled_drivingPhase4Compensation() {
        // This is what makes OrderServiceImpl.updateOrderStatus enqueue
        // OrderCancelled, which the inventory module reacts to by releasing
        // the stock this order held (see OrderCreatedInventoryConsumer).
        PaymentFailedEvent event = new PaymentFailedEvent(UUID.randomUUID(), ORDER_ID, 7L, LocalDateTime.now());

        consumer.onMessage(record(event), EventType.PAYMENT_FAILED, null);

        verify(orderService).updateOrderStatus(ORDER_ID, OrderStatus.CANCELLED);
    }

    @Test
    void paymentFailed_swallowsIllegalOrderStateException_asARedeliveryNoOp() {
        // At-least-once delivery means a redelivered PaymentFailed is expected,
        // not exceptional — by the time it's redelivered the order has already
        // left AWAITING_PAYMENT, so the transition throws and the consumer must
        // log-and-drop rather than propagate (which would trigger a Kafka retry
        // and eventually a dead-letter for a message that was never a real failure).
        PaymentFailedEvent event = new PaymentFailedEvent(UUID.randomUUID(), ORDER_ID, 7L, LocalDateTime.now());
        doThrow(new IllegalOrderStateException("already cancelled"))
                .when(orderService).updateOrderStatus(ORDER_ID, OrderStatus.CANCELLED);

        consumer.onMessage(record(event), EventType.PAYMENT_FAILED, null);

        verify(orderService).updateOrderStatus(ORDER_ID, OrderStatus.CANCELLED);
        // No assertion beyond "didn't throw" is needed — the test itself failing
        // to complete would mean the exception propagated.
    }

    @Test
    void inventoryReservationFailed_swallowsIllegalOrderStateException_asARedeliveryNoOp() {
        InventoryReservationFailedEvent event =
                new InventoryReservationFailedEvent(UUID.randomUUID(), ORDER_ID, "insufficient stock", LocalDateTime.now());
        doThrow(new IllegalOrderStateException("already cancelled"))
                .when(orderService).updateOrderStatus(ORDER_ID, OrderStatus.CANCELLED);

        consumer.onMessage(record(event), EventType.INVENTORY_RESERVATION_FAILED, null);

        verify(orderService).updateOrderStatus(ORDER_ID, OrderStatus.CANCELLED);
    }

    @Test
    void ignoresOrderCreated_sinceThatBelongsToTheInventoryConsumerGroup() {
        consumer.onMessage(new ConsumerRecord<>(TOPIC, 0, 0L, ORDER_ID.toString(), "{}"), EventType.ORDER_CREATED, null);

        verifyNoInteractions(orderService);
    }

    @Test
    void ignoresUnknownOrMissingEventType() {
        consumer.onMessage(new ConsumerRecord<>(TOPIC, 0, 0L, ORDER_ID.toString(), "{}"), null, null);

        verify(orderService, never()).updateOrderStatus(anyLong(), any());
    }

    private ConsumerRecord<String, String> record(Object event) {
        String json = objectMapper.writeValueAsString(event);
        return new ConsumerRecord<>(TOPIC, 0, 0L, ORDER_ID.toString(), json);
    }
}
