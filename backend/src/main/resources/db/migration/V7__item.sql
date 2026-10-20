CREATE TABLE item (
    id uuid PRIMARY KEY,
    space_id uuid NOT NULL REFERENCES space (id),
    name text NOT NULL,
    description text,
    category_id uuid,
    price_amount integer,
    currency char(3) NOT NULL DEFAULT 'KRW',
    embedding vector(1408),
    embedding_model text,
    embedding_version text,
    version bigint NOT NULL DEFAULT 0,
    created_at timestamptz NOT NULL,
    updated_at timestamptz NOT NULL,
    deleted_at timestamptz,
    CONSTRAINT uq_item_id_space UNIQUE (id, space_id),
    CONSTRAINT ck_item_price_nonnegative CHECK (price_amount IS NULL OR price_amount >= 0),
    CONSTRAINT ck_item_currency_krw CHECK (currency = 'KRW'),
    CONSTRAINT fk_item_category_space
        FOREIGN KEY (category_id, space_id)
        REFERENCES category (id, space_id)
);

CREATE INDEX ix_item_space_category ON item (space_id, category_id);
CREATE INDEX ix_item_space_updated ON item (space_id, updated_at DESC, id DESC) WHERE deleted_at IS NULL;
CREATE INDEX ix_item_name_trgm ON item USING gin (lower(name) gin_trgm_ops);
