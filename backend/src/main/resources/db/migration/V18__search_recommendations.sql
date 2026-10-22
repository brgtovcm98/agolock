CREATE TABLE recent_search (
    id uuid PRIMARY KEY,
    user_id uuid NOT NULL REFERENCES app_user (id),
    query text NOT NULL,
    normalized_query text NOT NULL,
    last_searched_at timestamptz NOT NULL,
    CONSTRAINT uq_recent_search_user_query UNIQUE (user_id, normalized_query)
);

CREATE INDEX ix_recent_search_user_time
    ON recent_search (user_id, last_searched_at DESC);

CREATE TABLE user_item_view (
    user_id uuid NOT NULL REFERENCES app_user (id),
    item_id uuid NOT NULL,
    space_id uuid NOT NULL REFERENCES space (id),
    view_count bigint NOT NULL DEFAULT 1,
    last_viewed_at timestamptz NOT NULL,
    PRIMARY KEY (user_id, item_id),
    CONSTRAINT fk_user_item_view_item_space
        FOREIGN KEY (item_id, space_id) REFERENCES item (id, space_id)
);

CREATE INDEX ix_user_item_view_user_time
    ON user_item_view (user_id, last_viewed_at DESC);

CREATE INDEX ix_user_item_view_user_count
    ON user_item_view (user_id, view_count DESC);
