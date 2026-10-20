-- M2 Idempotency-Key foundation, reused by later create endpoints.
CREATE TABLE idempotency_record (
    id uuid PRIMARY KEY,
    user_id uuid NOT NULL REFERENCES app_user (id),
    idempotency_key text NOT NULL,
    request_method text NOT NULL,
    request_path text NOT NULL,
    request_hash char(64) NOT NULL,
    response_status integer NOT NULL,
    response_body jsonb NOT NULL,
    created_at timestamptz NOT NULL,
    expires_at timestamptz NOT NULL,
    CONSTRAINT uq_idempotency_user_key UNIQUE (user_id, idempotency_key),
    CONSTRAINT ck_idempotency_expiry CHECK (expires_at > created_at)
);

CREATE INDEX ix_idempotency_expiry ON idempotency_record (expires_at);
