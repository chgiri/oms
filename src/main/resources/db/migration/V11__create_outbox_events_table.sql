CREATE TABLE outbox_events (
    id              UUID PRIMARY KEY,
    aggregate_type  VARCHAR(50)  NOT NULL,
    aggregate_id    VARCHAR(100) NOT NULL,
    event_type      VARCHAR(100) NOT NULL,
    topic           VARCHAR(255) NOT NULL,
    partition_key   VARCHAR(100) NOT NULL,
    payload         TEXT         NOT NULL,
    status          VARCHAR(20)  NOT NULL DEFAULT 'PENDING',
    retry_count     INT          NOT NULL DEFAULT 0,
    last_error      TEXT,
    created_at      TIMESTAMP(6) NOT NULL,
    published_at    TIMESTAMP(6),
    CONSTRAINT ck_outbox_events_status CHECK (status IN ('PENDING', 'PUBLISHED', 'FAILED'))
);

CREATE INDEX idx_outbox_events_status_created_at ON outbox_events (status, created_at);
