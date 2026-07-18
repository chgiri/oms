package com.giri.oms.inventory.service.impl;

import com.giri.oms.common.lock.DistributedLockService;
import com.giri.oms.inventory.constants.InventoryConstants;
import com.giri.oms.inventory.entity.Inventory;
import com.giri.oms.inventory.entity.InventoryReservation;
import com.giri.oms.inventory.exception.InsufficientStockException;
import com.giri.oms.inventory.repository.InventoryReservationRepository;
import com.giri.oms.inventory.repository.InventoryRepository;
import com.giri.oms.inventory.service.InventoryReservationService;
import com.giri.oms.messaging.event.EventType;
import com.giri.oms.messaging.event.InventoryReservationEventFactory;
import com.giri.oms.messaging.event.InventoryReservedEvent;
import com.giri.oms.messaging.event.OrderCreatedEvent;
import com.giri.oms.messaging.outbox.OutboxService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class InventoryReservationServiceImpl implements InventoryReservationService {

    private final InventoryRepository inventoryRepository;
    private final InventoryReservationRepository inventoryReservationRepository;
    private final DistributedLockService distributedLockService;
    private final OutboxService outboxService;
    private final InventoryReservationEventFactory inventoryReservationEventFactory;

    @Value("${app.lock.inventory.wait-seconds}")
    private long lockWaitSeconds;

    @Value("${app.lock.inventory.lease-seconds}")
    private long lockLeaseSeconds;

    // Separate prefix from InventoryServiceImpl's "lock:inventory:" (which is keyed
    // by inventory record id, for direct CRUD updates) — this one is keyed by
    // product id, since reservation may need to pick among several locations
    // holding the same product.
    private static final String RESERVATION_LOCK_PREFIX = "lock:inventory:reserve:product:";

    @Override
    @Transactional
    public void reserveForOrder(OrderCreatedEvent event) {
        for (OrderCreatedEvent.OrderItemEvent item : event.items()) {
            reserveLineItem(event.orderId(), event.eventId(), item);
        }

        // Enqueued in the same transaction as the reservation writes above, so
        // the outbox pattern's usual guarantee applies: either the stock
        // decrements + reservation rows + this event all commit together, or
        // none of them do. On a redelivery of the same OrderCreated event where
        // every line item was already reserved (see the idempotency check in
        // reserveLineItem), this still re-enqueues an InventoryReserved event —
        // that's fine, since OrderSagaEventConsumer treats a repeat delivery
        // as an idempotent no-op (the order has already left PENDING).
        enqueueReservedEvent(event.orderId());
    }

    private void enqueueReservedEvent(Long orderId) {
        UUID eventId = UUID.randomUUID();
        InventoryReservedEvent reservedEvent = inventoryReservationEventFactory.reserved(orderId, eventId);
        outboxService.enqueue(
                eventId,
                inventoryReservationEventFactory.aggregateType(),
                inventoryReservationEventFactory.aggregateId(orderId),
                EventType.INVENTORY_RESERVED,
                inventoryReservationEventFactory.topic(),
                inventoryReservationEventFactory.partitionKey(orderId),
                reservedEvent);
    }

    private void reserveLineItem(Long orderId, UUID eventId, OrderCreatedEvent.OrderItemEvent item) {
        Long productId = item.productId();

        // Fast-path idempotency check: if a prior delivery of this same
        // OrderCreated event already reserved this line item, skip it rather
        // than reserving it again. The unique DB constraint (caught below) is
        // the real guarantee; this just avoids taking the lock unnecessarily
        // on the common redelivery case.
        if (inventoryReservationRepository.existsByOrderIdAndProductId(orderId, productId)) {
            log.info(InventoryConstants.RESERVATION_SKIPPED_ALREADY_PROCESSED_LOG, orderId, productId);
            return;
        }

        distributedLockService.executeWithLock(
                RESERVATION_LOCK_PREFIX + productId,
                Duration.ofSeconds(lockWaitSeconds),
                Duration.ofSeconds(lockLeaseSeconds),
                () -> doReserveLineItem(orderId, eventId, productId, item.quantity()));
    }

    private Void doReserveLineItem(Long orderId, UUID eventId, Long productId, int quantity) {
        // Re-check inside the lock: another thread/instance could have reserved
        // this exact line item between the fast-path check above and acquiring
        // the lock.
        if (inventoryReservationRepository.existsByOrderIdAndProductId(orderId, productId)) {
            log.info(InventoryConstants.RESERVATION_SKIPPED_ALREADY_PROCESSED_LOG, orderId, productId);
            return null;
        }

        Inventory inventory = findAvailableInventory(productId, quantity);

        inventory.setQuantityAvailable(inventory.getQuantityAvailable() - quantity);
        inventory.setQuantityReserved(inventory.getQuantityReserved() + quantity);
        inventoryRepository.save(inventory);

        try {
            inventoryReservationRepository.save(InventoryReservation.of(orderId, productId, eventId, quantity));
        } catch (DataIntegrityViolationException ex) {
            // Belt-and-braces: the unique (order_id, product_id) constraint caught a
            // race the pre-checks above missed. Someone else already reserved this
            // line item, so this attempt's own stock decrement above must be undone —
            // easiest and safest way is to let the whole transaction roll back.
            log.warn("Concurrent reservation detected for order id {} / product id {} — rolling back", orderId, productId);
            throw ex;
        }

        log.info(InventoryConstants.STOCK_RESERVED_LOG, quantity, productId, orderId, inventory.getLocation());
        return null;
    }

    private Inventory findAvailableInventory(Long productId, int quantity) {
        List<Inventory> records = inventoryRepository.findByProductId(productId);

        return records.stream()
                .filter(inv -> inv.getQuantityAvailable() >= quantity)
                // Deterministic choice among locations with enough stock. A smarter
                // strategy (nearest warehouse, split across locations, etc.) can
                // replace this later without changing the reservation/idempotency
                // mechanics around it.
                .min(Comparator.comparing(Inventory::getId))
                .orElseThrow(() -> new InsufficientStockException(productId, quantity, totalAvailable(records)));
    }

    private int totalAvailable(List<Inventory> records) {
        return records.stream().mapToInt(Inventory::getQuantityAvailable).sum();
    }
}
