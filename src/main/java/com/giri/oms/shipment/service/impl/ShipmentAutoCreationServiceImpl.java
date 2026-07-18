package com.giri.oms.shipment.service.impl;

import com.giri.oms.messaging.event.OrderConfirmedEvent;
import com.giri.oms.order.entity.Order;
import com.giri.oms.order.exception.OrderNotFoundException;
import com.giri.oms.order.repository.OrderRepository;
import com.giri.oms.shipment.constants.ShipmentConstants;
import com.giri.oms.shipment.entity.Shipment;
import com.giri.oms.shipment.entity.ShipmentStatus;
import com.giri.oms.shipment.entity.ShippingCarrier;
import com.giri.oms.shipment.repository.ShipmentRepository;
import com.giri.oms.shipment.service.ShipmentAutoCreationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class ShipmentAutoCreationServiceImpl implements ShipmentAutoCreationService {

    private final ShipmentRepository shipmentRepository;
    private final OrderRepository orderRepository;

    // Phase 3 auto-creates a shipment the moment an order is confirmed, but
    // nothing in the saga so far has any notion of which carrier the customer
    // wants — that's still a REST-only concept today. Rather than block
    // shipment creation on a piece of information the saga doesn't carry yet,
    // every auto-created shipment gets this default; a human (or a future
    // event carrying a real preference) can update the carrier later via the
    // existing PATCH endpoint if it matters operationally.
    @Value("${app.shipment.default-carrier}")
    private ShippingCarrier defaultCarrier;

    @Override
    @Transactional
    public void createForConfirmedOrder(OrderConfirmedEvent event) {
        Long orderId = event.orderId();

        // Idempotency check: unlike inventory reservations, there's no unique DB
        // constraint backing this (a legitimate reship intentionally creates a
        // second shipment for the same order via the REST endpoint), so this is
        // a soft guard rather than a hard one. It's still correct here because
        // OrderConfirmed only fires once per order along the happy path this
        // consumer reacts to — a redelivery of the same event is what this is
        // actually guarding against, not a real second shipment.
        if (!shipmentRepository.findByOrderId(orderId).isEmpty()) {
            log.info("Skipping shipment auto-creation for order id={} — a shipment already exists (duplicate delivery)",
                    orderId);
            return;
        }

        Order order = getExistingOrder(orderId);

        Shipment shipment = new Shipment();
        shipment.setOrder(order);
        shipment.setCarrier(defaultCarrier);
        shipment.setStatus(ShipmentStatus.PENDING);

        Shipment savedShipment = shipmentRepository.save(shipment);

        log.info(ShipmentConstants.SHIPMENT_CREATED_LOG, savedShipment.getId());
    }

    private Order getExistingOrder(Long orderId) {
        return orderRepository.findById(orderId)
                .orElseThrow(() -> {
                    log.warn("Order not found with id: {} while auto-creating shipment", orderId);
                    return new OrderNotFoundException(orderId);
                });
    }
}
