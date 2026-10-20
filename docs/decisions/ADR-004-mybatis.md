# ADR-004: MyBatis와 Flyway 선택

- 상태: 승인됨
- 날짜: 2026-10-19

## 맥락

이 앱의 조회는 계층 경로, effective field, `pg_trgm` 검색, pgvector 유사도, 공유 ACL, 카테고리 Group By 등 복잡한 SQL을 포함합니다. JPA의 엔티티 매핑 이점보다 SQL 제어와 실행 계획 최적화가 더 중요합니다.

## 결정

- 데이터 접근 계층은 MyBatis를 사용합니다.
- 스키마 변경은 Flyway 마이그레이션으로 관리합니다.
- 복잡한 조회는 XML 매퍼 또는 명시적 SQL로 작성합니다.
- DTO 기반으로 매핑하고 `Map` 반환을 지양합니다.
- pgvector는 TypeHandler 또는 `CAST(... AS vector)`로 처리합니다.
- 테넌트 조건과 ACL 조건은 쿼리에 명시합니다.
- MyBatis 인터셉터는 보조 수단으로만 사용합니다.

## 결과

- 검색·ACL·벡터 쿼리를 직접 최적화할 수 있습니다.
- N+1과 지연 로딩 문제를 피할 수 있습니다.
- CRUD 보일러플레이트와 수동 매핑이 늘어납니다.
- SQL 리뷰와 통합 테스트가 중요해집니다.
- 스키마와 데이터 사전을 Flyway 기준으로 생성할 수 있습니다.

## 대안

- JPA: 단순 CRUD에는 편하지만 복잡한 조회가 native SQL로 빠지고 매핑 관리가 어렵습니다.
- jOOQ: 타입 안전성이 좋지만 학습·설정 비용이 추가됩니다.
- MyBatis + JPA 혼용: 두 모델의 일관성 관리 비용이 큽니다.

## 링크

- [아키텍처](../architecture.md)
- [검색](../search.md)
- [데이터 사전](../generated/data-dictionary.md)
