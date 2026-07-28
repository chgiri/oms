package com.giri.oms.inventory.consumer;

import com.giri.oms.common.correlation.MdcCorrelation;
import com.giri.oms.inventory.entity.ProductRef;
import com.giri.oms.inventory.repository.ProductRefRepository;
import com.giri.oms.messaging.event.EventType;
import com.giri.oms.messaging.event.ProductCreatedEvent;
import com.giri.oms.messaging.event.ProductDeletedEvent;
import com.giri.oms.messaging.event.ProductUpdatedEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.json.JsonMapper;

import java.time.LocalDateTime;

/**
 * Keeps inventory's product_ref replica table in sync with the product
 * module (Phase 1 step 3 of the microservices-prep plan) — see ProductRef's
 * Javadoc for why this table exists at all, and InventoryServiceImpl for
 * where it gets read.
 *
 * Event handling:
 * - ProductCreated / ProductUpdated: upsert the row. Both carry the full
 *   current name (not a diff — see those events' Javadoc), so "insert or
 *   overwrite with this value" is the entire operation; no need to
 *   distinguish create from update here.
 * - ProductDeleted: deliberately a no-op for this table. Since Phase 1 step 2
 *   made Product deletion a soft status flip rather than a hard delete, a
 *   product that fires this event can still be referenced by existing
 *   inventory rows, and those rows still need a name to display. Clearing or
 *   removing the replica row on this event would break every read of an
 *   inventory record for a now-discontinued product. If a "(discontinued)"
 *   affordance is ever wanted in inventory's API, that needs its own field —
 *   not reusing product_ref's absence to signal it.
 *
 * Delivery/consistency notes (see OrderCreatedInventoryConsumer for the full
 * reasoning, identical here):
 * - At-least-once delivery: redelivery is handled by these upserts already
 *   being idempotent (same event replayed twice just overwrites with the
 *   same value).
 * - Ordering: ProductEventFactory partitions by product id, so all events for
 *   one product are processed in order and never concurrently by two
 *   instances in this group.
 * - Offset commit: nothing here throws for a business-level reason the way
 *   InsufficientStockException does for reservations, so there's no
 *   catch-and-continue case to call out — any failure propagates to the
 *   configured error handler/DLT like an infrastructure failure would.
 *
 * <p>Guarded by app.process.role, same as OrderCreatedInventoryConsumer —
 * worker-only.
 */
@Slf4j
@Component
@ConditionalOnProperty(name = "app.process.role", havingValue = "worker", matchIfMissing = true)
@RequiredArgsConstructor
public class ProductEventInventoryConsumer {

    private final ProductRefRepository productRefRepository;
    private final JsonMapper objectMapper;

    @KafkaListener(
            topics = "${app.kafka.topics.product-events}",
            groupId = "${spring.kafka.consumer.group-id}")
    public void onMessage(
            ConsumerRecord<String, String> record,
            @Header(name = "eventType", required = false) String eventType,
            @Header(name = "correlationId", required = false) String correlationId) {
        MdcCorrelation.runWithCorrelationId(correlationId, () -> handle(record, eventType));
    }

    private void handle(ConsumerRecord<String, String> record, String eventType) {
        if (EventType.PRODUCT_CREATED.equals(eventType)) {
            ProductCreatedEvent event = readEvent(record.value(), ProductCreatedEvent.class);
            log.debug("Received ProductCreated event id={} for product id={}", event.eventId(), event.productId());
            upsert(event.productId(), event.name(), event.occurredAt());
        } else if (EventType.PRODUCT_UPDATED.equals(eventType)) {
            ProductUpdatedEvent event = readEvent(record.value(), ProductUpdatedEvent.class);
            log.debug("Received ProductUpdated event id={} for product id={}", event.eventId(), event.productId());
            upsert(event.productId(), event.name(), event.occurredAt());
        } else if (EventType.PRODUCT_DELETED.equals(eventType)) {
            ProductDeletedEvent event = readEvent(record.value(), ProductDeletedEvent.class);
            log.debug("Received ProductDeleted event id={} for product id={} — no-op, see class Javadoc",
                    event.eventId(), event.productId());
        } else {
            log.debug("Ignoring event of type {} on product-events topic (key={})", eventType, record.key());
        }
    }

    /**
     * Deserializes one event payload, tolerating unknown JSON properties —
     * see docs/event-schema-versioning.md. Deliberately overridden per-read via
     * {@code ObjectReader.without(...)} rather than on a separate globally
     * injected JsonMapper bean: the app's default {@code JsonMapper} (used for
     * REST request/response bodies) stays at Jackson's normal strict default,
     * and this override only ever applies to this one readValue call.
     */
    private <T> T readEvent(String json, Class<T> eventClass) {
        return objectMapper.readerFor(eventClass)
                .without(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
                .readValue(json);
    }

    @Transactional
    protected void upsert(Long productId, String name, LocalDateTime occurredAt) {
        ProductRef ref = productRefRepository.findById(productId).orElseGet(ProductRef::new);
        ref.setProductId(productId);
        ref.setName(name);
        ref.setUpdatedAt(occurredAt);
        productRefRepository.save(ref);
    }
}
