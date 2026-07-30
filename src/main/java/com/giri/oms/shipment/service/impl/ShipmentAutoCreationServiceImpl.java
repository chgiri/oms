package com.giri.oms.shipment.service.impl;

import com.giri.oms.messaging.event.OrderConfirmedEvent;
import com.giri.oms.order.service.OrderService;
import com.giri.oms.shipment.constants.ShipmentConstants;
import com.giri.oms.shipment.entity.Shipment;
import com.giri.oms.shipment.entity.ShipmentStatus;
import com.giri.oms.shipment.entity.ShippingCarrier;
import com.giri.oms.shipment.exception.ShipmentWritesFrozenException;
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
    private final OrderService orderService;

    // Stage 3 of the microservices-prep plan (data cutover) — see
    // ShipmentWritesFrozenException and docs/stage3-data-cutover-runbook-shipment.md.
    // This is defense-in-depth, NOT the primary safeguard for this class —
    // the runbook's real guarantee is stopping this consumer's Kafka group
    // membership entirely before shipment-service's own copy of it starts
    // (see that consumer's javadoc and the runbook). This check exists for
    // the case where a redelivered/backlogged message reaches this method
    // after the flag flips but before the consumer process itself is
    // actually torn down.
    @Value("${app.shipment.writes-frozen:false}")
    private boolean writesFrozen;

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

        // See ShipmentWritesFrozenException's javadoc — this should rarely
        // actually fire; if it does, the message is retried by
        // KafkaConfig's DefaultErrorHandler and eventually dead-lettered
        // rather than silently creating a shipment mid-cutover.
        if (writesFrozen) {
            throw new ShipmentWritesFrozenException();
        }

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

        // Existence-only check — OrderService.getOrderById throws OrderNotFoundException
        // itself if the order doesn't exist.
        orderService.getOrderById(orderId);

        Shipment shipment = new Shipment();
        shipment.setOrderId(orderId);
        shipment.setCarrier(defaultCarrier);
        shipment.setStatus(ShipmentStatus.PENDING);

        Shipment savedShipment = shipmentRepository.save(shipment);

        log.info(ShipmentConstants.SHIPMENT_CREATED_LOG, savedShipment.getId());
    }
}