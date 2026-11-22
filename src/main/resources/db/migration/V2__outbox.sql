CREATE TABLE outbox_events (
    id             UUID PRIMARY KEY,
    aggregate_type VARCHAR(50)  NOT NULL,
    aggregate_id   UUID         NOT NULL,
    event_type     VARCHAR(100) NOT NULL,
    payload        JSONB        NOT NULL,
    status         VARCHAR(20)  NOT NULL,
    attempts       INT          NOT NULL DEFAULT 0,
    last_error     TEXT,
    created_at     TIMESTAMPTZ  NOT NULL,
    processed_at   TIMESTAMPTZ
);
-- the poller only ever looks at PENDING rows, so index just those
CREATE INDEX idx_outbox_pending ON outbox_events (created_at) WHERE status = 'PENDING';
