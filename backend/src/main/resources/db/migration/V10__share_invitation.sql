CREATE TYPE permission AS ENUM ('VIEW', 'EDIT', 'MANAGE');
CREATE TYPE invitation_status AS ENUM ('PENDING', 'ACCEPTED', 'DECLINED', 'REVOKED', 'EXPIRED');
CREATE TYPE outbox_status AS ENUM ('PENDING', 'PUBLISHED', 'FAILED');

CREATE TABLE share_invitation (
    id uuid PRIMARY KEY,
    space_id uuid NOT NULL REFERENCES space (id),
    room_id uuid,
    shelf_id uuid,
    invited_email citext NOT NULL,
    invited_user_id uuid REFERENCES app_user (id),
    permission permission NOT NULL,
    token_hash text NOT NULL UNIQUE,
    status invitation_status NOT NULL DEFAULT 'PENDING',
    expires_at timestamptz NOT NULL,
    created_by uuid NOT NULL REFERENCES app_user (id),
    created_at timestamptz NOT NULL,
    accepted_at timestamptz,
    declined_at timestamptz,
    revoked_at timestamptz,
    CONSTRAINT ck_invitation_one_target CHECK (num_nonnulls(room_id, shelf_id) = 1),
    CONSTRAINT ck_invitation_expiry CHECK (expires_at > created_at),
    CONSTRAINT ck_invitation_terminal_times CHECK (
        (status <> 'ACCEPTED' OR accepted_at IS NOT NULL) AND
        (status <> 'DECLINED' OR declined_at IS NOT NULL) AND
        (status <> 'REVOKED' OR revoked_at IS NOT NULL)
    ),
    CONSTRAINT fk_invitation_room_space FOREIGN KEY (room_id, space_id) REFERENCES room (id, space_id),
    CONSTRAINT fk_invitation_shelf_space FOREIGN KEY (shelf_id, space_id) REFERENCES shelf (id, space_id)
);

CREATE UNIQUE INDEX ux_invitation_room_email_pending
    ON share_invitation (room_id, invited_email)
    WHERE status = 'PENDING' AND room_id IS NOT NULL;
CREATE UNIQUE INDEX ux_invitation_shelf_email_pending
    ON share_invitation (shelf_id, invited_email)
    WHERE status = 'PENDING' AND shelf_id IS NOT NULL;
CREATE INDEX ix_invitation_space_status ON share_invitation (space_id, status, created_at DESC);
CREATE INDEX ix_invitation_email_status ON share_invitation (invited_email, status, created_at DESC);
CREATE INDEX ix_invitation_expiry ON share_invitation (status, expires_at);
