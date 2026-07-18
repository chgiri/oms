package com.giri.oms.inventory.consumer;

import com.giri.oms.inventory.exception.InsufficientStockException;
import com.giri.oms.inventory.service.InventoryReservationService;
import com.giri.oms.messaging.event.EventType;
import com.giri.oms.messaging.event.InventoryReservationEventFactory;
import com.giri.oms.messaging.event.InventoryReservationFailedEvent;
import com.giri.oms.messaging.event.OrderCancelledEvent;
import com.giri.oms.messaging.event.OrderCreatedEvent;
import com.giri.oms.messaging.outbox.OutboxService;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import tools.jackson.databind.json.JsonMapper;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * Pure unit tests — no Spring context, no embedded Kafka. InventoryReservationService,
 * OutboxService, and InventoryReservationEventFactory are all mocked; a real JsonMapper
 * (de)serializes the record payloads, the same way OrderSagaEventConsumerTest does.
 *
 * Covers both halves of this consumer: OrderCreated (Phase 2 reservation, plus the
 * InsufficientStockException -> InventoryReservationFailed path) and OrderCancelled
 * (Phase 4's compensating release) — the latter had no coverage at all before this.
 */
@ExtendWith(MockitoExtension.class)
class OrderCreatedInventoryConsumerTest {

    @Mock
    private InventoryReservationService inventoryReservationService;

    @Mock
    private OutboxService outboxService;

    @Mock
    private InventoryReservationEventFactory inventoryReservationEventFactory;

    private JsonMapper objectMapper;
    private OrderCreatedInventoryConsumer consumer;

    private static final Long ORDER_ID = 42L;
    private static final String TOPIC = "oms.order.events";

    @BeforeEach
    void setUp() {
        objectMapper = JsonMapper.builder().build();
        consumer = new OrderCreatedInventoryConsumer(
                inventoryReservationService, objectMapper, outboxService, inventoryReservationEventFactory);
    }

    @Test
    void orderCreated_reservesInventoryForTheOrder() {
        OrderCreatedEvent event = orderCreatedEvent();

        consumer.onMessage(record(event), EventType.ORDER_CREATED, null);

        verify(inventoryReservationService).reserveForOrder(any(OrderCreatedEvent.class));
        verifyNoInteractions(outboxService);
    }

    @Test
    void orderCreated_enqueuesReservationFailedEvent_whenStockIsInsufficient() {
        OrderCreatedEvent event = orderCreatedEvent();
        doThrow(new InsufficientStockException(1L, 5, 2))
                .when(inventoryReservationService).reserveForOrder(any(OrderCreatedEvent.class));
        when(inventoryReservationEventFactory.aggregateType()).thenReturn("Order");
        when(inventoryReservationEventFactory.aggregateId(ORDER_ID)).thenReturn(ORDER_ID.toString());
        when(inventoryReservationEventFactory.topic()).thenReturn(TOPIC);
        when(inventoryReservationEventFactory.partitionKey(ORDER_ID)).thenReturn(ORDER_ID.toString());
        when(inventoryReservationEventFactory.failed(eq(ORDER_ID), any(UUID.class), any(String.class)))
                .thenAnswer(invocation -> new InventoryReservationFailedEvent(
                        invocation.getArgument(1), ORDER_ID, invocation.getArgument(2), LocalDateTime.now()));

        consumer.onMessage(record(event), EventType.ORDER_CREATED, null);

        // Business failure — must not propagate and trigger a Kafka retry.
        ArgumentCaptor<Object> payload = ArgumentCaptor.forClass(Object.class);
        verify(outboxService).enqueue(
                any(UUID.class), eq("Order"), eq(ORDER_ID.toString()),
                eq(EventType.INVENTORY_RESERVATION_FAILED), eq(TOPIC), eq(ORDER_ID.toString()), payload.capture());
        assertThat(payload.getValue()).isInstanceOf(InventoryReservationFailedEvent.class);
        assertThat(((InventoryReservationFailedEvent) payload.getValue()).orderId()).isEqualTo(ORDER_ID);
    }

    @Test
    void orderCancelled_releasesReservedStockForTheOrder() {
        OrderCancelledEvent event = new OrderCancelledEvent(UUID.randomUUID(), ORDER_ID, LocalDateTime.now());

        consumer.onMessage(record(event), EventType.ORDER_CANCELLED, null);

        verify(inventoryReservationService).releaseForOrder(any(OrderCancelledEvent.class));
        verifyNoInteractions(outboxService);
        verify(inventoryReservationService, never()).reserveForOrder(any());
    }

    @Test
    void ignoresEventTypesThisConsumerDoesNotOwn() {
        // PaymentConfirmed/PaymentFailed/OrderConfirmed belong to the order-saga
        // and shipment consumer groups, not this one.
        consumer.onMessage(new ConsumerRecord<>(TOPIC, 0, 0L, ORDER_ID.toString(), "{}"), EventType.PAYMENT_CONFIRMED, null);

        verifyNoInteractions(inventoryReservationService, outboxService);
    }

    private OrderCreatedEvent orderCreatedEvent() {
        return new OrderCreatedEvent(
                UUID.randomUUID(), ORDER_ID, 5L, "PENDING", new BigDecimal("100.00"),
                List.of(new OrderCreatedEvent.OrderItemEvent(1L, "Widget", 3, new BigDecimal("10.00"), new BigDecimal("30.00"))),
                LocalDateTime.now());
    }

    private ConsumerRecord<String, String> record(Object event) {
        String json = objectMapper.writeValueAsString(event);
        return new ConsumerRecord<>(TOPIC, 0, 0L, ORDER_ID.toString(), json);
    }
}
