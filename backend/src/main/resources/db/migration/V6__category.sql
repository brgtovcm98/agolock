CREATE TABLE category (
    id uuid PRIMARY KEY,
    space_id uuid NOT NULL REFERENCES space (id),
    name text NOT NULL,
    normalized_name text NOT NULL,
    color text,
    sort_order integer NOT NULL DEFAULT 0,
    created_at timestamptz NOT NULL,
    updated_at timestamptz NOT NULL,
    deleted_at timestamptz,
    CONSTRAINT uq_category_id_space UNIQUE (id, space_id)
);

CREATE UNIQUE INDEX ux_category_space_normalized_active
    ON category (space_id, normalized_name)
    WHERE deleted_at IS NULL;

CREATE INDEX ix_category_space_sort
    ON category (space_id, sort_order, name, id)
    WHERE deleted_at IS NULL;
