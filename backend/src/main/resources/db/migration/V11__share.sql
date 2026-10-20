CREATE TABLE share (
    id uuid PRIMARY KEY,
    space_id uuid NOT NULL REFERENCES space (id),
    room_id uuid,
    shelf_id uuid,
    grantee_user_id uuid NOT NULL REFERENCES app_user (id),
    permission permission NOT NULL,
    granted_by uuid NOT NULL REFERENCES app_user (id),
    created_at timestamptz NOT NULL,
    updated_at timestamptz NOT NULL,
    revoked_at timestamptz,
    CONSTRAINT ck_share_one_target CHECK (num_nonnulls(room_id, shelf_id) = 1),
    CONSTRAINT fk_share_room_space FOREIGN KEY (room_id, space_id) REFERENCES room (id, space_id),
    CONSTRAINT fk_share_shelf_space FOREIGN KEY (shelf_id, space_id) REFERENCES shelf (id, space_id)
);

CREATE UNIQUE INDEX ux_share_room_grantee_active
    ON share (room_id, grantee_user_id)
    WHERE revoked_at IS NULL AND room_id IS NOT NULL;
CREATE UNIQUE INDEX ux_share_shelf_grantee_active
    ON share (shelf_id, grantee_user_id)
    WHERE revoked_at IS NULL AND shelf_id IS NOT NULL;
CREATE INDEX ix_share_grantee_permission ON share (grantee_user_id, permission) WHERE revoked_at IS NULL;
CREATE INDEX ix_share_space_room ON share (space_id, room_id);
CREATE INDEX ix_share_space_shelf ON share (space_id, shelf_id);
