-- Single-user app: exactly one settings row (id = 1).
CREATE TABLE notification_settings (
    id            INT PRIMARY KEY CHECK (id = 1),
    email         VARCHAR(320),
    email_enabled BOOLEAN     NOT NULL DEFAULT FALSE,
    updated_at    TIMESTAMPTZ NOT NULL
);

-- One row per rule run. email_to is a snapshot taken at creation time, so changing the
-- settings later never redirects an email that is already queued.
CREATE TABLE notifications (
    id             UUID PRIMARY KEY,
    application_id UUID REFERENCES applications (id),
    rule_id        UUID,
    title          VARCHAR(300)  NOT NULL,
    message        VARCHAR(1000) NOT NULL,
    read_at        TIMESTAMPTZ,
    email_to       VARCHAR(320),
    email_status   VARCHAR(20)   NOT NULL,
    email_attempts INT           NOT NULL DEFAULT 0,
    email_error    TEXT,
    created_at     TIMESTAMPTZ   NOT NULL,
    emailed_at     TIMESTAMPTZ
);
CREATE INDEX idx_notifications_created ON notifications (created_at DESC);
CREATE INDEX idx_notifications_email_pending ON notifications (created_at) WHERE email_status = 'PENDING';
