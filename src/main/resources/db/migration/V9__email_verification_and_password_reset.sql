ALTER TABLE users ADD COLUMN email_verified  BOOLEAN NOT NULL DEFAULT FALSE;
-- bumped on every password reset; a session is only valid while its copy matches, so a reset logs out everyone
ALTER TABLE users ADD COLUMN session_version INT     NOT NULL DEFAULT 0;
-- accounts that already exist were created before verification existed: keep them working
UPDATE users SET email_verified = TRUE;

-- One-time links. Only a SHA-256 hash of the token is stored, so a database leak does not leak usable links.
CREATE TABLE auth_tokens (
    id         UUID PRIMARY KEY,
    user_id    UUID        NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    type       VARCHAR(20) NOT NULL,
    token_hash VARCHAR(64) NOT NULL UNIQUE,
    expires_at TIMESTAMPTZ NOT NULL,
    used_at    TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL
);
CREATE INDEX idx_auth_tokens_user ON auth_tokens (user_id, type);

-- Account emails (verify / reset) reuse the outgoing-email queue and its retry logic.
ALTER TABLE outgoing_emails ADD COLUMN kind VARCHAR(20) NOT NULL DEFAULT 'FOLLOW_UP';
