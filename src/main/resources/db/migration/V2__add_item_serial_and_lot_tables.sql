ALTER TABLE items
    ADD COLUMN expiration_period_days INT;

ALTER TABLE items
    ADD CONSTRAINT chk_items_expiration_period_days
        CHECK (expiration_period_days IS NULL OR expiration_period_days > 0);

CREATE TABLE item_serial_policies (
    id               SERIAL PRIMARY KEY,
    external_id      UUID UNIQUE NOT NULL DEFAULT gen_random_uuid(),
    item_id          INT NOT NULL,
    serial_name      VARCHAR(255) NOT NULL,
    prefix           VARCHAR(100),
    suffix           VARCHAR(100),
    increment_unit   INT NOT NULL DEFAULT 1,
    padding_length   INT NOT NULL DEFAULT 0,
    current_sequence BIGINT NOT NULL DEFAULT 0,
    active           BOOLEAN NOT NULL DEFAULT TRUE,
    created_at       TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at       TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (item_id) REFERENCES items(id) ON DELETE CASCADE,
    CONSTRAINT chk_item_serial_policies_increment_unit CHECK (increment_unit > 0),
    CONSTRAINT chk_item_serial_policies_padding_length CHECK (padding_length >= 0),
    CONSTRAINT chk_item_serial_policies_current_sequence CHECK (current_sequence >= 0)
);

CREATE INDEX idx_item_serial_policies_item_id
    ON item_serial_policies (item_id);

CREATE TABLE item_lot_policies (
    id               SERIAL PRIMARY KEY,
    external_id      UUID UNIQUE NOT NULL DEFAULT gen_random_uuid(),
    item_id          INT NOT NULL,
    lot_name         VARCHAR(255) NOT NULL,
    vendor_code      VARCHAR(100),
    date_format      VARCHAR(32),
    include_sequence BOOLEAN NOT NULL DEFAULT TRUE,
    last_sequence_key VARCHAR(32),
    current_sequence INT NOT NULL DEFAULT 0,
    active           BOOLEAN NOT NULL DEFAULT TRUE,
    created_at       TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at       TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (item_id) REFERENCES items(id) ON DELETE CASCADE,
    CONSTRAINT chk_item_lot_policies_current_sequence CHECK (current_sequence >= 0)
);

CREATE INDEX idx_item_lot_policies_item_id
    ON item_lot_policies (item_id);

CREATE TABLE item_lots (
    id              SERIAL PRIMARY KEY,
    external_id     UUID UNIQUE NOT NULL DEFAULT gen_random_uuid(),
    item_id         INT NOT NULL,
    lot_policy_id   INT,
    lot_number      VARCHAR(255) NOT NULL,
    expiration_date DATE,
    created_at      TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (item_id) REFERENCES items(id) ON DELETE CASCADE,
    FOREIGN KEY (lot_policy_id) REFERENCES item_lot_policies(id) ON DELETE SET NULL,
    CONSTRAINT uq_item_lots_item_lot_number UNIQUE (item_id, lot_number)
);

CREATE INDEX idx_item_lots_item_id
    ON item_lots (item_id);

ALTER TABLE stocks
    ADD COLUMN lot_id INT;

ALTER TABLE stocks
    ADD CONSTRAINT fk_stocks_lot_id
        FOREIGN KEY (lot_id) REFERENCES item_lots(id) ON DELETE SET NULL;

CREATE INDEX idx_stocks_lot_id
    ON stocks (lot_id);
