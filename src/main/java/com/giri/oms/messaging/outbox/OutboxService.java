package com.giri.oms.messaging.outbox;

import com.fasterxml.jackson.core.JsonProcessingException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.json.JsonMapper;

import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class OutboxService {

    private final OutboxEventRepository outboxEventRepository;
    private final JsonMapper objectMapper;

    @Transactional
    public UUID enqueue(
            UUID eventId,
            String aggregateType,
            String aggregateId,
            String eventType,
            String topic,
            String partitionKey,
            Object payload) {
        String serializedPayload = serializePayload(payload);

        OutboxEvent outboxEvent = OutboxEvent.pending(
                eventId,
                aggregateType,
                aggregateId,
                eventType,
                topic,
                partitionKey,
                serializedPayload);

        outboxEventRepository.save(outboxEvent);
        log.debug("Enqueued outbox event id={} type={} aggregate={}/{}", eventId, eventType, aggregateType, aggregateId);
        return eventId;
    }

    private String serializePayload(Object payload) {
        try {
            return objectMapper.writeValueAsString(payload);
        } catch (JacksonException ex) {
            throw new IllegalStateException("Failed to serialize outbox payload for event type: "
                    + payload.getClass().getSimpleName(), ex);
        }
    }
}
