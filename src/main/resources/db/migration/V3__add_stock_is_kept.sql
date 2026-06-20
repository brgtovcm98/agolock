ALTER TABLE stocks
    ADD COLUMN is_kept BOOLEAN NOT NULL DEFAULT FALSE;

CREATE INDEX idx_stocks_available
    ON stocks (space_id, status, is_kept)
    WHERE status = 'IN_STOCK' AND is_kept = FALSE;
