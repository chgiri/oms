-- Bridges the outbox pattern's trace gap: OutboxService.enqueue() runs on
-- whatever thread called it (an HTTP request thread, or a KafkaListener
-- container thread) with a live tracing span in context, but
-- OutboxPublisher's @Scheduled poller picks the row up later on
-- scheduling-1, a thread with no span of its own. Left alone, that means
-- every saga that goes through the outbox produces two disconnected traces
-- — "HTTP request -> DB write" and a separate "poller -> Kafka send" — not
-- the one continuous trace you'd want in Tempo.
--
-- These columns carry the W3C trace id (32 hex chars) and span id (16 hex
-- chars) that were current at enqueue time, the same way correlation_id
-- already carries the MDC correlation id across that same thread hop. When
-- OutboxPublisher eventually sends the record, it uses these to add a span
-- link (see OutboxTraceLinking) tying the Kafka publish back to the
-- original request's trace instead of starting an unrelated one. Nullable
-- for the same reason correlation_id is: no live span at enqueue time (e.g.
-- tracing disabled, or a call site with nothing in context) just means the
-- eventual publish gets no link, not an error.
ALTER TABLE oms_messaging.outbox_events ADD COLUMN trace_id VARCHAR(32);
ALTER TABLE oms_messaging.outbox_events ADD COLUMN span_id VARCHAR(16);
