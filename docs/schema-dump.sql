-- SeuStock final database schema dump
-- Source: src/main/resources/db/migration/V1__initial_schema.sql through V7__backfill_inherited_pricing.sql
-- Target: PostgreSQL
-- Note: V7 is a data backfill migration and does not change schema.

CREATE EXTENSION IF NOT EXISTS pgcrypto;

CREATE TABLE users (
    id          SERIAL PRIMARY KEY,
    external_id UUID UNIQUE NOT NULL DEFAULT gen_random_uuid(),
    email       VARCHAR(255) UNIQUE NOT NULL,
    nickname    VARCHAR(255) NOT NULL,
    password    VARCHAR(255) NOT NULL,
    created_at  TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE spaces (
    id          SERIAL PRIMARY KEY,
    external_id UUID UNIQUE NOT NULL DEFAULT gen_random_uuid(),
    user_id     INT NOT NULL,
    name        VARCHAR(255) NOT NULL,
    created_at  TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (user_id) REFERENCES users(id)
);

CREATE TABLE shelves (
    id          SERIAL PRIMARY KEY,
    external_id UUID UNIQUE NOT NULL DEFAULT gen_random_uuid(),
    space_id    INT NOT NULL,
    name        VARCHAR(255) NOT NULL,
    created_at  TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (space_id) REFERENCES spaces(id) ON DELETE CASCADE
);

CREATE TABLE boxes (
    id          SERIAL PRIMARY KEY,
    external_id UUID UNIQUE NOT NULL DEFAULT gen_random_uuid(),
    shelf_id    INT NOT NULL,
    name        VARCHAR(255) NOT NULL,
    created_at  TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (shelf_id) REFERENCES shelves(id) ON DELETE CASCADE
);

CREATE TABLE items (
    id                     SERIAL PRIMARY KEY,
    external_id            UUID UNIQUE NOT NULL DEFAULT gen_random_uuid(),
    user_id                INT NOT NULL,
    name                   VARCHAR(255) NOT NULL,
    description            TEXT,
    active                 BOOLEAN NOT NULL DEFAULT TRUE,
    created_at             TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    expiration_period_days INT,
    price                  NUMERIC(12,0),
    serial_mode            VARCHAR(10) NOT NULL DEFAULT 'NONE',
    serial_prefix          VARCHAR(100),
    serial_padding_length  INT NOT NULL DEFAULT 0,
    serial_increment_unit  INT NOT NULL DEFAULT 1,
    serial_next_sequence   BIGINT NOT NULL DEFAULT 0,
    lot_mode               VARCHAR(10) NOT NULL DEFAULT 'NONE',
    lot_vendor_code        VARCHAR(100),
    lot_date_format        VARCHAR(32) DEFAULT 'yyyyMMdd',
    lot_include_sequence   BOOLEAN NOT NULL DEFAULT TRUE,
    lot_sequence_key       VARCHAR(32),
    lot_next_sequence      INT NOT NULL DEFAULT 0,
    FOREIGN KEY (user_id) REFERENCES users(id),
    CONSTRAINT chk_items_expiration_period_days
        CHECK (expiration_period_days IS NULL OR expiration_period_days > 0),
    CONSTRAINT chk_items_price
        CHECK (price IS NULL OR price >= 0),
    CONSTRAINT chk_items_serial_mode
        CHECK (serial_mode IN ('NONE', 'AUTO', 'MANUAL')),
    CONSTRAINT chk_items_lot_mode
        CHECK (lot_mode IN ('NONE', 'AUTO', 'MANUAL')),
    CONSTRAINT chk_items_serial_padding_length
        CHECK (serial_padding_length >= 0),
    CONSTRAINT chk_items_serial_increment_unit
        CHECK (serial_increment_unit > 0),
    CONSTRAINT chk_items_serial_next_sequence
        CHECK (serial_next_sequence >= 0),
    CONSTRAINT chk_items_lot_next_sequence
        CHECK (lot_next_sequence >= 0)
);

CREATE TABLE item_lots (
    id              SERIAL PRIMARY KEY,
    external_id     UUID UNIQUE NOT NULL DEFAULT gen_random_uuid(),
    item_id         INT NOT NULL,
    lot_number      VARCHAR(255) NOT NULL,
    expiration_date DATE,
    created_at      TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (item_id) REFERENCES items(id) ON DELETE CASCADE,
    CONSTRAINT uq_item_lots_item_lot_number UNIQUE (item_id, lot_number)
);

CREATE INDEX idx_item_lots_item_id
    ON item_lots (item_id);

CREATE TABLE stocks (
    id              SERIAL PRIMARY KEY,
    external_id     UUID UNIQUE NOT NULL DEFAULT gen_random_uuid(),
    item_id         INT NOT NULL,
    space_id        INT NOT NULL,
    shelf_id        INT,
    box_id          INT,
    serial_number   VARCHAR(255),
    lot_number      VARCHAR(255),
    expiration_date DATE,
    memo            TEXT,
    status          VARCHAR(20) NOT NULL DEFAULT 'IN_STOCK',
    created_at      TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    lot_id          INT,
    is_kept         BOOLEAN NOT NULL DEFAULT FALSE,
    price           NUMERIC(12,0),
    FOREIGN KEY (item_id) REFERENCES items(id),
    FOREIGN KEY (space_id) REFERENCES spaces(id),
    FOREIGN KEY (shelf_id) REFERENCES shelves(id) ON DELETE CASCADE,
    FOREIGN KEY (box_id) REFERENCES boxes(id) ON DELETE CASCADE,
    CONSTRAINT fk_stocks_lot_id
        FOREIGN KEY (lot_id) REFERENCES item_lots(id) ON DELETE SET NULL,
    CONSTRAINT chk_box_requires_shelf
        CHECK (box_id IS NULL OR shelf_id IS NOT NULL),
    CONSTRAINT chk_stock_status
        CHECK (status IN ('IN_STOCK', 'DISPATCHED', 'LOST', 'DAMAGED', 'DISPOSED')),
    CONSTRAINT chk_stocks_price
        CHECK (price IS NULL OR price >= 0),
    CONSTRAINT uq_stocks_item_serial UNIQUE (item_id, serial_number)
);

CREATE INDEX idx_stocks_lot_id
    ON stocks (lot_id);

CREATE INDEX idx_stocks_available
    ON stocks (space_id, status, is_kept)
    WHERE status = 'IN_STOCK' AND is_kept = FALSE;

CREATE TABLE stock_transactions (
    id               SERIAL PRIMARY KEY,
    external_id      UUID UNIQUE NOT NULL DEFAULT gen_random_uuid(),
    stock_id         INT NOT NULL,
    transaction_type VARCHAR(20) NOT NULL,
    from_space_id    INT,
    from_shelf_id    INT,
    from_box_id      INT,
    to_space_id      INT,
    to_shelf_id      INT,
    to_box_id        INT,
    memo             TEXT,
    created_at       TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (stock_id) REFERENCES stocks(id) ON DELETE CASCADE,
    FOREIGN KEY (from_space_id) REFERENCES spaces(id),
    FOREIGN KEY (from_shelf_id) REFERENCES shelves(id),
    FOREIGN KEY (from_box_id) REFERENCES boxes(id),
    FOREIGN KEY (to_space_id) REFERENCES spaces(id),
    FOREIGN KEY (to_shelf_id) REFERENCES shelves(id),
    FOREIGN KEY (to_box_id) REFERENCES boxes(id),
    CONSTRAINT chk_transaction_type
        CHECK (transaction_type IN ('IN', 'OUT', 'MOVE', 'ADJUST'))
);

CREATE TABLE images (
    id                SERIAL PRIMARY KEY,
    external_id       UUID UNIQUE NOT NULL DEFAULT gen_random_uuid(),
    user_id           INT NOT NULL,
    storage_path      TEXT NOT NULL,
    original_filename VARCHAR(255),
    content_type      VARCHAR(100),
    size_bytes        BIGINT,
    content_hash      VARCHAR(64),
    created_at        TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE,
    CONSTRAINT uq_images_user_hash UNIQUE (user_id, content_hash)
);

CREATE TABLE item_images (
    item_id       INT NOT NULL,
    image_id      INT NOT NULL,
    display_order INT NOT NULL DEFAULT 0,
    is_primary    BOOLEAN NOT NULL DEFAULT FALSE,
    created_at    TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (item_id, image_id),
    FOREIGN KEY (item_id) REFERENCES items(id) ON DELETE CASCADE,
    FOREIGN KEY (image_id) REFERENCES images(id) ON DELETE CASCADE
);

CREATE TABLE stock_images (
    stock_id      INT NOT NULL,
    image_id      INT NOT NULL,
    display_order INT NOT NULL DEFAULT 0,
    is_primary    BOOLEAN NOT NULL DEFAULT FALSE,
    created_at    TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (stock_id, image_id),
    FOREIGN KEY (stock_id) REFERENCES stocks(id) ON DELETE CASCADE,
    FOREIGN KEY (image_id) REFERENCES images(id) ON DELETE CASCADE
);
