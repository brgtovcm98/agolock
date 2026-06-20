ALTER TABLE items
    ADD COLUMN serial_mode VARCHAR(10) NOT NULL DEFAULT 'NONE',
    ADD COLUMN serial_prefix VARCHAR(100),
    ADD COLUMN serial_padding_length INT NOT NULL DEFAULT 0,
    ADD COLUMN serial_increment_unit INT NOT NULL DEFAULT 1,
    ADD COLUMN serial_next_sequence BIGINT NOT NULL DEFAULT 0,
    ADD COLUMN lot_mode VARCHAR(10) NOT NULL DEFAULT 'NONE',
    ADD COLUMN lot_vendor_code VARCHAR(100),
    ADD COLUMN lot_date_format VARCHAR(32) DEFAULT 'yyyyMMdd',
    ADD COLUMN lot_include_sequence BOOLEAN NOT NULL DEFAULT TRUE,
    ADD COLUMN lot_sequence_key VARCHAR(32),
    ADD COLUMN lot_next_sequence INT NOT NULL DEFAULT 0;

ALTER TABLE items
    ADD CONSTRAINT chk_items_serial_mode
        CHECK (serial_mode IN ('NONE', 'AUTO', 'MANUAL')),
    ADD CONSTRAINT chk_items_lot_mode
        CHECK (lot_mode IN ('NONE', 'AUTO', 'MANUAL')),
    ADD CONSTRAINT chk_items_serial_padding_length
        CHECK (serial_padding_length >= 0),
    ADD CONSTRAINT chk_items_serial_increment_unit
        CHECK (serial_increment_unit > 0),
    ADD CONSTRAINT chk_items_serial_next_sequence
        CHECK (serial_next_sequence >= 0),
    ADD CONSTRAINT chk_items_lot_next_sequence
        CHECK (lot_next_sequence >= 0);

ALTER TABLE item_lots
    DROP CONSTRAINT IF EXISTS item_lots_lot_policy_id_fkey;

ALTER TABLE item_lots
    DROP COLUMN IF EXISTS lot_policy_id;

DROP TABLE IF EXISTS item_serial_policies;
DROP TABLE IF EXISTS item_lot_policies;

ALTER TABLE stocks
    ADD CONSTRAINT uq_stocks_item_serial UNIQUE (item_id, serial_number);
