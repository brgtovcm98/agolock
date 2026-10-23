CREATE INDEX idx_item_description_trgm
    ON item USING gin (lower(description) gin_trgm_ops)
    WHERE description IS NOT NULL AND deleted_at IS NULL;

CREATE INDEX idx_inventory_description_override_trgm
    ON inventory USING gin (lower(description_override) gin_trgm_ops)
    WHERE description_override IS NOT NULL AND deleted_at IS NULL;
