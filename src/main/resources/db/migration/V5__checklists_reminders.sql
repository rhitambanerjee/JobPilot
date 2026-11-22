CREATE TABLE checklists (
    id             UUID PRIMARY KEY,
    application_id UUID         NOT NULL REFERENCES applications (id),
    title          VARCHAR(200) NOT NULL,
    items          JSONB        NOT NULL DEFAULT '[]'::jsonb,
    created_at     TIMESTAMPTZ  NOT NULL
);
CREATE INDEX idx_checklists_application ON checklists (application_id);

CREATE TABLE reminders (
    id             UUID PRIMARY KEY,
    application_id UUID         NOT NULL REFERENCES applications (id),
    message        VARCHAR(500) NOT NULL,
    due_at         TIMESTAMPTZ  NOT NULL,
    done           BOOLEAN      NOT NULL DEFAULT FALSE,
    created_at     TIMESTAMPTZ  NOT NULL
);
CREATE INDEX idx_reminders_due ON reminders (due_at) WHERE done = FALSE;
