CREATE TABLE rules (
    id           UUID PRIMARY KEY,
    name         VARCHAR(200) NOT NULL,
    enabled      BOOLEAN      NOT NULL DEFAULT TRUE,
    trigger_spec JSONB        NOT NULL,
    conditions   JSONB        NOT NULL DEFAULT '[]'::jsonb,
    actions      JSONB        NOT NULL,
    created_at   TIMESTAMPTZ  NOT NULL
);
