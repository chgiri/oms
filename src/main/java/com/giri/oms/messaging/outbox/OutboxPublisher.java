package com.giri.oms.messaging.outbox;

import com.giri.oms.common.correlation.MdcCorrelation;
import com.giri.oms.messaging.config.KafkaAppProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.header.internals.RecordHeader;
import org.springframework.data.domain.PageRequest;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

@Slf4j
@Component
@RequiredArgsConstructor
public class OutboxPublisher {

    private static final long SEND_TIMEOUT_SECONDS = 10;

    private final OutboxEventRepository outboxEventRepository;
    private final KafkaTemplate<String, String> kafkaTemplate;
    private final KafkaAppProperties kafkaAppProperties;
    private final Clock clock;

    @Scheduled(fixedDelayString = "${app.kafka.outbox.poll-interval-ms}")
    public void publishPendingEvents() {
        int batchSize = kafkaAppProperties.outbox().batchSize();
        List<OutboxEvent> pendingEvents = outboxEventRepository.findByStatusOrderByCreatedAtAsc(
                OutboxEventStatus.PENDING,
                PageRequest.of(0, batchSize));

        if (pendingEvents.isEmpty()) {
            return;
        }

        log.debug("Publishing {} pending outbox event(s)", pendingEvents.size());
        for (OutboxEvent event : pendingEvents) {
            publishSingleEvent(event);
        }
    }

    public void publishSingleEvent(OutboxEvent event) {
        // This runs on scheduling-1, a shared pool thread with no MDC of its own —
        // scope the correlation id (captured back when OutboxService.enqueue()
        // wrote this row) to just this event's publish, so a batch of unrelated
        // events being flushed in the same poll never bleed correlation ids into
        // each other's log lines.
        MdcCorrelation.runWithCorrelationId(event.getCorrelationId(), () -> doPublish(event));
    }

    private void doPublish(OutboxEvent event) {
        try {
            ProducerRecord<String, String> record = new ProducerRecord<>(
                    event.getTopic(), null, event.getPartitionKey(), event.getPayload());
            record.headers().add(new RecordHeader("eventType", event.getEventType().getBytes(StandardCharsets.UTF_8)));
            if (event.getCorrelationId() != null) {
                record.headers().add(new RecordHeader("correlationId", event.getCorrelationId().getBytes(StandardCharsets.UTF_8)));
            }

            kafkaTemplate.send(record).get(SEND_TIMEOUT_SECONDS, TimeUnit.SECONDS);

            event.markPublished(clock);
            outboxEventRepository.save(event);
            log.info("Published outbox event id={} type={} topic={}", event.getId(), event.getEventType(), event.getTopic());
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            event.recordFailure("Interrupted while publishing to Kafka");
            outboxEventRepository.save(event);
            log.warn("Interrupted while publishing outbox event id={}", event.getId());
        } catch (ExecutionException | TimeoutException ex) {
            event.recordFailure(ex.getMessage());
            outboxEventRepository.save(event);
            log.warn("Failed to publish outbox event id={} type={}: {}", event.getId(), event.getEventType(), ex.getMessage());
        }
    }
}
