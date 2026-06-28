-- stocks 테이블 검색 및 필터 성능 최적화 인덱스
-- FK 자동 인덱스가 생성되지 않는 PostgreSQL 특성상 명시적 추가

CREATE INDEX IF NOT EXISTS idx_stocks_item_id_status
    ON stocks (item_id, status);

CREATE INDEX IF NOT EXISTS idx_stocks_space_id_status_shelf_null
    ON stocks (space_id, status) WHERE shelf_id IS NULL;

CREATE INDEX IF NOT EXISTS idx_stocks_shelf_id_status_box_null
    ON stocks (shelf_id, status) WHERE box_id IS NULL;

CREATE INDEX IF NOT EXISTS idx_stocks_box_id_status
    ON stocks (box_id, status);

CREATE INDEX IF NOT EXISTS idx_stocks_serial_number
    ON stocks (serial_number) WHERE serial_number IS NOT NULL;

-- stock_transactions 조회 성능 (거래 이력 검색)
CREATE INDEX IF NOT EXISTS idx_stock_transactions_stock_id
    ON stock_transactions (stock_id);

CREATE INDEX IF NOT EXISTS idx_stock_transactions_user_type
    ON stock_transactions (transaction_type);

-- images content_hash 검색 최적화 (이미지 중복 확인)
CREATE INDEX IF NOT EXISTS idx_images_user_id_content_hash
    ON images (user_id, content_hash) WHERE content_hash IS NOT NULL;
