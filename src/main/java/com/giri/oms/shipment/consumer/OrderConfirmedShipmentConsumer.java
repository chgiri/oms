package com.giri.oms.shipment.consumer;

import com.giri.oms.common.correlation.MdcCorrelation;
import com.giri.oms.messaging.event.EventType;
import com.giri.oms.messaging.event.OrderConfirmedEvent;
import com.giri.oms.shipment.service.ShipmentAutoCreationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.stereotype.Component;
import tools.jackson.databind.json.JsonMapper;

/**
 * Phase 3's last step: auto-creates a shipment once an order is confirmed.
 * Runs in its own consumer group on the shared order-events topic (see the
 * topic-strategy note on OrderSagaEventConsumer) — a third independent
 * subscriber alongside the inventory and order-saga groups.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class OrderConfirmedShipmentConsumer {

    private final ShipmentAutoCreationService shipmentAutoCreationService;
    private final JsonMapper objectMapper;

    @KafkaListener(
            topics = "${app.kafka.topics.order-events}",
            groupId = "${app.kafka.consumer.shipment-service-group-id}")
    public void onMessage(
            ConsumerRecord<String, String> record,
            @Header(name = "eventType", required = false) String eventType,
            @Header(name = "correlationId", required = false) String correlationId) {
        MdcCorrelation.runWithCorrelationId(correlationId, () -> handle(record, eventType));
    }

    private void handle(ConsumerRecord<String, String> record, String eventType) {
        if (!EventType.ORDER_CONFIRMED.equals(eventType)) {
            log.debug("Ignoring event of type {} on order-events topic (key={})", eventType, record.key());
            return;
        }

        OrderConfirmedEvent event = objectMapper.readValue(record.value(), OrderConfirmedEvent.class);
        log.debug("Received OrderConfirmed event id={} for order id={}", event.eventId(), event.orderId());

        shipmentAutoCreationService.createForConfirmedOrder(event);
    }
}
