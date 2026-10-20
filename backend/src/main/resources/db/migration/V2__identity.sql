-- M1 Identity, Space, invite code, password reset foundations.
CREATE TYPE user_status AS ENUM ('ACTIVE', 'LOCKED', 'DELETED');

CREATE TABLE app_user (
    id uuid PRIMARY KEY,
    email citext NOT NULL,
    password_hash text NOT NULL,
    display_name text NOT NULL,
    status user_status NOT NULL DEFAULT 'ACTIVE',
    created_at timestamptz NOT NULL,
    updated_at timestamptz NOT NULL,
    deleted_at timestamptz
);

-- soft delete 후 같은 이메일을 다시 사용할 수 있도록 활성 행만 유일합니다.
CREATE UNIQUE INDEX ux_app_user_email_active
    ON app_user (email)
    WHERE deleted_at IS NULL;

CREATE TABLE space (
    id uuid PRIMARY KEY,
    owner_user_id uuid NOT NULL UNIQUE REFERENCES app_user (id),
    name text NOT NULL,
    created_at timestamptz NOT NULL,
    updated_at timestamptz NOT NULL,
    deleted_at timestamptz
);

CREATE TABLE invite_code (
    id uuid PRIMARY KEY,
    code_hash text NOT NULL UNIQUE,
    label text,
    max_uses integer NOT NULL CHECK (max_uses > 0),
    used_count integer NOT NULL DEFAULT 0 CHECK (used_count >= 0),
    expires_at timestamptz,
    revoked_at timestamptz,
    created_by uuid REFERENCES app_user (id),
    created_at timestamptz NOT NULL,
    CONSTRAINT ck_invite_code_used_count CHECK (used_count <= max_uses)
);

CREATE TABLE invite_code_use (
    id uuid PRIMARY KEY,
    invite_code_id uuid NOT NULL REFERENCES invite_code (id),
    user_id uuid NOT NULL REFERENCES app_user (id),
    used_at timestamptz NOT NULL,
    CONSTRAINT ux_invite_code_use UNIQUE (invite_code_id, user_id)
);

CREATE TABLE password_reset_token (
    id uuid PRIMARY KEY,
    user_id uuid NOT NULL REFERENCES app_user (id) ON DELETE CASCADE,
    token_hash text NOT NULL UNIQUE,
    expires_at timestamptz NOT NULL,
    used_at timestamptz,
    created_at timestamptz NOT NULL
);

CREATE INDEX ix_password_reset_token_user
    ON password_reset_token (user_id);

CREATE INDEX ix_password_reset_token_expiry
    ON password_reset_token (expires_at);
