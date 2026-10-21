CREATE TYPE image_status AS ENUM ('UPLOADING', 'DRAFT', 'PENDING', 'PROCESSING', 'READY', 'FAILED', 'DELETED');

CREATE TABLE image (
    id uuid PRIMARY KEY,
    space_id uuid NOT NULL REFERENCES space (id),
    item_id uuid,
    inventory_id uuid,
    object_key text NOT NULL UNIQUE,
    thumbnail_key text UNIQUE,
    declared_mime_type text,
    declared_size_bytes integer,
    mime_type text,
    size_bytes integer,
    width integer,
    height integer,
    sha256 text,
    is_primary boolean NOT NULL DEFAULT false,
    status image_status NOT NULL DEFAULT 'UPLOADING',
    upload_expires_at timestamptz NOT NULL,
    upload_completed_at timestamptz,
    processed_at timestamptz,
    processing_attempts integer NOT NULL DEFAULT 0,
    last_error text,
    embedding vector(1408),
    embedding_model text,
    embedding_version text,
    created_by uuid NOT NULL REFERENCES app_user (id),
    created_at timestamptz NOT NULL,
    updated_at timestamptz NOT NULL,
    deleted_at timestamptz,
    CONSTRAINT ck_image_one_target CHECK (num_nonnulls(item_id, inventory_id) <= 1),
    CONSTRAINT ck_image_draft_target CHECK (status <> 'DRAFT' OR num_nonnulls(item_id, inventory_id) = 0),
    CONSTRAINT ck_image_processed_target CHECK (
        status NOT IN ('PENDING', 'PROCESSING', 'READY', 'FAILED')
        OR num_nonnulls(item_id, inventory_id) = 1
    ),
    CONSTRAINT ck_image_ready_fields CHECK (
        status <> 'READY'
        OR (mime_type IS NOT NULL AND size_bytes IS NOT NULL AND width IS NOT NULL
            AND height IS NOT NULL AND sha256 IS NOT NULL AND thumbnail_key IS NOT NULL
            AND processed_at IS NOT NULL)
    ),
    CONSTRAINT ck_image_primary_ready CHECK (
        NOT is_primary OR (status = 'READY' AND deleted_at IS NULL AND num_nonnulls(item_id, inventory_id) = 1)
    ),
    CONSTRAINT ck_image_sha256 CHECK (sha256 IS NULL OR sha256 ~ '^[0-9a-f]{64}$'),
    CONSTRAINT ck_image_upload_expiry CHECK (upload_expires_at > created_at),
    CONSTRAINT ck_image_attempts CHECK (processing_attempts >= 0),
    CONSTRAINT ck_image_last_error CHECK (last_error IS NULL OR char_length(last_error) <= 500),
    CONSTRAINT fk_image_item_space FOREIGN KEY (item_id, space_id) REFERENCES item (id, space_id),
    CONSTRAINT fk_image_inventory_space FOREIGN KEY (inventory_id, space_id) REFERENCES inventory (id, space_id)
);

CREATE UNIQUE INDEX ux_image_primary_item ON image (item_id) WHERE is_primary AND deleted_at IS NULL;
CREATE UNIQUE INDEX ux_image_primary_inventory ON image (inventory_id) WHERE is_primary AND deleted_at IS NULL;
CREATE INDEX ix_image_item_created ON image (item_id, created_at DESC) WHERE deleted_at IS NULL;
CREATE INDEX ix_image_inventory_created ON image (inventory_id, created_at DESC) WHERE deleted_at IS NULL;
CREATE INDEX ix_image_status_updated ON image (status, updated_at);
CREATE INDEX ix_image_status_expiry ON image (status, upload_expires_at);
CREATE INDEX ix_image_space_sha256 ON image (space_id, sha256);
