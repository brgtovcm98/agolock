-- 검색 keyset 페이지와 공유 ACL 조회를 보조하는 인덱스입니다.
-- CONCURRENTLY는 Flyway 트랜잭션과 충돌하므로 일반 CREATE로 만듭니다.
CREATE INDEX IF NOT EXISTS ix_share_grantee_space
  ON share (grantee_user_id, space_id) WHERE revoked_at IS NULL;
CREATE INDEX IF NOT EXISTS ix_item_name_lower_id
  ON item (lower(name), id) WHERE deleted_at IS NULL;
CREATE INDEX IF NOT EXISTS ix_inventory_item_updated
  ON inventory (item_id, updated_at DESC, id DESC) WHERE deleted_at IS NULL;
