-- V16 이전 가입자는 이메일 인증 수단이 없었으므로 grandfathering 한다.
-- V16이 적용된 시점 이전에 생성된 활성 사용자만 백필한다.
-- V16 이후 가입자는 정상적으로 이메일 인증을 거쳐야 하므로 대상이 아니다.
-- WHERE 조건으로 NULL 행만 갱신하므로 재실행해도 안전하다(멱등).
-- Spring Batch 없이 Flyway 일회성 UPDATE로 처리한다. 대용량이면 별도 one-off CLI의 배치 UPDATE를 검토한다.
UPDATE app_user
SET email_verified_at = created_at
WHERE email_verified_at IS NULL
  AND deleted_at IS NULL
  AND created_at < (
      SELECT installed_on
      FROM flyway_schema_history
      WHERE version = '16' AND success = true
  );
