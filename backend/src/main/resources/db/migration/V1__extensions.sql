-- M0 기반 마이그레이션: 검색·벡터·대소문자 무시 문자열 확장만 준비합니다.
-- 도메인 테이블과 Spring Session 테이블은 M1부터 추가합니다.
CREATE EXTENSION IF NOT EXISTS pg_trgm;
CREATE EXTENSION IF NOT EXISTS vector;
CREATE EXTENSION IF NOT EXISTS citext;
