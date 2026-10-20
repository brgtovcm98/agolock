CREATE TABLE audit_log (
    id uuid PRIMARY KEY,
    space_id uuid NOT NULL REFERENCES space (id),
    actor_user_id uuid NOT NULL REFERENCES app_user (id),
    action text NOT NULL,
    resource_type text NOT NULL,
    resource_id uuid,
    metadata jsonb,
    created_at timestamptz NOT NULL
);

CREATE INDEX ix_audit_space_created ON audit_log (space_id, created_at DESC);
CREATE INDEX ix_audit_resource ON audit_log (resource_type, resource_id);
