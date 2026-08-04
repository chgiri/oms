package com.giri.oms.messaging.config;

import com.giri.oms.inventory.exception.InsufficientStockException;
import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.listener.DeadLetterPublishingRecoverer;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.util.backoff.FixedBackOff;

@Configuration
public class KafkaConfig {

    @Bean
    NewTopic orderEventsTopic(KafkaAppProperties kafkaAppProperties) {
        return TopicBuilder.name(kafkaAppProperties.topics().orderEvents())
                .partitions(3)
                .replicas(1)
                .build();
    }

    // Same partition count as the source topic (not required to match, but keeps
    // things simple) — receives whatever the DefaultErrorHandler below gives up on.
    @Bean
    NewTopic orderEventsDeadLetterTopic(KafkaAppProperties kafkaAppProperties) {
        return TopicBuilder.name(kafkaAppProperties.topics().orderEvents() + ".DLT")
                .partitions(3)
                .replicas(1)
                .build();
    }

    // Phase 1 of the microservices-prep plan — see EventType's Product*
    // constants. As of Stage 5, this topic's producer is product-service's
    // own local outbox (see that repo's messaging.event.ProductEventFactory),
    // not anything in this codebase — inventory.consumer.ProductEventInventoryConsumer
    // is still this app's own reader of it, keeping inventory's product_ref
    // replica in sync. Still needs to exist here (and its DLT) purely so
    // that consumer and its topic/DLT config have somewhere to point.
    @Bean
    NewTopic productEventsTopic(KafkaAppProperties kafkaAppProperties) {
        return TopicBuilder.name(kafkaAppProperties.topics().productEvents())
                .partitions(3)
                .replicas(1)
                .build();
    }

    @Bean
    NewTopic productEventsDeadLetterTopic(KafkaAppProperties kafkaAppProperties) {
        return TopicBuilder.name(kafkaAppProperties.topics().productEvents() + ".DLT")
                .partitions(3)
                .replicas(1)
                .build();
    }

    /**
     * Applies to every @KafkaListener in the app (Spring Boot wires this into the
     * auto-configured listener container factory automatically) — currently
     * OrderCreatedInventoryConsumer and OrderSagaEventConsumer (OrderConfirmedShipmentConsumer
     * used to be a third, until Stage 5 of the microservices-prep plan moved it into
     * shipment-service — its own KafkaConfig has the equivalent bean now), and this
     * doesn't need to change as more consumers are added in later phases.
     *
     * Retries a failure 3 times, 2 seconds apart, to ride out a brief DB/Redis
     * blip without holding up the partition for too long. If it's still failing
     * after that, the DeadLetterPublishingRecoverer republishes the raw record to
     * "<topic>.DLT" instead of either blocking the partition forever or silently
     * dropping the message — it's preserved for inspection/replay.
     *
     * InsufficientStockException is listed as non-retryable purely as a safety
     * net: OrderCreatedInventoryConsumer already catches it itself (it's a
     * business failure, not a transient one — retrying can't produce more stock),
     * so in practice it should never reach this handler at all.
     */
    @Bean
    DefaultErrorHandler kafkaErrorHandler(KafkaTemplate<String, String> kafkaTemplate) {
        DeadLetterPublishingRecoverer recoverer = new DeadLetterPublishingRecoverer(kafkaTemplate);

        DefaultErrorHandler errorHandler = new DefaultErrorHandler(recoverer, new FixedBackOff(2000L, 3L));
        errorHandler.addNotRetryableExceptions(InsufficientStockException.class);

        return errorHandler;
    }
}