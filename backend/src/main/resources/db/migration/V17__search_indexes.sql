CREATE INDEX IF NOT EXISTS idx_item_name_trgm
    ON item USING gin (lower(name) gin_trgm_ops)
    WHERE deleted_at IS NULL;

CREATE INDEX IF NOT EXISTS idx_inventory_name_override_trgm
    ON inventory USING gin (lower(name_override) gin_trgm_ops)
    WHERE name_override IS NOT NULL AND deleted_at IS NULL;

CREATE INDEX IF NOT EXISTS idx_room_name_trgm
    ON room USING gin (lower(name) gin_trgm_ops)
    WHERE deleted_at IS NULL;

CREATE INDEX IF NOT EXISTS idx_shelf_name_trgm
    ON shelf USING gin (lower(name) gin_trgm_ops)
    WHERE deleted_at IS NULL;

CREATE INDEX IF NOT EXISTS idx_box_name_trgm
    ON box USING gin (lower(name) gin_trgm_ops)
    WHERE deleted_at IS NULL;

CREATE INDEX IF NOT EXISTS idx_share_grantee_active
    ON share (grantee_user_id, permission)
    WHERE revoked_at IS NULL;
