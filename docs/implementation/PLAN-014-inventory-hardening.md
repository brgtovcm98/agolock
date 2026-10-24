# PLAN-014: 물건·재고 관리 개선

> 상태: 코드 반영·분리 회귀 확인, 실제 API 검증·일부 구조 정리 남음 (2026-10-24)
> 요구사항: [제품 사양](../product-spec.md), [도메인](../domain.md), [공유](../sharing.md)
> 구조·결정: [아키텍처](../architecture.md), [ADR-023](../decisions/ADR-023-inventory-write-consistency.md)
> 화면: [프론트엔드](../frontend.md), 전체 현황·남은 작업: [PLAN-015](PLAN-015-current-status-and-followups.md)

## 1. 범위와 커밋

기존 제품 범위를 유지하면서 위치 계약, PATCH·폐기·상속, 동시성, 공유 조회·페이지, 변경 영향 미리보기와 편집 충돌을 개선했습니다. API 계약 변경과 연동 화면 변경을 함께 반영했습니다. AI·수량 관리·새 공유 방식·전면 디자인·실제 배포는 이번 범위에서 제외합니다.

| 구분 | 기준 |
|---|---|
| 착수 기준 | main `f27f0f7` |
| 구현 | `447c97178a8cb966c83a9b6c67a805c727b48906` |
| 검증 기록 | `7c42304` |
| 작업 브랜치 | `fix/inventory-hardening`, main 미병합·remote 미설정·push/배포 미실시 |

## 2. 반영 결과

| 영역 | 반영 내용 |
|---|---|
| 위치 | 경로 선택 상태와 요청 위치 분리, 등록·이동·직접 위치 목록에서 최종 위치 하나만 전송 |
| PATCH | 생략/null/빈 설명/가격 0 구분, 필드 길이·가격·버전 검증, no-op 유지, 응답 null 명시 |
| 상태 | 이미 폐기된 재고의 일반 편집 허용, DISPOSED 전환에만 확인 |
| 동시성 | 병합 version 증가, Category→Item→Room→Shelf→Box→Inventory 순서의 잠금, 원본·대상 최신 경로/권한 재확인, version 기반 삭제 |
| 조회 일관성 | MyBatis STATEMENT 캐시 설정, JDBC 변경 뒤 mapper가 이전 값을 재사용하지 않는 회귀 검증 |
| 권한·페이지 | Item 최대 권한 SQL 집계, 공유 카테고리 문맥과 저장 검증, 연결 재고·대상·카테고리 커서 페이지, 기본 폐기 제외 |
| 영향 확인 | 제안 PATCH 기반 변경 필드와 영향 재고 합집합 집계, 조회 시점 예상치 안내, 확인한 요청 고정 |
| 충돌·화면 | 기준/입력/최신 원본 필드 비교, 서로 다른 충돌값 선택, 입력 보존·재조회 중 편집 잠금·이탈 확인, 관련 Query 무효화와 조회 개수 표시 |
| 구조·데이터 | ItemQueryService 분리, ItemFieldPolicy/CategoryNamePolicy, 색상 null PATCH, V25 NFKC 정규화·기존 이름 충돌 시 중단 |
| 계약 생성 | 실제 승인 스냅샷 기반 프론트 타입, 물건·재고 초안 부분 동기화 스크립트 |

현재 동작·API 계약의 단일 원본은 상위 기능 문서와 [OpenAPI](../generated/openapi.yaml)입니다. 이 계획에서 필드·불변식을 다시 정의하지 않습니다.

## 3. 검증 기록

| 확인 대상 | 결과 |
|---|---|
| 수정 전 회귀 | 별도 기준 코드에서 폐기 편집·빈 이름·병합 version·오래된 삭제·생성과 삭제 경합의 실패 확인 |
| 백엔드 전체 | 175개 중 169개 통과, HierarchyIntegrationTest 6개는 테스트 DB 연결 거부로 실패 |
| 실패 묶음 재확인 | 같은 코드에서 HierarchyIntegrationTest 6개 분리 재실행 모두 통과. 전체 단일 실행 성공 기록은 별도로 남길 필요가 있음 |
| 신규 핵심 회귀 | 쓰기·동시성 12개, 조회·공유·페이지 4개, PATCH 파싱 3개 통과. 위 전체 실행의 부분집합 |
| 계약 | OpenAPI 테스트 2개 통과, 구현 preview 60개 path·승인 스냅샷·프론트 타입 갱신 |
| 프론트엔드 | 최종 단위 테스트 50개, TypeScript·ESLint·Prettier·빌드 통과 |
| 브라우저·접근성 | 모킹 기반 32개 통과, 실제 API opt-in 2개 기본 실행에서 제외 |
| 실제 API 브라우저 | 독립 DB에 V25까지 적용 후 별도 실행했으나 편집 화면에서 시간 초과. 성공 미확인 |
| 정적·문서 | ktlintCheck·detekt·문서 검사 통과 |
| 생성물 | 데이터 사전 preview 25개 table, 이벤트 preview 0채널은 기존 생성기 한계 |

MyBatis SESSION 캐시의 잠금 후 오래된 조회와 충돌 재조회 중 입력 덮어쓰기는 최종 리뷰에서 발견해 수정했습니다. 각각 mapper/JDBC 재조회와 지연 응답 회귀 테스트로 확인했습니다. 당시 `/tmp/agolock-inventory-*` 로그는 임시 자료이므로 후속 검증에서 다시 생성해야 합니다.

## 4. 남은 항목

아래 항목의 우선순위·수정 대상·완료 조건은 [PLAN-015 3번 항목](PLAN-015-current-status-and-followups.md)에 통합합니다.

- [ ] INV-01: 실제 API 브라우저 시간 초과 원인 확인과 소유/공유 MANAGE 전체 흐름 성공 검증
- [ ] INV-02: 같은 커밋의 전체 검사 단일 실행 기록 확보
- [ ] INV-03: Boolean presence flag로 남은 내부 PATCH 명령을 명시적 타입으로 정리
- [ ] INV-04: 프론트 Item 권한·공유 필수 타입과 소유자 추측 fallback 정리
- [ ] INV-05: InventoryResponseAssembler의 DB 조회·effective 값 계산 분리와 중복 정리
- [ ] INV-06: 변경 후 SQL 횟수·규모별 지연·모든 페이지 수집 비용 측정
- [ ] INV-07: 기존 데이터 복사본의 V25 충돌·중단·재실행 리허설
- [ ] INV-08: 번들 경고의 실제 로딩 영향 측정과 필요 시 코드 분할

`서비스 책임 분리·정규화` 전체가 완결된 것으로 보지 않습니다. Item 조회 분리와 공통 정책은 반영됐지만 내부 명령 타입화·응답 조립 순수화는 후속입니다. 기존 테스트 통과와 실제 저장소·브라우저·VPS 운영 검증을 구분합니다.
