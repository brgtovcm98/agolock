# PLAN-014: 물건·재고 관리 개선

> 상태: 구현 반영·자동 검증 완료, 실제 API E2E 확인 남음 (2026-10-24)
> 요구사항: [제품 사양](../product-spec.md), [도메인](../domain.md), [공유](../sharing.md)
> 구조: [아키텍처](../architecture.md), 화면: [프론트엔드](../frontend.md)

## 1. 목표와 범위

승인된 계획에 따라 기존 제품 범위를 유지하면서 위치 계약, PATCH·폐기·상속, 동시성, 공유 조회·페이지 처리, 변경 영향 미리보기, 편집 충돌과 계약 생성 경로를 개선합니다. API 계약 변경을 허용하며 백엔드와 프론트엔드를 함께 갱신합니다. AI·수량 관리·새 공유 방식·전면 디자인·실제 배포는 제외합니다.

## 2. 실행 단계

- [x] 위치 경로 상태와 정확히 하나의 API 위치를 분리하고 등록·이동·목록에 적용
- [x] 필드 생략/명시 null/빈 설명/가격 0 구분, 폐기 전환에만 확인, 버전·타입 검증
- [x] 병합 version 증가, 참조 생성·수정·삭제의 행 잠금 및 최신 권한 재검증
- [x] 권한 집계 SQL 재사용, 공유 카테고리, 연결 재고·대상·카테고리 페이지 처리
- [x] 변경 필드별 영향 미리보기와 고정 요청 확인
- [x] baseline/draft/latest 비교, 충돌 입력 보존과 명시적 선택, 관련 캐시 갱신
- [x] 서비스 책임 분리, 정규화·색상 PATCH, 마이그레이션 사전 충돌 검사
- [ ] 실제 API 브라우저 흐름의 성공 확인 (시간 초과로 미완료)
- [x] 계약 생성·문서·정적 검사·전체 회귀 검증

## 3. 계약

| API | 변경 |
|---|---|
| POST inventories / PATCH location | 위치 ID 정확히 하나 유지 |
| GET items | 선택적 roomId/shelfId/boxId로 등록 대상 Space 제한 |
| GET items/{id}/inventories | status/limit/cursor, 기본 폐기 제외, items/nextCursor |
| GET items/{id}/inventory-targets | 같은 Space의 MANAGE 등록 대상 |
| GET inventories/{id}/move-targets | 원본 MANAGE + 같은 Space의 MANAGE 이동 대상 |
| GET categories | itemId/limit/cursor 문맥과 items/nextCursor |
| POST items/{id}/update-impact | PATCH 변경분+version, 변경 필드별 영향 합집합 |
| DELETE items/{id}, inventories/{id} | version 쿼리 필수 |

기본 20개·최대 100개입니다. 재고는 updatedAt/id 내림차순, 카테고리는 sortOrder/name/id 오름차순, 대상은 ROOM/SHELF/BOX 순서와 id 오름차순입니다. 커서는 문맥·필터에 묶습니다. 미리보기는 조회 시점 예상치이며 저장 잠금을 보장하지 않습니다.

## 4. 동시성·편집

잠금 순서는 Category → Item → Room → Shelf → Box → Inventory, 동일 종류는 UUID 순입니다. 변경 전에 경로·권한을 재검증하고 문맥 변경은 RESOURCE_CONTEXT_CHANGED로 거부합니다. 병합은 관련 version을 증가시킵니다.

편집은 원본 필드 기준 baseline/draft/latest 비교를 사용합니다. 미변경 필드는 최신 값, 서버 미변경 필드는 입력을 보존하며 양쪽의 서로 다른 변경만 명시적으로 선택합니다. 자동 재전송하지 않습니다. Item 재저장은 영향 미리보기를 다시 수행합니다.

## 5. 검증

각 기능은 실패 회귀 테스트 후 구현하고 관련 테스트를 통과시킵니다. 위치 3종·권한 4종·폐기·상속·가격 경계·페이지 101개·커서·동시성·충돌·실제 API 브라우저 흐름을 검증합니다. 백엔드 test/check/contract, 프론트 lint/test/build/E2E/접근성, 문서 검사를 완료합니다.

## 6. 진행 기록

- 기준: f27f0f7, fix/inventory-hardening 작업 브랜치. main 변경·배포·push는 하지 않습니다.
- 기존 등록·편집 테스트 4개와 문서 검사는 계획 단계에서 통과했습니다.
- Ruling: 작업량이 크고 코드 소유권을 나눌 수 있어 subagent-driven-development로 실행을 전환합니다. 백엔드 변경/조회와 프론트엔드를 분담하고 통합 검토를 수행합니다.

## 7. 최종 검증과 한계

- 별도 리뷰에서 발견한 MyBatis SESSION 캐시의 잠금 후 오래된 조회를 STATEMENT 설정과 회귀 테스트로 수정했습니다.
- 충돌 재조회 중 새 입력이 덮어써지는 문제는 편집 잠금과 지연 응답 테스트로 검증했습니다.
- 백엔드 전체 175개 실행에서 169개 통과, HierarchyIntegrationTest 6개는 테스트 DB 포트 연결 거부로 실패했습니다. 해당 묶음 재확인 결과는 아래에 기록합니다.
- 새 쓰기·동시성 회귀 12개, 조회·페이지·공유 회귀 4개, PATCH 파싱 3개는 모두 통과했습니다.
- 실제 API 브라우저 시나리오는 시간 초과로 통과하지 못했습니다. 모킹 기반 브라우저 검증과 구분하며 실제 연동의 완료를 주장하지 않습니다.
- 기존 전체 브라우저 검사 32개 통과, 실제 API opt-in 2개 제외 결과를 확인했습니다.

- 실패했던 HierarchyIntegrationTest 6개를 분리 재실행해 모두 통과했습니다. 동일 실행의 OpenAPI 계약 테스트 2개도 통과했습니다.
- 프론트엔드 최종 전체 50개 테스트 통과, TypeScript·lint·빌드 통과. 수정 전 테스트가 가정하던 전체 개수 문구는 조회된 개수 문구에 맞췄습니다.
- 최종 ktlintCheck·detekt 통과. OpenAPI 60개 path·타입·승인 스냅샷과 데이터 사전 25개 table 미리보기 재생성, 문서 검사 통과. 이벤트 미리보기 0채널은 기존 생성기 한계이며 이번 범위에서 변경하지 않았습니다.
- 실제 API 검증은 독립 로컬 DB에서 V25까지 마이그레이션 후 수행했습니다. 브라우저 테스트는 편집 화면에서 시간 초과가 발생해 성공 확인이 남아 있습니다. 신규 기능을 더 확장하지 않고 이 상태를 기록해 마무리합니다.
