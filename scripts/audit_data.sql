-- 데이터 감사(읽기 전용).
-- 실행: psql -h localhost -U agolock -d agolock -f scripts/audit_data.sql
-- 이 스크립트는 어떤 행도 변경하지 않는다. 결과가 비어 있지 않거나 0이 아니면 원인과 조치 여부를 확인한다.

-- 1) Orphan inventory: soft-deleted Room/Shelf/Box를 직접 위치로 가리키는 활성 Inventory.
--    위치 리소스의 삭제 시각을 함께 확인해 실제 orphan인지 판단한다.
SELECT
    i.id AS inventory_id,
    i.space_id,
    i.item_id,
    CASE
        WHEN i.room_id IS NOT NULL THEN 'ROOM'
        WHEN i.shelf_id IS NOT NULL THEN 'SHELF'
        ELSE 'BOX'
    END AS location_type,
    COALESCE(i.room_id, i.shelf_id, i.box_id) AS location_id,
    COALESCE(r.deleted_at, s.deleted_at, b.deleted_at) AS location_deleted_at
FROM inventory i
LEFT JOIN room r ON r.id = i.room_id
LEFT JOIN shelf s ON s.id = i.shelf_id
LEFT JOIN box b ON b.id = i.box_id
WHERE i.deleted_at IS NULL
  AND (r.deleted_at IS NOT NULL OR s.deleted_at IS NOT NULL OR b.deleted_at IS NOT NULL)
ORDER BY i.space_id, i.id;

-- 2) Outbox PENDING 적체: 이벤트 타입별 미발행 건수와 가장 오래된 생성 시각.
--    특정 타입의 급증은 worker/consumer 장애나 스키마 불일치를 의심한다.
SELECT
    event_type,
    COUNT(*) AS pending_count,
    MIN(created_at) AS oldest_created_at
FROM outbox_event
WHERE status = 'PENDING'
GROUP BY event_type
ORDER BY pending_count DESC, event_type;

-- 3) 이메일 미인증 활성 사용자 수. V21은 배포 시점의 기존 활성 사용자를 created_at으로 백필했다.
--    V21 이후에도 NULL이면 신규 가입 후 미인증이거나 인증 흐름 장애를 의심한다.
SELECT
    COUNT(*) AS unverified_active_users
FROM app_user
WHERE email_verified_at IS NULL
  AND deleted_at IS NULL;

-- 4) 활성 Share Grant의 permission distinct 수와 전체 건수 요약.
--    permission은 VIEW < EDIT < MANAGE 누적 규칙을 따르며, 예상 밖 값이 없는지 확인한다.
SELECT
    COUNT(DISTINCT permission) AS distinct_permission_count,
    COUNT(*) AS active_grant_count
FROM share
WHERE revoked_at IS NULL;

-- 5) 활성 Share Grant의 permission별 분포와 대상 사용자 수.
--    특정 permission의 급증이나 grantee 중복은 권한 상속·재부여 이력을 점검하는 신호다.
SELECT
    permission,
    COUNT(*) AS grant_count,
    COUNT(DISTINCT grantee_user_id) AS distinct_grantee_count
FROM share
WHERE revoked_at IS NULL
GROUP BY permission
ORDER BY permission;
