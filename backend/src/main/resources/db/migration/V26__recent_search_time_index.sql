-- 최근 검색 30일 만료 정리 job을 보조하는 인덱스입니다.
CREATE INDEX IF NOT EXISTS ix_recent_search_time
    ON recent_search (last_searched_at);
