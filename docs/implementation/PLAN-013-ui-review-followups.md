# PLAN-013: UI 점검 반영과 후속 작업 인계

> 상태: 프론트엔드 반영 완료, 소유자 이름 노출은 결정 대기 (2026-10-24)
> 역할: 2026-10-23 UI 일관성 점검의 반영 결과와, 다른 세션이 이어서 할 후속 작업의 현재 상태·결정·절차를 남긴 인계 문서
> 상위 문서: [화면과 흐름](../frontend.md), [디자인 시스템](../design-system.md)
> 관련 문서: [PLAN-012](PLAN-012-microinteractions.md), [공유와 권한](../sharing.md), [API 계약 규칙](../contracts.md), [화면 검토 기록](../../design/review/2026-10-23/README.md)

## 1. 한눈에 보기

| 항목 | 상태 |
|---|---|
| 점검 높음 1건, 중간 8건 | 프론트엔드 반영 완료 |
| 점검 낮음 5건 (머리말 중복, 모바일 아바타, 비활성 스타일, 오류 재시도, 문서 모순) | 반영 완료 |
| 소유자 이름 노출 (`내 소유`를 `민지 소유`로) | 결정 대기, 백엔드 계약 변경 필요 |
| 스크린리더 실기기 확인 | 사람이 직접 해야 함, 미실시 |
| 화면별 오류·충돌 상태, 이미지 상태 시각 확인 | 미실시 |
| 브랜치 | 당시 `release/p3-readiness` 작업은 main에 통합됨. 물건·재고 후속 `447c971`도 main에 반영되어 현재 브랜치는 `main`(`298a2cb`) 하나입니다. push하지 않아 로컬 `origin/main` 추적 ref(`9e1f066`)보다 `main`이 앞서 있습니다 |

UI 최초 반영 커밋은 아래 4개입니다. 이후 물건·재고의 충돌·상속·조회 개선은 [PLAN-014](PLAN-014-inventory-hardening.md)에 있으며, 미완료 전체 목록은 [PLAN-015](PLAN-015-current-status-and-followups.md)를 따릅니다.

| 커밋 | 내용 |
|---|---|
| `86141b7` | `Select`, `Choice`, `RadioCard`, `PageHeader`, `Alert`의 `action`, 터치 영역·글자 크기 토큰, `cn()` 보정 |
| `8b5239e` | 화면별 수정 (카드 넘침, 용어 통일, 터치 영역, `PageHeader` 적용, 재시도 버튼, 모바일 아바타, 사이드바 공간 카드, 방·선반 아이콘, `내 소유`) |
| `b4a49c7` | 디자인 시스템 R-14~R-18, 시안, ESLint R-08 규칙, 다크 접근성 e2e |
| `c4d3178` | 시안 폴더 이동, `FRONTEND`·`DESIGN` 문서의 `last_verified_commit` 갱신 |

## 2. 반영된 규칙의 위치

새 규칙의 단일 원본은 [디자인 시스템 3번 항목](../design-system.md)입니다. 코드로 강제하는 곳은 다음과 같습니다.

| 규칙 | 강제 위치 |
|---|---|
| R-08 margin 금지, 스케일 밖 간격, `text-[Npx]` 금지 | `frontend/eslint.config.js`의 `no-restricted-syntax` (배지 좌우 10px만 예외 주석) |
| R-14 용어 통일 | `frontend/src/shared/ui/status-tag.tsx`의 `statusLabel`, `frontend/src/shared/lib/format.ts`의 `locationTypeLabel`, `josaEulReul` |
| R-15 사각 라디오·체크박스, `Select` | `frontend/src/shared/ui/choice.tsx`, `select.tsx` |
| R-17 `PageHeader` | `frontend/src/shared/ui/page-header.tsx` (홈, 설정, 재고, 공유, 검색, 공간 지도, 카테고리, 재고 편집, 물건 수정에 적용) |
| R-18 오류 재시도, 비활성 스타일 | `Alert`의 `action`, `Button`·`TextField`·`Select`의 `disabled:` 스타일 |
| 터치 영역 44px | `globals.css`의 `.tap`(텍스트 링크), `.tap-area`(보이는 크기를 유지한 채 영역 확장) |

## 3. 이어서 할 일

### 3.1 소유자 이름 노출 (결정 대기)

현재 공유받은 방·선반·박스에는 권한 라벨(`내 소유`, `편집`, `관리`, `보기`)만 보이고, 누구의 것인지 이름이 보이지 않습니다. 시안과 [디자인 시스템 7.1번 항목](../design-system.md)의 홈·검색 설계는 `민지 소유` 배지를 전제로 합니다.

**확인된 사실**

| 대상 | 현황 |
|---|---|
| `RoomResponse`, `ShelfResponse`, `BoxResponse` | `backend/src/main/kotlin/com/agolock/hierarchy/api/HierarchyDtos.kt`에 소유자 이름 필드가 없음 |
| `/shared` 응답 `ShareResponse` | `backend/src/main/kotlin/com/agolock/sharing/api/SharingDtos.kt`에 `granteeDisplayName`만 있고 `ownerName`이 없음 |
| `docs/generated/openapi.yaml`의 `SharedResource` | `ownerUserId`, `ownerName`을 필수로 선언하지만 실제 `/shared` 구현과 형태가 다름 (`resourceId`, `path` 등) |
| 검색 응답 | `backend/src/main/kotlin/com/agolock/search/api/SearchDtos.kt`에만 `ownerName`이 있고, 내 Space면 `null` |
| 프론트엔드 | `frontend/src/features/sharing/api.ts`의 `Share` 타입이 실제 구현 응답(`resourceName`, `roomId`, `shelfId`, `granteeDisplayName`)을 따름 |

생성 문서는 설계 초안을 유지하지만 [ADR-023](../decisions/ADR-023-inventory-write-consistency.md) 이후 프론트 타입은 승인 구현 스냅샷에서 생성합니다. 물건·재고의 부분 동기화가 공유 전체 계약 정리를 완료한 것은 아닙니다. 현재 검사는 path/method와 구현 preview 스냅샷을 검사하며 공유 설계 초안의 모든 응답 의미까지 같음을 보장하지 않습니다.

**먼저 정해야 할 결정**

| 결정 | 선택지 | 권장 |
|---|---|---|
| 문서와 구현 불일치 | (가) OpenAPI의 `SharedResource`에 구현을 맞춤 (나) 구현에 맞춰 OpenAPI를 고침 | (나). 프론트엔드가 이미 구현 형태에 맞춰져 있고 변경 범위가 작음 |
| 소유자 이름의 위치 | (가) 방·선반·박스 응답에 `ownerName` 추가 (나) `/shared` 응답에만 추가 | (가). 상세·트리 어디서든 같은 응답으로 표시 가능 |
| 내 소유일 때 값 | 항상 값 / `null` | `null`. 검색 응답과 같은 규칙이고 프론트엔드가 `내 소유`를 표시 |
| ADR | 필요 | 공유·권한 표시에 영향을 주므로 `ADR-022`로 기록. 번호는 [ADR 목록](../decisions/README.md)에서 다시 확인 |

**구현 순서 (결정 후)**

1. ADR을 작성하고 [ADR 목록](../decisions/README.md)에 추가합니다.
2. 백엔드: `RoomResponse`, `ShelfResponse`, `BoxResponse`에 `ownerName: String?`을 추가합니다. 값은 해당 리소스의 Space 소유자 표시 이름이며, 요청자 Space의 리소스면 `null`입니다. 매퍼 조회와 서비스 조립을 고칩니다.
3. 계약: springdoc preview 생성·정규화 후 승인 스냅샷을 갱신하고 `pnpm --filter @agolock/frontend generate:api`를 실행합니다. 공유·계층 범위를 생성기로 초안에 동기화해 `SharedResource` 불일치를 정리합니다. 현재 `sync_inventory_contracts.py`는 공유 전체를 동기화하지 않으므로 해당 범위의 생성 경로도 보완해야 합니다. 규칙은 [계약](../contracts.md)을 따릅니다.
4. 프론트엔드: `frontend/src/features/hierarchy/api.ts`의 `Room`, `Shelf`, `Box` 타입에 `ownerName`을 추가합니다. `frontend/src/shared/lib/permission.ts`에 표시용 헬퍼를 만들어 `ownerName`이 있으면 `{이름} 소유`, 없으면 기존 권한 라벨을 보입니다. 적용 위치는 공간 트리 행, 방·선반·박스 상세 헤더, 재고 상세 헤더입니다.
5. 테스트: 백엔드는 공유받은 Space와 내 Space에서의 `ownerName` 값을 검증하고, 프론트엔드는 `renderApp` 기반 테스트로 `민지 소유` 표시를 검증합니다.
6. 문서: [공유와 권한](../sharing.md)과 [화면과 흐름](../frontend.md)에 표시 규칙을 적고, 이 문서의 상태와 `last_verified_commit`을 갱신합니다.

**완료 조건**

- 공유받은 방·선반·박스의 모든 화면에서 소유자 이름이 같은 형태로 표시됩니다.
- 내 리소스는 `내 소유`로 표시됩니다.
- OpenAPI와 실제 응답 형태가 일치합니다.
- `./gradlew check`, `pnpm lint`, `pnpm test`, `pnpm build`, `python3 scripts/docs_check.py`가 통과합니다.

### 3.2 스크린리더 실기기 확인

새 `Choice`와 `RadioCard`는 입력을 `sr-only`로 숨기고 사각 박스를 따로 그리는 구조입니다. axe 점검은 통과했지만 읽는 순서와 라벨은 실제 기기로만 확인할 수 있습니다.

| 확인 대상 | 기준 |
|---|---|
| 설정의 화면 모드(`RadioCard` 3개) | 그룹 이름 `화면 모드 선택`, 각 항목 이름과 선택 상태, 방향키 이동 |
| 검색의 `사진 있음`(`Choice`) | 체크 상태 읽힘, 스페이스로 토글 |
| 재고 편집의 오버라이드 토글 | 라벨과 `마스터: 값` 배지가 함께 읽힘 |
| 새 `Select`의 화살표 칸 | 장식이므로 읽히지 않고, 네이티브 셀렉트가 그대로 읽힘 |
| 모바일 헤더 아바타 링크 | `설정으로 이동`으로 읽힘 |

환경은 macOS VoiceOver(Safari), iOS VoiceOver, Windows NVDA(Firefox 또는 Chrome)를 권장합니다. 문제를 찾으면 이 문서에 기록하고 `choice.tsx`를 고칩니다.

### 3.3 미실시 시각 확인

이번 점검에서 시각 확인을 하지 못한 부분입니다. 목 API를 확장해 캡처하면 됩니다.

| 대상 | 확인할 것 |
|---|---|
| 이미지가 있는 화면 | `READY`, `UPLOADING`, `FAILED` 이미지의 갤러리 썸네일 줄, 대표 이미지 표시, 삭제 버튼 터치 영역 44px, 업로드 진행 바와 간격(`image-gallery.tsx`의 위쪽 선을 제거해서 간격이 바뀜) |
| 오류·충돌 상태 | 화면별 403, 404, 409(버전 충돌), 네트워크 끊김의 문구와 복구 동작. PLAN-014의 충돌 입력 보존·최신 조회 거부 회귀는 통과했으나 실제 화면·실기기 시각 확인은 남음 |
| 다크 모드 | 정적 측정만 모든 화면에서 했고 눈으로 본 것은 일부. 반투명 글자(`text-foreground/60` 등)의 다크 명암비 |
| 모바일 등록 시트 키보드 | 키보드가 올라올 때 본문만 스크롤되고 하단 액션이 고정되는지 (실기기) |

## 4. 작업 환경과 주의사항

| 항목 | 내용 |
|---|---|
| 프론트엔드 명령 | `frontend/`에서 `npx tsc --noEmit`, `npx eslint . --max-warnings=0`, `npx prettier --check src e2e eslint.config.js`, `npx vitest run`, `npx playwright test` |
| 개발 서버 | Playwright 설정이 `127.0.0.1:5173`을 재사용합니다. 이미 떠 있으면 새로 띄우지 않습니다. |
| `prettier --write src` 주의 | 생성 파일 `frontend/src/shared/api/schema.d.ts`까지 재포맷합니다. 실행 뒤 `git status`를 확인하고 의도치 않은 변경은 `git checkout`으로 되돌립니다. |
| `cn()` 주의 | `frontend/src/shared/lib/cn.ts`가 `text-label`, `text-title`을 글자 크기로 등록합니다. 새 글자 크기 토큰을 추가하면 같은 곳에 등록하지 않으면 `tailwind-merge`가 색 클래스로 오인해 지웁니다. |
| 린트 R-08 규칙 | `mt-*`, `mb-*`, `gap-1.5` 같은 스케일 밖 값, `text-[Npx]`를 쓰면 린트가 실패합니다. 예외는 이유를 적은 `eslint-disable-next-line`으로만 둡니다. |
| 캡처 방법 | 세션 임시 폴더에 둔 스크립트는 남지 않습니다. 다시 하려면 `@playwright/test`의 `chromium`으로 `390×844`(모바일, `isMobile`), `1280×800`을 `localStorage['agolock.theme']` 라이트·다크로 띄우고, `**/api/v1/**`를 `page.route`로 목 처리합니다. 목 데이터 형태는 `frontend/e2e/a11y.spec.ts`의 `mockApi`를 참고하세요. |
| 모바일 가로 넘침 측정 | 모바일 컨텍스트에서는 `documentElement.scrollWidth`가 레이아웃 뷰포트를 따라 늘어나 믿을 수 없습니다. 요소의 `getBoundingClientRect().right`를 뷰포트 폭과 비교하세요. |
| 문서 갱신 절차 | [AGENTS.md 3번 항목](../../AGENTS.md)을 따릅니다. 변경 후 `python3 scripts/docs_impact.py`, `python3 scripts/docs_check.py`를 실행합니다. |

## 5. 알려진 한계

- 문장 안에 들어가는 링크(`회원가입`, `로그인`)는 줄 높이를 깨지 않도록 `tap-area`로 터치 영역만 확장했습니다. 줄 간격이 좁은 곳에서는 인접 링크의 터치 영역이 겹칠 수 있습니다.
- 권한 라벨 `내 소유`는 공유받은 리소스의 소유자를 알려주지 못합니다. 3.1번 항목의 결정이 필요합니다.
- 설계 시안은 [design/review/2026-10-23/fixes](../../design/review/2026-10-23/fixes/index.html)에 있고 제품 데이터가 아닙니다.
