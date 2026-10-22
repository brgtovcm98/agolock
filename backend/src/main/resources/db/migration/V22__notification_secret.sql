-- 메일 링크 토큰을 outbox payload에 평문으로 남기지 않기 위한 암호문 저장소입니다.
CREATE TABLE notification_secret (
    id UUID PRIMARY KEY,
    space_id UUID NOT NULL REFERENCES space (id),
    secret_type TEXT NOT NULL,
    ciphertext BYTEA NOT NULL,
    nonce BYTEA NOT NULL,
    expires_at TIMESTAMPTZ NOT NULL,
    created_at TIMESTAMPTZ NOT NULL
);

CREATE INDEX ix_notification_secret_expires_at
    ON notification_secret (expires_at);
