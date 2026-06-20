# 들뢰즈 철학으로 본 SeuStock — 분석 + 적용 로드맵

> 작성일: 2026-06-02
> 대상: SeuStock (개인 재고관리 / Spring Boot 4 · Java 25 · MyBatis · PostgreSQL)
> 목적: 들뢰즈·가타리(『천 개의 고원』)의 **리좀(rhizome)**, **배치(agencement/assemblage)**, **becoming(생성)** 세 개념을
> 이 코드베이스에 *적용할 수 있는지* 판정하고, 적용 방법을 **티어드 로드맵**으로 제시한다.
>
> **이 문서는 지도(map)이지 구현 지시(tracing)가 아니다.** 코드는 아직 한 줄도 바꾸지 않았다. 로드맵의 각 항목은
> 채택 시 별도의 설계→계획→구현 사이클을 거친다.

---

## 0. 한 문장 판정

**적용 가능하다 — 부분적으로, 그러나 의미 있게. 단 홈 파인(striated) 코어를 *대체*하는 것이 아니라 그 *위에 덧대는*
오버레이로서.** 재고 관리는 본질적으로 홈 파인(격자·측량) 도메인이므로(실제 선반·박스는 물리적 격자다) 전면적인
리좀화/이벤트소싱 재작성은 도메인과 싸우며 YAGNI다. 가장 가성비 높고 철학적으로도 충실한 길은 **이미 코드에
잠재해 있는 세 갈래 "봉합선"을 증폭하는 Tier 1 작업들**이다.

---

## 1. 들뢰즈 개념 빠른 정의 (코드 독자용)

| 개념 | 핵심 | 대립쌍 |
|--|--|--|
| **리좀(rhizome)** | 중심·위계 없는 연결망. 어떤 점이든 다른 점과 연결(연결성), 이질적인 것끼리 이어짐(이질성), 중심 없는 다중성, 어디서 끊겨도 다시 자람(비기표적 단절), 여러 입구를 가진 지도 | **수목(arborescent)**: 하나의 뿌리 → 가지 → 잎. 고정 경로·이진 위계 |
| **배치(agencement)** | 이질적 요소들이 우연히 모여 함께 기능하는 앙상블. 사물의 정체성은 *본질*이 아니라 *무엇과 연결되고 무엇을 할 수 있는가(affects/역량)*의 효과 | **본질주의**: 고정된 실체/속성 |
| **영토화↔탈영토화** | 흐름을 안정화·코드화(영토화) ↔ 탈주선·새 연결로 풀려남(탈영토화) | — |
| **becoming(생성)** | 존재(being)가 아니라 과정·이행·흐름·강도. 사물은 *그 변형의 궤적*으로 정의됨 | **존재(being)**: 고정 상태·정체성 |
| **홈 파인/매끄러운 공간** | 격자화·측량된 공간(창고 격자!) ↔ 열린·유목적 공간 | — |

---

## 2. 핵심 진단

### 2.1 지배적 결: SeuStock은 수목적·홈 파인·존재 중심이다

이것은 결함이 아니라 도메인의 정직한 반영이다. 다만 *들뢰즈의 눈으로 보면* 다음이 그렇다.

- **저장 계층 `users → spaces → shelves → boxes`**: 엄격한 FK 트리에 루트→리프 `ON DELETE CASCADE`.
  `stocks`는 단일 `space_id`(필수) + 선택 `shelf_id`/`box_id`를 가지며, `chk_box_requires_shelf` 제약이
  경로의 연속성(박스가 있으면 반드시 선반이 있어야 함)을 강제한다.
  → 단위(unit)는 정확히 **한 주소**에만 존재한다. 이것이 들뢰즈의 **홈 파인 공간(striated space)** 그 자체다.
  근거: `V1__initial_schema.sql:9-34`(트리), `:48-67`(stocks 단일 위치 + `chk_box_requires_shelf`).

- **`items`(마스터) → `stocks`(개체)**: `items`는 "정의의 카탈로그"(본질/type), `stocks`는 그 물리적 사례(token).
  전형적인 type→token 수목 구도이며, 들뢰즈가 말한 **overcoding(초코드화)** 추상에 해당한다.

- **단일 루트 소유 체인**: 모든 서비스 메서드가 `Principal.getName()`(email) → user → 트리 하향으로 권한을 뿌리내린다.
  하나의 뿌리에서 모든 것이 내려온다.

- **`resolveVerifiedLocation()`** (`StockService.java:689-734`): 이 메서드의 임무 중 하나는 *가지 사이의 지름길을
  금지*하는 것이다 — 박스는 지정된 선반에 속해야 하고, 선반은 지정된 공간에 속해야 한다. 위반 시 `SecurityException`.
  → 코드가 적극적으로 **홈 파임의 치안을 유지**한다. 트리의 무결성을 지키는 경찰.

### 2.2 이미 잠재된 세 갈래 봉합선 (리좀·배치·becoming의 씨앗)

수목적 코어 안에 이미 비수목적 선들이 자라고 있다. 로드맵은 이 셋을 증폭한다.

1. **`item_lots` + `stocks.lot_id` — 위치 트리를 가로지르는 횡단선 (becoming + 리좀)**
   로트는 장소와 무관하게 계보(벤더·일자·유효기간)를 공유하는 단위들의 묶음, 일종의 "되기-배치(batch)"다.
   위치 트리와 **직교**하는 비수목적 선이며, `ON DELETE SET NULL`이라 로트가 사라져도 단위는 파괴되지 않는다 —
   들뢰즈의 **비기표적 단절(asignifying rupture)**: 어디서 끊겨도 단위는 살아남아 다시 연결될 수 있다.
   근거: `V2__add_item_serial_and_lot_tables.sql:50-74`, `StockService.resolveLot()` `:806-829`.

2. **`stock_transactions` — becoming 층 (그러나 묻혀 있음)**
   모든 `IN/OUT/MOVE/ADJUST`를 `from_*`/`to_*` 위치·memo와 함께 append-only로 기록한다. 이것이 단위의
   **흐름·궤적·강도의 원장**이다. `moveUnits()`는 실제 이동 궤적(from→to)을 남긴다.
   하지만 **현재 이 원장은 쓰기 전용 감사 로그**일 뿐, 사용자가 보는 일급 서사가 아니다. 시스템은 행 위의 현재
   `status`(존재)를 우선하고, 원장(생성)은 숨긴다.
   근거: `V1__initial_schema.sql:69-90`, `StockService.moveUnits()` `:540-622`, `changeStatus()` `:487-527`.

3. **상속 가격 `COALESCE(stocks.price, items.price)` — 관계적 가치 (배치)**
   기본적으로 `stocks.price`는 NULL이고, 단위는 item-배치와의 *관계에서* 가격을 발현한다. 특정 단위가 다른 가격을
   가져야 할 때만 명시적으로 override(탈영토화)한다. 가치가 내재 속성이 아니라 **연결의 효과**라는 점에서 진정
   배치적이다.
   근거: `CLAUDE.md`의 Pricing semantics, `V5`/`V7` 마이그레이션.

### 2.3 부차 신호

- `item_images`/`stock_images` 다대다 junction (`V1:106-126`) = 이미 존재하는 작은 리좀(이질적 연결: 이미지 ↔ 다수 호스트).
- `TrackingMode`(NONE/AUTO/MANUAL) + serial/lot 생성기 = 흐름을 포획해 기표를 각인하는 **코딩 기계**.
  `serial_next_sequence`는 홈 파임을 생산하는 시퀀스 코드다.
- `is_kept` + 부분 인덱스 `idx_stocks_available`(`status='IN_STOCK' AND is_kept=FALSE`)
  = "이동 가능한 가용 흐름" ↔ "예약되어 묶인(영토화된) 잔여"의 경계선.

### 2.4 명명할 중심 긴장

> **존재(`stocks.status` 컬럼) vs 생성(`stock_transactions` 원장).**

지금은 *존재*가 이긴다. 쿼리는 `status`를 읽고, becoming은 묻혀 있다. 그러나 한 가지를 분명히 해야 한다:
**재고 도메인은 본질적으로 홈 파인 공간이다.** 실제 선반과 박스는 물리적 격자이며, 사용자는 "이 물건이 *지금
어디 있는가*"를 알아야 한다. 따라서 완전한 리좀/이벤트소싱 재작성은 도메인과 싸운다.

들뢰즈·가타리 자신도 매끄러운 공간과 홈 파인 공간은 늘 **공존하며 서로 번역된다**고 본다(유목민조차 길을 낸다).
그러므로 올바른 전략은 *대체*가 아니라 **홈 파인 코어 위에 리좀을 덧대는 것** — 격자를 유지하되, 그 위로 가로지르는
선·연결·서사를 풍부하게 하는 것이다.

---

## 3. 티어드 로드맵

각 개념마다 **Tier 1(저위험 증축층, 스키마 보존 또는 가산만)**과 **Tier 2(심층 재설계)**를 제시한다.
★는 추천 경로다.

### A. 리좀 — 위치 트리를 완화하고 any-to-any 연결 허용

| 티어 | 내용 | 리스크 | 건드릴 곳 |
|--|--|--|--|
| **T1 ★** | **태그/라벨**(item·stock 대상 다대다): 위치와 직교하는 자유 분류. 다중성·이질성·**다중 입구**(태그/위치/로트/유효기간 어느 쪽으로도 진입)를 한 번에 제공하는 가장 값싼 리좀 승리. 부가로 **아이템 상호링크**(`item_links` self-FK, 유형: "대체재/세트/부품")로 비위계 연결망을, **저장된 뷰**로 횡단 필터를 일급 내비 진입점으로. | 낮음 | 신규 `V8__add_tags.sql`(`tags`/`item_tags`/`stock_tags`) + `schema-test.sql` 동기화; 신규 매퍼+XML(패턴: `ItemImageMapper`); 서비스·컨트롤러·템플릿·i18n 5개 파일 |
| **T2** | **일반화 컨테이너 그래프**: 고정 3단(space/shelf/box)을 단일 재귀 `containers`(self-FK `parent_id` + closure table)와 `stocks.container_id`로 대체해 임의 깊이 중첩 허용. 철학적으로 가장 순수하나 모든 패널 쿼리(`findPanelBy*DirectOnly`)·`resolveVerifiedLocation`·전 템플릿·QR 스캔 재작성 필요. 다중 부모(단위가 동시에 여러 위치)는 물리 현실과 충돌(반-도메인). | 높음 | 스키마 전면 + 거의 전 매퍼/뷰 |

**판단**: T1 태그·상호링크는 진짜 리좀적 다중성을 *저위험*으로, 그리고 물리적으로 실재하는 격자와 *싸우지 않고
보완*하며 준다. T2 컨테이너 그래프는 "임의 깊이 중첩"이 실제로 필요해질 때만 — 그 전엔 YAGNI.

### B. 배치(agencement) — 정체성·역량을 연결의 효과로; 영토화↔탈영토화

| 티어 | 내용 | 리스크 | 건드릴 곳 |
|--|--|--|--|
| **T1 ★** | **발현적 읽기모델 `StockAssemblageDTO`**: 단위 정체성을 쿼리 시점에 *조립*한다 — 유효가격(COALESCE)·유효 유효기간(unit→lot→item 순 상속)·위치 경로·로트 계보·태그·이미지 + **현재 가능한 역량(affects)**: dispatch 가능?·이동 가능?·가용?·만료임박? "행+속성"에서 "*무엇과 연결되고 무엇을 할 수 있는가로 정의되는 배치*"로 재프레임. 더불어 **inherit/override 패턴을 영토화/탈영토화로 명명**하고 유효기간 등에 일관 적용. | 낮음 | 신규 DTO + 조인·계산 쿼리(기존 `findDetailByExternalId` 확장); 서비스 메서드 |
| **T2** | 속성을 **부착식 컴포넌트**(`stock_attributes` 테이블 또는 JSONB 컬럼)로 모델링 → 이질적 부분의 개방 집합. 타입안전·쿼리·검증·MyBatis result-mapping 컨벤션과 충돌. *진짜 사용자정의 개방 속성*에 한해 JSONB 권장. 또한 `*_images`/`*_tags`를 다형 `attachments(host_type, host_id)`로 통합하면 이질 연결은 늘지만 FK 무결성·깔끔한 per-host 매퍼 패턴이 깨짐 → 무승부. | 중~높음 | 스키마 + 매퍼/검증 |

**판단**: T1 발현적 DTO는 가장 철학적으로 충실하면서 *실용적으로도* 유용하다("이 단위는 지금 정확히 무엇이고
무엇을 할 수 있는가"를 한 뷰로). T2 EAV/JSONB는 사용자정의 개방 속성이 실제로 필요할 때만.

### C. becoming — 상태(존재)보다 흐름(과정)을 우대; 원장을 일급으로

| 티어 | 내용 | 리스크 | 건드릴 곳 |
|--|--|--|--|
| **T1 ★** | **`stock_transactions`를 감사로그 → 일급 서사로 승격**: 단위 "이력(履歷)/생애" 타임라인 `GET /stocks/{id}/history`(IN→MOVE→…→OUT/ADJUST)로 *되기*를 가시화. 데이터는 *이미* 있고 지금은 쓰기 전용 → **최고 가성비 becoming 변화**. 부가로 **흐름 분석**(입출 속도·`from_*/to_*` 이동 그래프·체류시간 = 들뢰즈의 "선·강도"), **되기의 재개**(LOST→FOUND/RESTORED 전이 추가로 단위가 막다른 상태에 갇히지 않게 — 비기표적 단절과 복귀). | 낮음 | `StockTransactionMapper.findByStockId`(XML 추가); 서비스+컨트롤러; `stocks/fragments/history-modal.html`; i18n |
| **T2** | **이벤트 소싱**: `stock_transactions`를 단일 진실원천으로 삼고 `status`/위치를 이벤트 스트림의 fold(projection)로 도출 → "단위 = 그 역사"(becoming-over-being의 정점). 영속 모델·전 쿼리 역전, 동시성/일관성 부담 큼. 철학적 왕관보석이나 이 앱엔 비현실적: **T1 타임라인이 ~5% 비용으로 ~80% 체감가치**를 준다. 시간성(bitemporal) 모델은 중~높음. | 매우 높음 | 영속/쿼리 전면 |

**판단**: T1 이력 타임라인은 시스템이 *이미 기록하지만 숨기는* becoming을 표면화한다 — 쓰기 전용 감사로그를
단위의 서사로 바꾼다. 이벤트소싱(T2)은 철학적 이상이지만 YAGNI 재작성.

---

## 4. 종합 판정 & 권장 순서

**"적용 가능한가?" → 그렇다, 단 오버레이로서.** 도메인이 홈 파인이므로 코어를 갈아엎지 말고, 이미 있는 세
봉합선(lots·transactions·상속가격)을 증폭하라.

권장 우선순위(가성비·철학 충실도 종합):

1. **becoming C-T1 — 단위 이력 타임라인** (데이터가 이미 있어 가장 즉효, 가장 낮은 위험)
2. **리좀 A-T1 — 태그 + 상호링크** (다중 입구를 여는 가장 "리좀다운" 추가)
3. **배치 B-T1 — 발현적 `StockAssemblageDTO`** (1·2의 결과를 한 배치 뷰로 종합)

Tier 2 세 가지(컨테이너 그래프·EAV/JSONB·이벤트소싱)는 "철학적으로 가장 순수"하나 고위험/YAGNI다. **언제 가치가
생기는가**의 트리거를 기록해 둔다:
- 컨테이너 그래프 → 사용자가 4단 이상 중첩(예: 건물>층>방>선반>박스)을 실제로 요구할 때.
- EAV/JSONB → 품목군마다 제각각인 사용자정의 속성이 폭증할 때.
- 이벤트 소싱 → 감사/규제 요구로 "임의 과거 시점 상태 재구성"이 필수가 될 때.

---

## 5. 부록 — 들뢰즈 용어 ↔ 코드 대응표

| 들뢰즈 개념 | SeuStock에서의 현현 | 위치(근거) |
|--|--|--|
| 수목(arborescent) | `users→spaces→shelves→boxes` FK 트리 + 하향 CASCADE | `V1:9-34` |
| 홈 파인 공간(striated) | 단일 주소 `stocks`(space 필수 + shelf/box) + `chk_box_requires_shelf` | `V1:48-67` |
| 홈 파임의 치안 | `resolveVerifiedLocation()`이 가지 간 지름길 금지 | `StockService.java:689-734` |
| overcoding(초코드화) | `items`(본질) → `stocks`(사례) type→token | `V1:36-67` |
| 코딩 기계 | serial/lot 생성기 + `serial_next_sequence` 등 시퀀스 | `V6:1-12`, `resolveSerialNumbers()` |
| 횡단선 / 되기-배치 | `item_lots` + `stocks.lot_id` (위치 직교) | `V2:50-74`, `resolveLot()` `:806-829` |
| 비기표적 단절 | `lot_id ... ON DELETE SET NULL` (끊겨도 단위 생존) | `V2:71` |
| becoming 원장(현재 숨겨짐) | `stock_transactions` append-only `from_*/to_*` | `V1:69-90`, `moveUnits()` `:540-622` |
| 존재 vs 생성의 긴장 | `status` 컬럼(읽힘) vs 원장(묻힘) | `changeStatus()` `:487-527` |
| 관계적 가치(배치) | 상속 가격 `COALESCE(stocks.price, items.price)` | Pricing semantics, `V5`/`V7` |
| 영토화 ↔ 탈영토화 | 가격 상속(기본) ↔ unit override | 동상 |
| 작은 리좀(이질적 연결) | `item_images`/`stock_images` 다대다 | `V1:106-126` |
| 가용 흐름 ↔ 묶인 잔여 | `is_kept` + `idx_stocks_available` 부분 인덱스 | `V3`, `CLAUDE.md` Stock-keep |

---

*끝. 이 문서는 진단과 지도를 제공할 뿐, 어떤 코드도 변경하지 않는다. 로드맵 항목을 채택하면 각 항목은 프로젝트의
테스트 컨벤션(`@MybatisTest` 매퍼 테스트 / Mockito 서비스 테스트)과 i18n 5개 번들 규칙을 따르는 자체 사이클로 진행한다.*
