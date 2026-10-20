-- M2 Room, Shelf, Box hierarchy with composite tenant FKs.
CREATE TABLE room (
    id uuid PRIMARY KEY,
    space_id uuid NOT NULL REFERENCES space (id),
    name text NOT NULL,
    description text,
    version bigint NOT NULL DEFAULT 0,
    created_at timestamptz NOT NULL,
    updated_at timestamptz NOT NULL,
    deleted_at timestamptz,
    CONSTRAINT uq_room_id_space UNIQUE (id, space_id)
);

CREATE INDEX ix_room_space_name
    ON room (space_id, name, id)
    WHERE deleted_at IS NULL;

CREATE TABLE shelf (
    id uuid PRIMARY KEY,
    space_id uuid NOT NULL,
    room_id uuid NOT NULL,
    name text NOT NULL,
    description text,
    version bigint NOT NULL DEFAULT 0,
    created_at timestamptz NOT NULL,
    updated_at timestamptz NOT NULL,
    deleted_at timestamptz,
    CONSTRAINT uq_shelf_id_space UNIQUE (id, space_id),
    CONSTRAINT fk_shelf_room_space
        FOREIGN KEY (room_id, space_id)
        REFERENCES room (id, space_id)
);

CREATE INDEX ix_shelf_space_room_name
    ON shelf (space_id, room_id, name, id)
    WHERE deleted_at IS NULL;

CREATE TABLE box (
    id uuid PRIMARY KEY,
    space_id uuid NOT NULL,
    shelf_id uuid NOT NULL,
    name text NOT NULL,
    description text,
    version bigint NOT NULL DEFAULT 0,
    created_at timestamptz NOT NULL,
    updated_at timestamptz NOT NULL,
    deleted_at timestamptz,
    CONSTRAINT uq_box_id_space UNIQUE (id, space_id),
    CONSTRAINT fk_box_shelf_space
        FOREIGN KEY (shelf_id, space_id)
        REFERENCES shelf (id, space_id)
);

CREATE INDEX ix_box_space_shelf_name
    ON box (space_id, shelf_id, name, id)
    WHERE deleted_at IS NULL;
