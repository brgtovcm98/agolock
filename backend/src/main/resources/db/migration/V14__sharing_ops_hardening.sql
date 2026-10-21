ALTER TABLE outbox_event
    ADD COLUMN locked_by text,
    ADD COLUMN locked_until timestamptz,
    ADD COLUMN claim_token uuid,
    ADD COLUMN schema_version integer NOT NULL DEFAULT 1,
    ADD CONSTRAINT ck_outbox_lease CHECK (
        (locked_by IS NULL AND locked_until IS NULL AND claim_token IS NULL)
        OR (locked_by IS NOT NULL AND locked_until IS NOT NULL AND claim_token IS NOT NULL)
    ),
    ADD CONSTRAINT ck_outbox_schema_version CHECK (schema_version >= 1);

CREATE INDEX ix_outbox_aggregate_created
    ON outbox_event (aggregate_type, aggregate_id, created_at DESC, id DESC);
CREATE INDEX ix_outbox_claim
    ON outbox_event (available_at, id)
    WHERE status = 'PENDING';
CREATE INDEX ix_invitation_space_created_id
    ON share_invitation (space_id, created_at DESC, id DESC);
CREATE INDEX ix_invitation_email_created_id
    ON share_invitation (invited_email, created_at DESC, id DESC);
CREATE INDEX ix_share_space_created_id
    ON share (space_id, created_at DESC, id DESC);
