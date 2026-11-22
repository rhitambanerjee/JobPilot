CREATE TABLE applications (
    id                UUID PRIMARY KEY,
    company           VARCHAR(200) NOT NULL,
    role              VARCHAR(200) NOT NULL,
    job_url           VARCHAR(1000),
    stage             VARCHAR(20)  NOT NULL,
    notes             TEXT,
    last_activity_at  TIMESTAMPTZ  NOT NULL,
    stale_notified_at TIMESTAMPTZ,
    created_at        TIMESTAMPTZ  NOT NULL,
    updated_at        TIMESTAMPTZ  NOT NULL,
    version           BIGINT       NOT NULL DEFAULT 0
);
CREATE INDEX idx_applications_stage ON applications (stage);
CREATE INDEX idx_applications_last_activity ON applications (last_activity_at);

CREATE TABLE audit_logs (
    id          UUID PRIMARY KEY,
    entity_type VARCHAR(50)  NOT NULL,
    entity_id   UUID         NOT NULL,
    action      VARCHAR(50)  NOT NULL,
    actor       VARCHAR(100) NOT NULL,
    details     JSONB        NOT NULL DEFAULT '{}'::jsonb,
    created_at  TIMESTAMPTZ  NOT NULL
);
CREATE INDEX idx_audit_entity ON audit_logs (entity_id, created_at DESC);
