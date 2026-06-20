# 상속형 단위 가격(Inherited Pricing) 설계

작성일: 2026-06-01

## Context (왜)

공간 목록에 추가한 **자산가치 집계**(`SUM(stocks.price)`)는 품목 가격을 바꿔도 움직이지 않는다. 원인은 `stocks.price`가 생성 시점의 **스냅샷**이기 때문(CLAUDE.md "Pricing semantics"). 사용자는 "품목 가격을 바꾸면 기존 재고 가치에도 반영"되길 원했고, 처음엔 "체크박스로 일괄 갱신"을 검토했으나 *어떤 단위까지 덮어쓸지(개별 수정분 보존 등)*의 비즈니스 로직이 과도하게 복잡했다.

브레인스토밍 결과, 근본 원인은 **단위 가격을 스냅샷으로 볼지 상속으로 볼지**가 미정이었던 것. 결정: **상속형(derived) 모델**로 전환한다. 이는 캐스케이드·체크박스·가격원장 테이블을 통째로 제거하면서 의도를 만족시킨다.

## 결정 사항

- **모델**: 상속형(A). `stocks.price = NULL` → "품목가 상속(라이브)", 값이 있으면 → 개별 고정(override).
- **유효가격(effective price) = `COALESCE(stocks.price, items.price)`** — 집계·표시 어디서든 이 식으로 단위 가격을 해석한다.
- **기존 데이터**: 일회성 백필. `stocks.price`가 소유 품목의 현재 `items.price`와 **같은** 단위는 `NULL`(상속)로, **다른** 단위는 개별 고정으로 보존.
- **하지 않는 것(YAGNI)**: 가격 전용 transaction/이력 테이블 없음, "기존에 반영" 체크박스 없음, 캐스케이드 로직 없음. (품목가 변경 감사 로그가 나중에 필요하면 별도 작업으로.)

## 의미론 (semantics)

| 상태 | `stocks.price` | 유효가격 | 품목가 변경 시 |
|---|---|---|---|
| 상속 | `NULL` | `items.price` | **자동 반영** |
| 개별 고정 | 값 | 그 값 | 영향 없음 |

- 생성 시: 사용자가 가격을 명시 입력하면 그 값(고정), 비우면 `NULL`(상속).
- 개별 수정 시: 빈칸으로 저장하면 다시 상속, 값을 넣으면 개별 고정.
- 집계는 IN_STOCK 단위만 대상으로 유효가격을 합산.

## 컴포넌트별 변경

1. **생성 — 스냅샷 중단** (`StockService.java:588`)
   `unit.setPrice(spec.price() != null ? spec.price() : item.getPrice())` → `unit.setPrice(spec.price())` (미입력이면 null=상속).
   - 폼(`stocks/fragments/in-modal.html`, `quick-modal.html`)은 이미 가격칸을 placeholder로 비워둠. **JS가 품목 선택 시 가격칸을 자동 채우지 않는지** 확인해 기본 비움을 보장(placeholder에 현재 품목가 노출은 유지/추가).

2. **공간 집계** (`SpaceMapper.xml`의 `total_value` 서브쿼리)
   `COALESCE(SUM(st.price),0)` → `COALESCE(SUM(COALESCE(st.price, i.price)),0)`, `JOIN items i ON st.item_id = i.id`, `status='IN_STOCK'` 유지.

3. **단위 표시/상세** (`StockDetailDTO` + `StockMapper.xml` 상세/검색 SQL, 이미 `JOIN items i` 존재)
   - DTO에 `itemPrice`(BigDecimal) 필드 추가. SQL에 `i.price AS item_price` 추가, `s.price`(원본, nullable)는 유지.
   - 화면(`stocks/fragments/detail-row.html` 등)은 유효가격 `price ?: itemPrice` 표시(원하면 "상속" 태그). 편집 폼은 **원본 `price`**(상속이면 빈칸)로 프리필.

4. **개별 단위 수정** (`updateDetails` / `StockUpdateForm`)
   가격 빈칸 → `NULL`(상속), 값 → 개별 고정. 매퍼는 이미 `price = #{form.price}`로 null 허용.

5. **마이그레이션** `V7__backfill_inherited_pricing.sql`
   ```sql
   UPDATE stocks s
   SET price = NULL
   FROM items i
   WHERE s.item_id = i.id
     AND s.price IS NOT NULL
     AND i.price IS NOT NULL
     AND s.price = i.price;
   ```
   스키마 변경 없음(`stocks.price`는 이미 nullable) → `schema-test.sql` 수정 불필요. Flyway 전용(테스트에선 미실행).

## 파급 범위 메모

`stocks.price`를 **표시/집계**하는 지점만 유효가격으로 해석하면 된다. 내부 로직(삭제 가드, 출고 대상 선정 등 — 가격을 안 읽음)은 무관. 현재 영향 지점은 위 2·3 뿐이며, 다른 SELECT들(`findById`/`findBySpaceId` 등)은 원본 `price`를 그대로 반환해도 무방(편집/내부용).

## 테스트

- **매퍼(SpaceMapperTest)**: 품목가가 있는 품목의 **상속(null-price) IN_STOCK 단위**가 집계에 품목가만큼 기여하는지, 개별 고정 단위는 자기 값으로 기여하는지(혼합 케이스) 검증. 기존 `aggregatesPerSpace`는 itemA에 가격이 없어 영향 없음 → 가격 있는 신규 케이스 추가.
- **매퍼(StockMapperTest)**: 상세 조회가 `itemPrice`를 채우고 원본 `price`(null 포함)를 보존하는지.
- **서비스(StockServiceTest)**: 입고 시 가격 미입력 → `stocks.price = null` 저장(상속), 입력 시 그 값 저장.
- 마이그레이션 SQL은 Flyway 전용이라 단위테스트 대상 아님(런타임 동작은 위 집계 테스트로 커버).

## 리스크 / 캐비엇

- 백필 휴리스틱: "우연히 품목가와 같은 값으로 개별 설정한 단위"도 상속으로 바뀜. 개인용 앱 범위에서 수용.
- 의미 변경: CLAUDE.md의 "snapshot, not retroactive" 서술을 상속형으로 갱신해야 함(문서 동기화).
- 상속 단위는 품목가가 바뀌면 *과거에 보이던 값도* 바뀐다(라이브). 집계는 IN_STOCK만 보므로 회계적으로 문제없음.
