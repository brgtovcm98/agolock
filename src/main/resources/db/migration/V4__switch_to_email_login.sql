-- 로그인 식별자를 username(아이디)에서 email로 전환한다.
-- 기존 username 컬럼은 표시용 nickname으로 재활용한다(고유 제약 제거, 중복 허용).
-- email은 로그인 식별자 겸 향후 계정 복구 수단으로 고유(UNIQUE) + NOT NULL.

-- 1) username -> nickname 으로 rename 하고 고유 제약을 제거한다.
ALTER TABLE users RENAME COLUMN username TO nickname;
ALTER TABLE users DROP CONSTRAINT IF EXISTS users_username_key;

-- 2) email 컬럼 추가. 개발 단계라 운영 데이터는 없으나,
--    혹시 남아 있는 시드 행이 있으면 nickname 기반의 합성 이메일로 백필하여
--    NOT NULL 적용이 실패하지 않도록 한다(빈 테이블이면 UPDATE는 0건).
ALTER TABLE users ADD COLUMN email VARCHAR(255);
UPDATE users SET email = nickname || '@seed.local' WHERE email IS NULL;
ALTER TABLE users ALTER COLUMN email SET NOT NULL;
ALTER TABLE users ADD CONSTRAINT users_email_key UNIQUE (email);
