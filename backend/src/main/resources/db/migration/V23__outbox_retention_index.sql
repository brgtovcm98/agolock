-- Outbox terminal 이벤트 보관/삭제 job을 보조하는 인덱스입니다.
CREATE INDEX IF NOT EXISTS ix_outbox_status_created_at
    ON outbox_event (status, created_at);
