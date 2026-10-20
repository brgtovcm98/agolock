CREATE TABLE outbox_event (
    id uuid PRIMARY KEY,
    space_id uuid REFERENCES space (id),
    aggregate_type text NOT NULL,
    aggregate_id uuid NOT NULL,
    event_type text NOT NULL,
    payload jsonb NOT NULL,
    status outbox_status NOT NULL DEFAULT 'PENDING',
    attempts integer NOT NULL DEFAULT 0,
    available_at timestamptz NOT NULL,
    published_at timestamptz,
    last_error text,
    created_at timestamptz NOT NULL
);

CREATE INDEX ix_outbox_status_available ON outbox_event (status, available_at);
CREATE INDEX ix_outbox_space_type ON outbox_event (space_id, event_type);
