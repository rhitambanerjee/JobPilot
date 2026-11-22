CREATE TABLE users (
    id            UUID PRIMARY KEY,
    email         VARCHAR(320) NOT NULL,
    password_hash VARCHAR(100) NOT NULL,
    display_name  VARCHAR(100),
    created_at    TIMESTAMPTZ  NOT NULL
);
CREATE UNIQUE INDEX uq_users_email ON users (lower(email));

-- Placeholder owner for rows that exist before accounts do. Nobody can log in as it (the hash is not valid
-- BCrypt). The first account that registers takes over these rows and this placeholder is deleted.
INSERT INTO users (id, email, password_hash, display_name, created_at)
VALUES ('00000000-0000-0000-0000-000000000000', 'legacy@job-tracker.local', '!', 'Existing data', now());

-- Every user-owned table gets an owner.
ALTER TABLE applications    ADD COLUMN user_id UUID REFERENCES users (id);
ALTER TABLE audit_logs      ADD COLUMN user_id UUID REFERENCES users (id);
ALTER TABLE outbox_events   ADD COLUMN user_id UUID REFERENCES users (id);
ALTER TABLE rules           ADD COLUMN user_id UUID REFERENCES users (id);
ALTER TABLE rule_executions ADD COLUMN user_id UUID REFERENCES users (id);
ALTER TABLE checklists      ADD COLUMN user_id UUID REFERENCES users (id);
ALTER TABLE reminders       ADD COLUMN user_id UUID REFERENCES users (id);
ALTER TABLE notifications   ADD COLUMN user_id UUID REFERENCES users (id);
ALTER TABLE outgoing_emails ADD COLUMN user_id UUID REFERENCES users (id);
ALTER TABLE email_templates ADD COLUMN user_id UUID REFERENCES users (id);

UPDATE applications    SET user_id = '00000000-0000-0000-0000-000000000000';
UPDATE audit_logs      SET user_id = '00000000-0000-0000-0000-000000000000';
UPDATE outbox_events   SET user_id = '00000000-0000-0000-0000-000000000000';
UPDATE rules           SET user_id = '00000000-0000-0000-0000-000000000000';
UPDATE rule_executions SET user_id = '00000000-0000-0000-0000-000000000000';
UPDATE checklists      SET user_id = '00000000-0000-0000-0000-000000000000';
UPDATE reminders       SET user_id = '00000000-0000-0000-0000-000000000000';
UPDATE notifications   SET user_id = '00000000-0000-0000-0000-000000000000';
UPDATE outgoing_emails SET user_id = '00000000-0000-0000-0000-000000000000';
-- built-in templates stay ownerless (shared by everyone); saved ones belong to the placeholder owner
UPDATE email_templates SET user_id = '00000000-0000-0000-0000-000000000000' WHERE built_in = FALSE;

ALTER TABLE applications    ALTER COLUMN user_id SET NOT NULL;
ALTER TABLE audit_logs      ALTER COLUMN user_id SET NOT NULL;
ALTER TABLE outbox_events   ALTER COLUMN user_id SET NOT NULL;
ALTER TABLE rules           ALTER COLUMN user_id SET NOT NULL;
ALTER TABLE rule_executions ALTER COLUMN user_id SET NOT NULL;
ALTER TABLE checklists      ALTER COLUMN user_id SET NOT NULL;
ALTER TABLE reminders       ALTER COLUMN user_id SET NOT NULL;
ALTER TABLE notifications   ALTER COLUMN user_id SET NOT NULL;
ALTER TABLE outgoing_emails ALTER COLUMN user_id SET NOT NULL;
ALTER TABLE email_templates ADD CONSTRAINT chk_template_owner CHECK (built_in OR user_id IS NOT NULL);

CREATE INDEX idx_applications_user_stage ON applications (user_id, stage);
CREATE INDEX idx_audit_user ON audit_logs (user_id, created_at DESC);
CREATE INDEX idx_rules_user ON rules (user_id, created_at DESC);
CREATE INDEX idx_notifications_user ON notifications (user_id, created_at DESC);
CREATE INDEX idx_reminders_user_due ON reminders (user_id, due_at) WHERE done = FALSE;
CREATE INDEX idx_templates_user ON email_templates (user_id);

-- Notification settings were a single row (id = 1); now one row per user.
ALTER TABLE notification_settings RENAME TO notification_settings_old;
CREATE TABLE notification_settings (
    user_id       UUID PRIMARY KEY REFERENCES users (id),
    email         VARCHAR(320),
    email_enabled BOOLEAN     NOT NULL DEFAULT FALSE,
    updated_at    TIMESTAMPTZ NOT NULL
);
INSERT INTO notification_settings (user_id, email, email_enabled, updated_at)
SELECT '00000000-0000-0000-0000-000000000000', email, email_enabled, updated_at FROM notification_settings_old;
DROP TABLE notification_settings_old;
