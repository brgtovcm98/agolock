-- 성능 회귀 확인용 EXPLAIN 모음입니다. 실제 space_id/actor_id로 바꿔 실행하세요.
-- psql -d agolock -f scripts/explain_queries.sql
\set space_id '00000000-0000-0000-0000-000000000000'
\set actor_id '00000000-0000-0000-0000-000000000000'

\echo '1) Inventory 목록: inventory_access + ACL + keyset'
EXPLAIN (ANALYZE, BUFFERS)
SELECT a.inventory_id, a.updated_at
FROM inventory_access a
LEFT JOIN LATERAL (
    SELECT max(CASE s.permission WHEN 'MANAGE' THEN 3 WHEN 'EDIT' THEN 2 ELSE 1 END) AS rank
    FROM share s
    WHERE s.grantee_user_id = :'actor_id'
      AND s.space_id = a.space_id
      AND s.revoked_at IS NULL
      AND (s.room_id = a.effective_room_id OR s.shelf_id = a.effective_shelf_id)
) g ON a.space_id <> :'space_id'
WHERE a.status IN ('IN_USE', 'STORED')
  AND (a.space_id = :'space_id' OR g.rank IS NOT NULL)
ORDER BY a.updated_at DESC, a.inventory_id DESC
LIMIT 20;

\echo '2) 검색 UPDATED_DESC keyset: Item 그룹 후보'
EXPLAIN (ANALYZE, BUFFERS)
WITH hit AS (
    SELECT a.item_id, a.updated_at
    FROM inventory_access a
    JOIN item it ON it.id = a.item_id AND it.space_id = a.space_id
    WHERE it.deleted_at IS NULL
      AND a.status IN ('IN_USE', 'STORED')
      AND a.space_id = :'space_id'
), grp AS (
    SELECT item_id, max(updated_at) AS updated_at, count(*) AS inventory_count
    FROM hit
    GROUP BY item_id
)
SELECT item_id, updated_at, inventory_count
FROM grp
ORDER BY updated_at DESC, item_id DESC
LIMIT 21;

\echo '3) 공유 ACL: grantee + space + target'
EXPLAIN (ANALYZE, BUFFERS)
SELECT s.id, s.room_id, s.shelf_id, s.permission
FROM share s
WHERE s.grantee_user_id = :'actor_id'
  AND s.space_id = :'space_id'
  AND s.revoked_at IS NULL
  AND (s.room_id IS NOT NULL OR s.shelf_id IS NOT NULL);
