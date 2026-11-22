-- One row per (rule, event, action). The unique idempotency key is what makes
-- duplicate event delivery harmless: the second insert is a no-op.
CREATE TABLE rule_executions (
    id              UUID PRIMARY KEY,
    idempotency_key VARCHAR(200) NOT NULL UNIQUE,
    rule_id         UUID         NOT NULL,
    application_id  UUID         NOT NULL REFERENCES applications (id),
    action_type     VARCHAR(50)  NOT NULL,
    params          JSONB        NOT NULL DEFAULT '{}'::jsonb,
    status          VARCHAR(20)  NOT NULL,
    attempts        INT          NOT NULL DEFAULT 0,
    max_attempts    INT          NOT NULL,
    next_run_at     TIMESTAMPTZ  NOT NULL,
    locked_at       TIMESTAMPTZ,
    last_error      TEXT,
    created_at      TIMESTAMPTZ  NOT NULL,
    updated_at      TIMESTAMPTZ  NOT NULL
);
CREATE INDEX idx_rule_exec_claim ON rule_executions (next_run_at) WHERE status IN ('PENDING', 'RUNNING');
CREATE INDEX idx_rule_exec_application ON rule_executions (application_id);
