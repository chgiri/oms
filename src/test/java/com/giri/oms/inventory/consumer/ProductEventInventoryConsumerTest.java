package com.giri.oms.inventory.consumer;

import com.giri.oms.inventory.entity.ProductRef;
import com.giri.oms.inventory.repository.ProductRefRepository;
import com.giri.oms.messaging.event.EventType;
import com.giri.oms.messaging.event.ProductCreatedEvent;
import com.giri.oms.messaging.event.ProductDeletedEvent;
import com.giri.oms.messaging.event.ProductUpdatedEvent;
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
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * Pure unit tests — no Spring context, no embedded Kafka. ProductRefRepository
 * is mocked; a real JsonMapper (de)serializes the record payloads, the same
 * way OrderCreatedInventoryConsumerTest does.
 *
 * Covers the three product-lifecycle events this consumer reacts to (Phase 1
 * step 3 of the microservices-prep plan): ProductCreated/ProductUpdated both
 * upsert product_ref, ProductDeleted is a deliberate no-op (see the class
 * Javadoc — a discontinued product's name must still resolve for existing
 * inventory rows), plus ignoring event types this consumer doesn't own.
 */
@ExtendWith(MockitoExtension.class)
class ProductEventInventoryConsumerTest {

    @Mock
    private ProductRefRepository productRefRepository;

    private JsonMapper objectMapper;
    private ProductEventInventoryConsumer consumer;

    private static final Long PRODUCT_ID = 7L;
    private static final String TOPIC = "oms.product.events";

    @BeforeEach
    void setUp() {
        objectMapper = JsonMapper.builder().build();
        consumer = new ProductEventInventoryConsumer(productRefRepository, objectMapper);
    }

    @Test
    void productCreated_insertsNewProductRefRow() {
        LocalDateTime occurredAt = LocalDateTime.now();
        ProductCreatedEvent event = new ProductCreatedEvent(
                UUID.randomUUID(), PRODUCT_ID, "Wireless Mouse", new BigDecimal("25.99"), occurredAt);
        when(productRefRepository.findById(PRODUCT_ID)).thenReturn(Optional.empty());

        consumer.onMessage(record(event), EventType.PRODUCT_CREATED, null);

        ArgumentCaptor<ProductRef> savedRef = ArgumentCaptor.forClass(ProductRef.class);
        verify(productRefRepository).save(savedRef.capture());
        assertThat(savedRef.getValue().getProductId()).isEqualTo(PRODUCT_ID);
        assertThat(savedRef.getValue().getName()).isEqualTo("Wireless Mouse");
        assertThat(savedRef.getValue().getUpdatedAt()).isEqualTo(occurredAt);
    }

    @Test
    void productUpdated_overwritesExistingProductRefRow() {
        ProductRef existing = new ProductRef(PRODUCT_ID, "Old Name", LocalDateTime.now().minusDays(1));
        LocalDateTime occurredAt = LocalDateTime.now();
        ProductUpdatedEvent event = new ProductUpdatedEvent(
                UUID.randomUUID(), PRODUCT_ID, "New Name", new BigDecimal("29.99"), occurredAt);
        when(productRefRepository.findById(PRODUCT_ID)).thenReturn(Optional.of(existing));

        consumer.onMessage(record(event), EventType.PRODUCT_UPDATED, null);

        ArgumentCaptor<ProductRef> savedRef = ArgumentCaptor.forClass(ProductRef.class);
        verify(productRefRepository).save(savedRef.capture());
        // Same row (existing entity), overwritten in place — not a second insert.
        assertThat(savedRef.getValue()).isSameAs(existing);
        assertThat(savedRef.getValue().getName()).isEqualTo("New Name");
        assertThat(savedRef.getValue().getUpdatedAt()).isEqualTo(occurredAt);
    }

    @Test
    void productDeleted_isNoOp_andLeavesProductRefUntouched() {
        // Product deletion is a soft status flip (Phase 1 step 2) — an existing
        // inventory row can still reference a discontinued product and still
        // needs a name to display, so this event must NOT clear or remove the
        // replica row. See the class Javadoc.
        ProductDeletedEvent event = new ProductDeletedEvent(UUID.randomUUID(), PRODUCT_ID, LocalDateTime.now());

        consumer.onMessage(record(event), EventType.PRODUCT_DELETED, null);

        verifyNoInteractions(productRefRepository);
    }

    @Test
    void ignoresEventTypesThisConsumerDoesNotOwn() {
        consumer.onMessage(new ConsumerRecord<>(TOPIC, 0, 0L, PRODUCT_ID.toString(), "{}"), EventType.ORDER_CREATED, null);

        verifyNoInteractions(productRefRepository);
    }

    private ConsumerRecord<String, String> record(Object event) {
        String json = objectMapper.writeValueAsString(event);
        return new ConsumerRecord<>(TOPIC, 0, 0L, PRODUCT_ID.toString(), json);
    }
}
