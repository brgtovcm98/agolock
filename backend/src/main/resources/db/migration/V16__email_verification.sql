ALTER TABLE app_user ADD COLUMN email_verified_at timestamptz;

CREATE TABLE email_verification_token (
    id uuid PRIMARY KEY,
    user_id uuid NOT NULL REFERENCES app_user (id) ON DELETE CASCADE,
    token_hash text NOT NULL UNIQUE,
    expires_at timestamptz NOT NULL,
    used_at timestamptz,
    created_at timestamptz NOT NULL
);

CREATE INDEX ix_email_verification_token_user ON email_verification_token (user_id);
CREATE INDEX ix_email_verification_token_expiry ON email_verification_token (expires_at);
