CREATE TYPE inventory_status AS ENUM ('IN_USE', 'STORED', 'DISPOSED');
CREATE TYPE embedding_source AS ENUM ('MASTER', 'INVENTORY');

CREATE TABLE inventory (
    id uuid PRIMARY KEY,
    space_id uuid NOT NULL REFERENCES space (id),
    item_id uuid NOT NULL,
    room_id uuid,
    shelf_id uuid,
    box_id uuid,
    name_override text,
    description_override text,
    category_id_override uuid,
    price_amount_override integer,
    status inventory_status NOT NULL DEFAULT 'STORED',
    effective_embedding vector(1408),
    embedding_source embedding_source,
    embedding_model text,
    embedding_version text,
    embedding_updated_at timestamptz,
    version bigint NOT NULL DEFAULT 0,
    created_at timestamptz NOT NULL,
    updated_at timestamptz NOT NULL,
    deleted_at timestamptz,
    CONSTRAINT uq_inventory_id_space UNIQUE (id, space_id),
    CONSTRAINT ck_inventory_one_location CHECK (num_nonnulls(room_id, shelf_id, box_id) = 1),
    CONSTRAINT ck_inventory_price_nonnegative CHECK (price_amount_override IS NULL OR price_amount_override >= 0),
    CONSTRAINT fk_inventory_item_space FOREIGN KEY (item_id, space_id) REFERENCES item (id, space_id),
    CONSTRAINT fk_inventory_room_space FOREIGN KEY (room_id, space_id) REFERENCES room (id, space_id),
    CONSTRAINT fk_inventory_shelf_space FOREIGN KEY (shelf_id, space_id) REFERENCES shelf (id, space_id),
    CONSTRAINT fk_inventory_box_space FOREIGN KEY (box_id, space_id) REFERENCES box (id, space_id),
    CONSTRAINT fk_inventory_category_space FOREIGN KEY (category_id_override, space_id) REFERENCES category (id, space_id)
);

CREATE INDEX ix_inventory_space_item ON inventory (space_id, item_id) WHERE deleted_at IS NULL;
CREATE INDEX ix_inventory_space_status ON inventory (space_id, status, updated_at DESC, id DESC) WHERE deleted_at IS NULL;
CREATE INDEX ix_inventory_space_category ON inventory (space_id, category_id_override) WHERE deleted_at IS NULL;
CREATE INDEX ix_inventory_space_room ON inventory (space_id, room_id) WHERE deleted_at IS NULL;
CREATE INDEX ix_inventory_space_shelf ON inventory (space_id, shelf_id) WHERE deleted_at IS NULL;
CREATE INDEX ix_inventory_space_box ON inventory (space_id, box_id) WHERE deleted_at IS NULL;
CREATE INDEX ix_inventory_space_updated ON inventory (space_id, updated_at DESC, id DESC) WHERE deleted_at IS NULL;
