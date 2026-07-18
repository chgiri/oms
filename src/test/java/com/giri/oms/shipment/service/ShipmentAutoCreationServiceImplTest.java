package com.giri.oms.shipment.service;

import com.giri.oms.messaging.event.OrderConfirmedEvent;
import com.giri.oms.order.entity.Order;
import com.giri.oms.order.exception.OrderNotFoundException;
import com.giri.oms.order.repository.OrderRepository;
import com.giri.oms.shipment.entity.Shipment;
import com.giri.oms.shipment.entity.ShipmentStatus;
import com.giri.oms.shipment.entity.ShippingCarrier;
import com.giri.oms.shipment.repository.ShipmentRepository;
import com.giri.oms.shipment.service.impl.ShipmentAutoCreationServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Pure unit tests — no Spring context, no DB. ShipmentRepository and
 * OrderRepository are mocked so these run in milliseconds and only exercise
 * ShipmentAutoCreationServiceImpl's own logic (Phase 3 of the Kafka rollout —
 * see OrderConfirmedShipmentConsumer).
 *
 * defaultCarrier is a @Value field, not a constructor argument, so it isn't
 * populated by @InjectMocks and has to be set manually — same reason
 * InventoryReservationServiceImplTest doesn't need this (its @Value fields are
 * only read by the mocked DistributedLockService, never by the code under test
 * directly).
 */
@ExtendWith(MockitoExtension.class)
class ShipmentAutoCreationServiceImplTest {

    @Mock
    private ShipmentRepository shipmentRepository;

    @Mock
    private OrderRepository orderRepository;

    @InjectMocks
    private ShipmentAutoCreationServiceImpl shipmentAutoCreationService;

    private static final Long ORDER_ID = 1L;
    private static final ShippingCarrier DEFAULT_CARRIER = ShippingCarrier.OTHER;

    private Order order;
    private OrderConfirmedEvent event;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(shipmentAutoCreationService, "defaultCarrier", DEFAULT_CARRIER);

        order = new Order();
        order.setId(ORDER_ID);

        event = new OrderConfirmedEvent(UUID.randomUUID(), ORDER_ID, LocalDateTime.now());
    }

    @Test
    void createsShipmentWithDefaultCarrierAndPendingStatus_whenNoneExistsYet() {
        when(shipmentRepository.findByOrderId(ORDER_ID)).thenReturn(List.of());
        when(orderRepository.findById(ORDER_ID)).thenReturn(Optional.of(order));
        when(shipmentRepository.save(any(Shipment.class))).thenAnswer(invocation -> invocation.getArgument(0));

        shipmentAutoCreationService.createForConfirmedOrder(event);

        ArgumentCaptor<Shipment> savedShipment = ArgumentCaptor.forClass(Shipment.class);
        verify(shipmentRepository).save(savedShipment.capture());
        assertThat(savedShipment.getValue().getOrder()).isEqualTo(order);
        assertThat(savedShipment.getValue().getCarrier()).isEqualTo(DEFAULT_CARRIER);
        assertThat(savedShipment.getValue().getStatus()).isEqualTo(ShipmentStatus.PENDING);
    }

    @Test
    void skipsCreation_whenAShipmentAlreadyExistsForTheOrder() {
        Shipment existing = new Shipment();
        existing.setId(50L);
        when(shipmentRepository.findByOrderId(ORDER_ID)).thenReturn(List.of(existing));

        shipmentAutoCreationService.createForConfirmedOrder(event);

        verify(shipmentRepository, never()).save(any());
        verify(orderRepository, never()).findById(any());
    }

    @Test
    void throwsOrderNotFoundException_whenOrderDoesNotExist() {
        when(shipmentRepository.findByOrderId(ORDER_ID)).thenReturn(List.of());
        when(orderRepository.findById(ORDER_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> shipmentAutoCreationService.createForConfirmedOrder(event))
                .isInstanceOf(OrderNotFoundException.class)
                .hasMessageContaining(ORDER_ID.toString());

        verify(shipmentRepository, never()).save(any());
    }
}