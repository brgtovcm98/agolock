# PLAN-010: 등록 행동 중심 UX P0

> 상태: P0·P1 핵심 구현 완료 (2026-10-22). AI·등록 전 사진은 M7 의존
> 역할: [ADR-020](../decisions/ADR-020-registration-action-ia.md)의 결정을 P0 화면·작업·검증으로 분해
> 상위 문서: [화면과 흐름](../frontend.md), [디자인 시스템](../design-system.md), [구현 로드맵](../implementation-roadmap.md)
> 관련 문서: [제품 사양](../product-spec.md), [M3 계획](PLAN-004-inventory.md), [M5 계획](PLAN-006-media-messaging.md)

## 1. 목적과 범위

현재 `/inventory`의 카테고리·Item·Inventory 3중 폼을 없애고, 모든 화면에서 열 수 있는 등록 시트와 등록 후 목적지(재고 상세·물건 상세)를 만듭니다.

| 구분 | P0 포함 |
|---|---|
| 진입 | 데스크톱 상단 "등록", 모바일 FAB, Room·Shelf·Box 상세의 "이 위치에 등록", 물건 상세의 "재고 추가" |
| 등록 | 새 물건 + 첫 Inventory, 기존 물건에 Inventory 추가, 위치 3단 선택, 카테고리·가격·설명·상태 |
| 목록 | `/inventory` 상태 필터, 행 전체 상세 링크, 빈 상태 CTA |
| 상세 | `/inventory/:inventoryId` 정보·상태 변경·이동·삭제, `/items/:itemId` 마스터·연결 재고·재고 추가 |
| 위치 | Room·Shelf·Box 상세의 직접 위치 재고 섹션과 등록 CTA |
| 홈 | 최근 추가, 이름 검색(`GET /items?q=`), 등록 CTA |
| 정리 | M1·환경·SCR 라벨 제거, 권한 한국어 라벨, `/design-preview` 운영 비노출 |
| 카테고리 | `/settings/categories` 목록·생성·삭제, 등록 시트 인라인 생성 |

P0 비목표:

- 사진 업로드·AI 인식. M5·M7에서 같은 시트 앞단에 추가합니다.
- 재고 오버라이드 편집 화면. 상세에서 배지만 표시하고 P1에서 편집합니다.
- 이동 이력 타임라인. P1에서 `GET /inventories/{id}/movements`로 추가합니다.
- 공유받은 위치에서의 등록. 공유 Room/Shelf 상세 전환과 `/shared/*` API 연동을 P1에서 다룹니다.
- 수량 필드, 일괄 등록, Item 병합.
- M6 전체 검색·필터·정렬.

### 1.1 확정 사항 (2026-10-21)

| 항목 | 결정 |
|---|---|
| 공유 위치 | P0는 내 위치만. 공유 위치 등록은 P1 |
| 새 물건 저장 | 클라이언트 2단계(`POST /items` → `POST /inventories`)와 부분 실패 복구 |
| 사진 | 상세의 기존 이미지 갤러리만 재사용. 등록 시트 사진은 P1 |
| 재고 상세 | 상태 변경·이동·삭제까지. 오버라이드 편집·이동 이력은 P1 |
| 카테고리 | `/settings/categories` 목록·생성·삭제. 이름 변경·병합은 P1 |
| 홈 검색 | 제출 시 `GET /items?q=` 이름 검색 + 최근 추가 |
| Room 없음 | “먼저 공간을 만들어 주세요” 안내와 `/space` 링크 |
| 물건 상세 | 읽기 + 재고 추가만. 마스터 수정은 P1 |
| 상태 변경 | 상세 인라인 select + 저장, `DISPOSED`는 확인 |
| 위치 필터 | P0는 클라이언트 필터, P1에서 서버 쿼리 |
| 검증 | Vitest + lint + build + Playwright 행복 경로 1개 |
| 디자인 미리보기 | `import.meta.env.DEV`에서만 노출 |

## 2. 현재 API 제약

| 제약 | 영향 | P0 대응 |
|---|---|---|
| `POST /items`와 `POST /inventories`가 분리됨 | 새 물건 저장이 두 번의 호출 | Item 생성 후 Inventory 생성, 두 번째 실패 시 생성된 Item을 유지하고 재시도 안내. `newItem` 통합 생성은 P1 |
| `GET /inventories`에 위치·카테고리 필터가 없음 | 위치 상세에서 직접 재고를 서버 필터로 못 가져옴 | `scope=ALL&limit=100`으로 조회한 뒤 `location.roomId/shelfId/boxId`로 클라이언트 필터. P1에서 `roomId`·`shelfId`·`boxId` 쿼리 추가 |
| `/rooms` 목록은 내 Space만 반환 | 공유 위치가 선택 목록에 없음 | P0는 소유자 위치 등록만. 공유 위치는 `/shared`과 `/shared/*` 연동 후 P1 |
| Item 응답에 `inventoryCount`가 없음 | 목록 개수 표시 | `/items/{id}/inventories` 길이 또는 상세 조회 결과로 계산 |
| `GET /inventories` 기본 `scope=OWNED` | 공유 재고가 목록에서 빠짐 | 재고 목록은 `scope=ALL` 사용 |

## 3. 화면과 라우트

| 화면 | 경로 | 핵심 |
|---|---|---|
| 재고 목록 | `/inventory` | 상태 칩, 카드/행, 등록 CTA, 빈 상태 |
| 재고 상세 | `/inventory/:inventoryId` | 정보 표, 상태 변경, 이동, 삭제, 물건 상세 링크 |
| 물건 상세 | `/items/:itemId` | 마스터 정보, 연결 재고, 재고 추가 |
| 카테고리 관리 | `/settings/categories` | 목록, 생성, 삭제, 사용 중 오류 안내 |
| 등록 시트 | 전역 오버레이 | 새 물건/기존 물건, 위치, 카테고리, 상태 |
| 위치 상세 | `/space/rooms/:roomId` 등 | 직접 위치 재고, 이 위치에 등록 |

등록 시트는 라우트가 아니라 `AddInventoryProvider`가 여는 오버레이로 둡니다. 위치 상세와 물건 상세는 컨텍스트를 인자로 전달합니다.

## 4. 작업 분해

| 순서 | 작업 | 주요 파일 | 완료 기준 |
|---|---|---|---|
| 1 | 재고 API 타입·함수 확장 | `features/inventory/api.ts` | 목록(scope/status/cursor), 상세, 생성, 수정, 이동, 삭제, 카테고리, Item 검색 |
| 2 | 위치 선택·물건 선택 공통 컴포넌트 | `features/inventory/components/location-picker.tsx`, `item-combobox.tsx` | 3단 위치, 현재 위치 프리셋, `q` 검색, 인라인 카테고리 |
| 3 | 전역 등록 시트와 진입점 | `features/inventory/add-inventory-sheet.tsx`, `add-inventory-provider.tsx`, `app/app-shell.tsx` | 데스크톱 버튼·모바일 FAB·위치/물건 컨텍스트, 저장 후 계속/보기 |
| 4 | 재고 목록 교체 | `features/inventory/inventory-page.tsx` | 3중 폼 제거, 상태 필터, 상세 링크, 기존 이미지 갤러리는 상세로 이동 |
| 5 | 재고 상세·물건 상세 | `inventory-detail-page.tsx`, `item-detail-page.tsx`, `app/router.tsx` | 상태·이동·삭제, 연결 재고, 재고 추가 |
| 6 | 위치 상세 재고 섹션 | `features/inventory/location-inventories.tsx`, `features/hierarchy/*-detail-page.tsx` | 직접 재고 카드, `MANAGE` CTA, `inventoryCount` 타입 |
| 7 | 홈·설정·정리 | `features/home/home-page.tsx`, `features/settings/*`, `features/sharing/*` | 최근 추가·이름 검색, 카테고리 관리, M1/원문 라벨 제거 |

## 5. 등록 시트 상태

| 상태 | 처리 |
|---|---|
| 초기 | 컨텍스트 위치가 있으면 선택된 상태, 없으면 Room 선택 필요 |
| 모드 | 소유자만 "새 물건" 표시. 공유 사용자는 "이미 있는 물건"만 |
| 저장 중 | 버튼 비활성화, 중복 제출 차단 |
| 새 물건 부분 실패 | "물건은 만들어졌고 재고 등록만 실패했어요" + 재시도 |
| 완료 | 위치 경로, "계속 등록", "물건 보기" |
| 닫기 | 입력이 있으면 확인 대화상자 |

## 6. 검증

| 영역 | 테스트 |
|---|---|
| 등록 시트 | 새 물건 필수값, 위치 없음 저장 불가, 기존 물건 검색, 소유자/공유 모드 |
| 목록 | 기본 폐기 제외, 상태 필터, 빈 상태, 상세 링크 |
| 상세 | 상태 변경, `DISPOSED` 확인, 이동 후 갱신, 409 재조회, 삭제 |
| 위치 | 직접 재고만 필터, 하위 위치 재고 제외, `MANAGE` CTA 노출 |
| 홈 | 최근 추가, `q` 검색, 결과 링크 |
| 접근성 | 시트 포커스 트랩, ESC, 44px 터치, 한국어 라벨 |
| 회귀 | `pnpm lint`, `pnpm test`, `pnpm build`, 기존 인증·계층·공유 테스트 |

## 7. P1 이후

- 사진·AI 제안을 시트 1단계로 추가.
- 재고 오버라이드 편집과 이동 이력 타임라인.
- 서버 위치 필터와 공유 위치 등록.
- 공유 Room/Shelf 상세의 문맥형 공유 초대.
- 마스터 수정 영향 범위 대화상자.

## 8. P0 구현 결과

| 영역 | 결과 |
|---|---|
| 등록 시트 | 전역(데스크톱 헤더·모바일 FAB)·위치 상세·물건 상세 진입, 새 물건/기존 물건, 위치 3단, 카테고리 인라인 생성, 상태, 가격·설명 접기 |
| 새 물건 저장 | `POST /items` → `POST /inventories`, 부분 실패 시 Item 유지·재시도, Idempotency-Key 재사용 |
| 목록 | 상태 필터, 커서 더 보기, 카드 전체 상세 링크, 빈 상태 CTA |
| 상세 | 상태 변경(폐기 확인), 위치 이동, 삭제, 이미지 갤러리, 물건 상세 링크 |
| 물건 상세 | 마스터 정보, 연결 재고, 재고 추가. 마스터 수정은 P1 |
| 위치 상세 | 직접 위치 재고, `inventoryCount`, “이 위치에 등록”, 공유 위치 등록 안내 |
| 홈 | 최근 추가, `GET /items?q=` 이름 검색, FAB/헤더 등록 |
| 설정 | `/settings/categories` 목록·생성·삭제, 사용 중 409 안내 |
| 정리 | M1·SCR·환경 라벨 제거, 권한 한국어 라벨, `/design-preview` DEV 전용 |
| 검증 | Vitest 29개, lint, build 통과 |

알려진 P0 한계:

- 위치 상세 재고는 `scope=ALL&limit=100` 조회 후 클라이언트 필터입니다.
- 공유 위치 등록과 공유 물건의 재고 추가는 P1입니다.
- 등록 사진, 오버라이드 편집, 이동 이력, 물건 마스터 수정은 P1입니다.
- 새 물건과 첫 재고의 비원자성은 P1의 `newItem` 서버 트랜잭션으로 해소했습니다.

## 9. P1 구현 결과 (2026-10-22)

| 영역 | 결과 |
|---|---|
| 새 물건 저장 | `POST /inventories`의 `newItem`으로 Item+Inventory를 한 트랜잭션에서 생성. 클라이언트 2단계와 부분 실패 UI 제거 |
| 위치 필터 | `GET /inventories`에 `roomId`·`shelfId`·`boxId` 직접 필터 추가. 위치 상세가 서버 필터 사용 |
| 공유 위치 | 공유 `MANAGE` Room/Shelf를 위치 선택에 추가. 공유 위치에서 기존 물건 재고 등록, 새 물건 생성은 차단 |
| 재고 편집 | 이름·설명·카테고리·가격 오버라이드 입력과 해제, 409 재조회 |
| 이동 이력 | `GET /inventories/{id}/movements` 타임라인과 “등록” 합성 |
| 물건 수정 | 마스터 이름·설명·카테고리·가격 수정, `update-impact` 영향 범위 확인 |
| 카테고리 | 이름 변경, 병합(영향 건수), 삭제 |
| 공유 초대 | Room/Shelf 상세의 문맥형 공유 대화상자, Shelf 초대 지원 |
| 사진 | 등록 완료 화면에서 사진 갤러리 추가. 등록 전 DRAFT 업로드는 M7 |
| 검증 | Vitest, lint, build, Playwright 등록·smoke, backend Inventory/Sharing 통합 테스트 |

P1 잔여:

- AI 인식 제안과 등록 전 사진은 M7 `ai_job`·DRAFT 연결이 필요합니다. 현재 백엔드에 AI 모듈이 없어 UI만 만들지 않습니다.
- Item 대표 이미지 갤러리는 물건 수정 화면에 아직 넣지 않았습니다.
- 위치 필터는 직접 위치만 지원합니다. 하위 위치 포함 검색은 M6에서 다룹니다.
