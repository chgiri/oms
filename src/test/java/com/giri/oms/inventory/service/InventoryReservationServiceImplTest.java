package com.giri.oms.inventory.service;

import com.giri.oms.common.lock.DistributedLockService;
import com.giri.oms.inventory.entity.Inventory;
import com.giri.oms.inventory.entity.InventoryReservation;
import com.giri.oms.inventory.exception.InsufficientStockException;
import com.giri.oms.inventory.repository.InventoryReservationRepository;
import com.giri.oms.inventory.repository.InventoryRepository;
import com.giri.oms.inventory.service.impl.InventoryReservationServiceImpl;
import com.giri.oms.messaging.event.InventoryReservationEventFactory;
import com.giri.oms.messaging.event.OrderCreatedEvent;
import com.giri.oms.messaging.outbox.OutboxService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Pure unit tests — no Spring context, no DB, no Kafka. Mirrors the mocking style
 * used in InventoryServiceImplTest: the distributed lock is stubbed to run its
 * supplier straight through, same as the real lock does once acquired.
 */
@ExtendWith(MockitoExtension.class)
class InventoryReservationServiceImplTest {

    @Mock
    private InventoryRepository inventoryRepository;

    @Mock
    private InventoryReservationRepository inventoryReservationRepository;

    @Mock
    private DistributedLockService distributedLockService;

    @Mock
    private OutboxService outboxService;

    @Mock
    private InventoryReservationEventFactory inventoryReservationEventFactory;

    @InjectMocks
    private InventoryReservationServiceImpl reservationService;

    private static final Long ORDER_ID = 100L;
    private static final Long PRODUCT_ID = 1L;
    private static final UUID EVENT_ID = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        lenient().when(distributedLockService.executeWithLock(anyString(), any(), any(), any()))
                .thenAnswer(invocation -> {
                    java.util.function.Supplier<?> action = invocation.getArgument(3);
                    return action.get();
                });
    }

    @Test
    void reserveForOrder_decrementsAvailableAndIncrementsReserved_whenStockIsSufficient() {
        Inventory inventory = inventory(10L, PRODUCT_ID, "WAREHOUSE-A", 10, 0);
        when(inventoryRepository.findByProductId(PRODUCT_ID)).thenReturn(List.of(inventory));

        reservationService.reserveForOrder(orderCreatedEvent(orderItem(PRODUCT_ID, 3)));

        ArgumentCaptor<Inventory> savedInventory = ArgumentCaptor.forClass(Inventory.class);
        verify(inventoryRepository).save(savedInventory.capture());
        assertThat(savedInventory.getValue().getQuantityAvailable()).isEqualTo(7);
        assertThat(savedInventory.getValue().getQuantityReserved()).isEqualTo(3);

        ArgumentCaptor<InventoryReservation> savedReservation = ArgumentCaptor.forClass(InventoryReservation.class);
        verify(inventoryReservationRepository).save(savedReservation.capture());
        assertThat(savedReservation.getValue().getOrderId()).isEqualTo(ORDER_ID);
        assertThat(savedReservation.getValue().getProductId()).isEqualTo(PRODUCT_ID);
        assertThat(savedReservation.getValue().getQuantity()).isEqualTo(3);
    }

    @Test
    void reserveForOrder_picksLowestIdLocationThatHasEnoughStock() {
        Inventory tooLittleStock = inventory(2L, PRODUCT_ID, "WAREHOUSE-A", 1, 0);
        Inventory enoughStock = inventory(3L, PRODUCT_ID, "WAREHOUSE-B", 10, 0);
        when(inventoryRepository.findByProductId(PRODUCT_ID)).thenReturn(List.of(tooLittleStock, enoughStock));

        reservationService.reserveForOrder(orderCreatedEvent(orderItem(PRODUCT_ID, 5)));

        ArgumentCaptor<Inventory> savedInventory = ArgumentCaptor.forClass(Inventory.class);
        verify(inventoryRepository).save(savedInventory.capture());
        assertThat(savedInventory.getValue().getLocation()).isEqualTo("WAREHOUSE-B");
    }

    @Test
    void reserveForOrder_throwsInsufficientStock_whenNoLocationHasEnough() {
        Inventory inventory = inventory(10L, PRODUCT_ID, "WAREHOUSE-A", 2, 0);
        when(inventoryRepository.findByProductId(PRODUCT_ID)).thenReturn(List.of(inventory));

        OrderCreatedEvent event = orderCreatedEvent(orderItem(PRODUCT_ID, 5));

        assertThatThrownBy(() -> reservationService.reserveForOrder(event))
                .isInstanceOf(InsufficientStockException.class)
                .hasMessageContaining("requested 5")
                .hasMessageContaining("available 2");

        verify(inventoryRepository, never()).save(any());
        verify(inventoryReservationRepository, never()).save(any());
    }

    @Test
    void reserveForOrder_throwsInsufficientStock_whenProductHasNoInventoryRecordsAtAll() {
        when(inventoryRepository.findByProductId(PRODUCT_ID)).thenReturn(List.of());

        OrderCreatedEvent event = orderCreatedEvent(orderItem(PRODUCT_ID, 1));

        assertThatThrownBy(() -> reservationService.reserveForOrder(event))
                .isInstanceOf(InsufficientStockException.class)
                .hasMessageContaining("available 0");
    }

    @Test
    void reserveForOrder_skipsLineItem_whenAlreadyReserved_soRedeliveryDoesNotDoubleDecrement() {
        when(inventoryReservationRepository.existsByOrderIdAndProductId(ORDER_ID, PRODUCT_ID)).thenReturn(true);

        reservationService.reserveForOrder(orderCreatedEvent(orderItem(PRODUCT_ID, 3)));

        verify(inventoryRepository, never()).findByProductId(anyLong());
        verify(inventoryRepository, never()).save(any());
        verify(inventoryReservationRepository, never()).save(any());
    }

    @Test
    void reserveForOrder_reservesEachLineItemIndependently() {
        Long secondProductId = 2L;
        when(inventoryRepository.findByProductId(PRODUCT_ID))
                .thenReturn(List.of(inventory(20L, PRODUCT_ID, "WAREHOUSE-A", 10, 0)));
        when(inventoryRepository.findByProductId(secondProductId))
                .thenReturn(List.of(inventory(21L, secondProductId, "WAREHOUSE-A", 10, 0)));

        OrderCreatedEvent event = new OrderCreatedEvent(
                EVENT_ID, ORDER_ID, 5L, "PENDING", new BigDecimal("100.00"),
                List.of(orderItem(PRODUCT_ID, 2), orderItem(secondProductId, 4)),
                LocalDateTime.now());

        reservationService.reserveForOrder(event);

        verify(inventoryRepository, times(2)).save(any());
        verify(inventoryReservationRepository, times(2)).save(any());
    }

    private Inventory inventory(Long inventoryId, Long productId, String location, int available, int reserved) {
        Inventory inventory = new Inventory();
        inventory.setId(inventoryId);
        com.giri.oms.product.entity.Product product = new com.giri.oms.product.entity.Product();
        product.setId(productId);
        inventory.setProduct(product);
        inventory.setLocation(location);
        inventory.setQuantityAvailable(available);
        inventory.setQuantityReserved(reserved);
        return inventory;
    }

    private OrderCreatedEvent.OrderItemEvent orderItem(Long productId, int quantity) {
        return new OrderCreatedEvent.OrderItemEvent(productId, "Product " + productId, quantity, new BigDecimal("10.00"),
                new BigDecimal("10.00").multiply(BigDecimal.valueOf(quantity)));
    }

    private OrderCreatedEvent orderCreatedEvent(OrderCreatedEvent.OrderItemEvent... items) {
        return new OrderCreatedEvent(
                EVENT_ID, ORDER_ID, 5L, "PENDING", new BigDecimal("100.00"), List.of(items), LocalDateTime.now());
    }
}
