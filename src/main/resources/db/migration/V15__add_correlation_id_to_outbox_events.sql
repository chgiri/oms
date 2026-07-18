-- Carries the originating request's correlation id (see CorrelationIdFilter)
-- through the outbox so it survives the hop from the HTTP thread onto
-- scheduling-1 (OutboxPublisher) and, from there, onto the Kafka header that
-- KafkaListenerEndpointContainer threads read back out. Nullable: events
-- enqueued with no correlation id in MDC (e.g. a scheduled job with no
-- inbound request) just publish without one, same as today.
ALTER TABLE outbox_events ADD COLUMN correlation_id VARCHAR(100);
