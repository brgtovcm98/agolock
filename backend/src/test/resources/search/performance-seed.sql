-- Testcontainers 전용 시드. 실제 환경에서는 실행하지 않습니다.
INSERT INTO app_user (id, email, password_hash, display_name, created_at, updated_at)
SELECT md5('perf-user-' || n)::uuid, 'perf-' || n || '@example.com', 'test-only', '성능 사용자 ' || n, now(), now()
FROM generate_series(1, 2) n;

INSERT INTO space (id, owner_user_id, name, created_at, updated_at)
SELECT md5('perf-space-' || n)::uuid, md5('perf-user-' || n)::uuid, '성능 공간 ' || n, now(), now()
FROM generate_series(1, 2) n;

INSERT INTO room (id, space_id, name, created_at, updated_at)
SELECT md5('perf-room-' || n)::uuid, md5('perf-space-' || n)::uuid, '성능 창고 ' || n, now(), now()
FROM generate_series(1, 2) n;

INSERT INTO share (id, space_id, room_id, grantee_user_id, permission, granted_by, created_at, updated_at)
VALUES (gen_random_uuid(), md5('perf-space-2')::uuid, md5('perf-room-2')::uuid,
        md5('perf-user-1')::uuid, 'VIEW', md5('perf-user-2')::uuid, now(), now());

INSERT INTO item (id, space_id, name, description, created_at, updated_at)
SELECT md5('perf-item-' || n)::uuid, md5('perf-space-' || CASE WHEN n <= 9000 THEN 1 ELSE 2 END)::uuid,
       '물건 ' || lpad(n::text, 5, '0'), '성능 검증용 물건', now(), now()
FROM generate_series(1, 10000) n;

INSERT INTO inventory (id, space_id, item_id, room_id, name_override, status, created_at, updated_at)
SELECT md5('perf-inventory-' || n || '-' || k)::uuid,
       md5('perf-space-' || CASE WHEN n <= 9000 THEN 1 ELSE 2 END)::uuid,
       md5('perf-item-' || n)::uuid,
       md5('perf-room-' || CASE WHEN n <= 9000 THEN 1 ELSE 2 END)::uuid,
       CASE WHEN k = 1 AND n % 10 = 0 THEN '개별 물건 ' || n ELSE NULL END,
       CASE WHEN k = 5 THEN 'IN_USE'::inventory_status ELSE 'STORED'::inventory_status END,
       now(), now() - n * interval '1 second'
FROM generate_series(1, 10000) n CROSS JOIN generate_series(1, 5) k;

INSERT INTO image (id, space_id, item_id, object_key, thumbnail_key, mime_type, size_bytes, width, height,
                   sha256, is_primary, status, upload_expires_at, processed_at, created_by, created_at, updated_at)
SELECT md5('perf-image-' || n)::uuid,
       md5('perf-space-' || CASE WHEN n <= 9000 THEN 1 ELSE 2 END)::uuid,
       md5('perf-item-' || n)::uuid, 'perf/' || n, 'perf/thumb/' || n, 'image/webp', 100, 10, 10,
       repeat('a', 64), true, 'READY', now() + interval '1 day', now(),
       md5('perf-user-' || CASE WHEN n <= 9000 THEN 1 ELSE 2 END)::uuid, now(), now()
FROM generate_series(1, 10000) n WHERE n % 10 < 7;

-- 일괄 삽입으로 생긴 GIN pending list를 정리한 뒤 정상 유지보수 상태에서 측정합니다.
SELECT gin_clean_pending_list('idx_item_name_trgm'::regclass);
SELECT gin_clean_pending_list('ix_item_name_trgm'::regclass);
SELECT gin_clean_pending_list('idx_inventory_name_override_trgm'::regclass);
SELECT gin_clean_pending_list('idx_item_description_trgm'::regclass);
SELECT gin_clean_pending_list('idx_inventory_description_override_trgm'::regclass);

ANALYZE app_user;
ANALYZE space;
ANALYZE room;
ANALYZE item;
ANALYZE inventory;
ANALYZE image;
ANALYZE share;
