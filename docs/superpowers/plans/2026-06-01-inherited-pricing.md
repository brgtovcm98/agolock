# 상속형 단위 가격(Inherited Pricing) Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 단위 가격을 "스냅샷"에서 "상속형"으로 바꿔, 품목 가격 변경이 상속 단위(가격 미지정)의 가치에 자동 반영되게 한다.

**Architecture:** `stocks.price = NULL`이면 품목가를 라이브 상속, 값이 있으면 개별 고정. **유효가격 = `COALESCE(stocks.price, items.price)`** 를 집계·표시 지점에서 해석한다. 새 테이블/체크박스/캐스케이드 없음. 기존 데이터는 일회성 백필(품목가와 같은 값 → NULL)로 전환.

**Tech Stack:** Spring Boot 4 / Java 25 / MyBatis(XML) / PostgreSQL(+Flyway) / H2(테스트) / Thymeleaf / JUnit5 + Mockito + AssertJ.

**Spec:** `docs/superpowers/specs/2026-06-01-inherited-pricing-design.md`

---

## File Structure (변경/생성 맵)

| 파일 | 책임 | 변경 |
|---|---|---|
| `service/StockService.java` | 입고 단위 생성 | 스냅샷 중단(line 588), quick-create 상속(line 266) |
| `test/.../service/StockServiceTest.java` | 생성 가격 동작 | 스냅샷 테스트 2개 반전 + quick 상속 테스트 |
| `mapper/SpaceMapper.xml` | 공간 집계 | `total_value`를 유효가격 합으로 |
| `test/.../mapper/SpaceMapperTest.java` | 집계 검증 | 상속/고정 혼합 테스트 추가 |
| `model/dto/StockDetailDTO.java` | 단위 상세 | `itemPrice` 필드 추가 |
| `mapper/StockMapper.xml` | 단위 상세 SQL | resultMap + `i.price AS item_price` |
| `test/.../mapper/StockMapperTest.java` | 상세 검증 | itemPrice 동반 테스트 추가 |
| `templates/stocks/fragments/detail-row.html` | 단위 표시 | 유효가격 표시 + "상속" 태그 |
| `messages*.properties` (5개) | i18n | `view.stock.price.inherited` |
| `db/migration/V7__backfill_inherited_pricing.sql` | 데이터 전환 | 신규 |
| `CLAUDE.md` | 문서 동기화 | Pricing semantics 갱신 |

각 Task는 독립적으로 빌드/테스트 통과 가능하도록 구성. 명령은 모두 repo 루트(`/home/dooftac/IdeaProjects/SeuStock`)에서 실행.

---

## Task 1: 생성 시 스냅샷 중단 → 상속 기본값

**Files:**
- Modify: `src/main/java/com/seu/seustock/service/StockService.java:588`, `:266`
- Test: `src/test/java/com/seu/seustock/service/StockServiceTest.java` (기존 테스트 2개 반전 + 1개 추가)

배경: 모든 입고 경로는 `prepareInboundUnits()`(line 571-592)의 단일 지점 line 588에서 가격을 정한다. 현재는 미입력 시 `item.getPrice()`를 복사(스냅샷). 이를 `spec.price()` 그대로(미입력이면 null=상속)로 바꾼다. quick-create(`createWithNewItem`)는 입력 가격을 item에 이미 넣으므로, 단위는 상속(null)시킨다.

- [ ] **Step 1: 기존 스냅샷 테스트 2개를 상속 기대값으로 반전 (failing test)**

`StockServiceTest.java`에서 아래 두 메서드를 교체한다.

```java
    @Test
    void create_leavesPriceNullWhenFormPriceNull() {
        item.setPrice(new BigDecimal("5000"));
        when(itemMapper.findByExternalId(ITEM_EXTERNAL_ID)).thenReturn(Optional.of(item));
        when(spaceMapper.findByExternalId(SPACE_EXTERNAL_ID)).thenReturn(Optional.of(space));

        stockService.create(stockForm(ITEM_EXTERNAL_ID, SPACE_EXTERNAL_ID, null, null), USERNAME);

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<StockDTO>> captor = ArgumentCaptor.forClass(List.class);
        verify(stockMapper).insertStocks(captor.capture());
        assertThat(captor.getValue().get(0).getPrice()).isNull();   // 상속: 스냅샷 안 함
    }
```

```java
    @Test
    void addUnits_leavesPriceNullWhenFormPriceNull() {
        item.setPrice(new BigDecimal("5000"));
        when(itemMapper.findByExternalId(ITEM_EXTERNAL_ID)).thenReturn(Optional.of(item));
        when(spaceMapper.findByExternalId(SPACE_EXTERNAL_ID)).thenReturn(Optional.of(space));

        stockService.addUnits(stockInOutForm(SPACE_EXTERNAL_ID, null, null), USERNAME);

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<StockDTO>> captor = ArgumentCaptor.forClass(List.class);
        verify(stockMapper).insertStocks(captor.capture());
        assertThat(captor.getValue().get(0).getPrice()).isNull();   // 상속
    }
```

`create_usesFormPriceOverItemPrice`(명시 가격 8000 저장)는 **그대로 둔다** — 상속 모델에서도 유효.

- [ ] **Step 2: 테스트 실패 확인**

Run: `./gradlew test --tests "com.seu.seustock.service.StockServiceTest"`
Expected: FAIL — `create_leavesPriceNullWhenFormPriceNull`/`addUnits_leavesPriceNullWhenFormPriceNull`가 `expected: null but was: 5000` (현재 코드가 item 가격을 복사하므로).

- [ ] **Step 3: 스냅샷 중단 구현**

`StockService.java` line 588 교체:

```java
            unit.setPrice(spec.price());
```

(기존 `unit.setPrice(spec.price() != null ? spec.price() : item.getPrice());` 삭제.)

`StockService.java` line 266(`createWithNewItem`의 InboundSpec)에서 단위가 상속하도록 가격 인자를 `null`로:

```java
                new InboundSpec(form.getCount(), null, null, null, null, null, form.getMemo()));
```

(기존 `form.getPrice()` → `null`. 입력 가격은 이미 `item.setPrice(form.getPrice())`로 품목에 반영되므로 단위는 상속.)

- [ ] **Step 4: 테스트 통과 확인 (전체 StockServiceTest)**

Run: `./gradlew test --tests "com.seu.seustock.service.StockServiceTest"`
Expected: PASS (반전된 2개 + 기존 전부). quick-create 관련 기존 테스트가 가격을 단언한다면 함께 PASS인지 확인 — 깨지면 그 테스트도 "상속(null)" 기대로 맞춘다.

- [ ] **Step 5: Commit**

```bash
git add src/main/java/com/seu/seustock/service/StockService.java src/test/java/com/seu/seustock/service/StockServiceTest.java
git commit -m "feat(pricing): stop snapshotting item price at stock creation (inherit by default)"
```

---

## Task 2: 공간 집계 = 유효가격 합

**Files:**
- Modify: `src/main/resources/mapper/SpaceMapper.xml` (`findSummariesBySpaceIds`의 `total_value` 서브쿼리)
- Test: `src/test/java/com/seu/seustock/mapper/SpaceMapperTest.java` (테스트 추가)

- [ ] **Step 1: 상속/고정 혼합 집계 테스트 추가 (failing test)**

`SpaceMapperTest.java`의 `findSummariesBySpaceIds_expiryWindowBoundariesInclusive` 뒤에 추가:

```java
    @Test
    void findSummariesBySpaceIds_inheritedUnitsUseItemPrice() {
        LocalDate today = LocalDate.now();
        Long space = insertSpace("진열대");

        ItemDTO priced = new ItemDTO();
        priced.setUserId(userId);
        priced.setName("음료");
        priced.setPrice(new BigDecimal("1000"));
        itemMapper.insertItem(priced);

        Long free = insertItem("증정품");  // 품목가 null

        insertStock(priced.getId(), space, null, null, StockStatus.IN_STOCK, null, null);                  // 상속 → 1000
        insertStock(priced.getId(), space, null, null, StockStatus.IN_STOCK, new BigDecimal("500"), null); // 고정 → 500
        insertStock(free, space, null, null, StockStatus.IN_STOCK, null, null);                            // 상속(품목가 null) → 0
        insertStock(free, space, null, null, StockStatus.IN_STOCK, new BigDecimal("300"), null);           // 고정 → 300

        SpaceDTO s = spaceMapper.findById(space).orElseThrow();
        SpaceSummaryDTO summary = spaceMapper.findSummariesBySpaceIds(List.of(space), today, today.plusDays(7))
                .stream().filter(x -> x.getSpaceExternalId().equals(s.getExternalId())).findFirst().orElseThrow();

        assertThat(summary.getStockCount()).isEqualTo(4);
        assertThat(summary.getTotalValue()).isEqualByComparingTo("1800");  // 1000 + 500 + 0 + 300
    }
```

(`ItemDTO` import는 이미 존재. `insertStock`/`insertItem`/`insertSpace` 헬퍼는 Task 진입 시점에 이미 존재.)

- [ ] **Step 2: 테스트 실패 확인**

Run: `./gradlew test --tests "com.seu.seustock.mapper.SpaceMapperTest"`
Expected: FAIL — `expected: 1800 but was: 800` (현재 쿼리는 `SUM(st.price)`라 상속 단위 1000을 0으로 셈: 500+300=800).

- [ ] **Step 3: total_value 서브쿼리를 유효가격 합으로 변경**

`SpaceMapper.xml`의 `findSummariesBySpaceIds` 내 `total_value` 서브쿼리를 교체:

```xml
            (SELECT COALESCE(SUM(COALESCE(st.price, i.price)), 0) FROM stocks st
                JOIN items i ON st.item_id = i.id
                WHERE st.space_id = s.id AND st.status = 'IN_STOCK') AS total_value,
```

(기존 `(SELECT COALESCE(SUM(st.price), 0) FROM stocks st WHERE st.space_id = s.id AND st.status = 'IN_STOCK') AS total_value,` 교체.)

- [ ] **Step 4: 테스트 통과 확인 (전체 SpaceMapperTest)**

Run: `./gradlew test --tests "com.seu.seustock.mapper.SpaceMapperTest"`
Expected: PASS — 신규 + 기존 `aggregatesPerSpace`(itemA 가격 null이라 상속분 0, 합계 3700 불변) 모두 통과.

- [ ] **Step 5: Commit**

```bash
git add src/main/resources/mapper/SpaceMapper.xml src/test/java/com/seu/seustock/mapper/SpaceMapperTest.java
git commit -m "feat(pricing): aggregate space value using effective price (COALESCE stock/item)"
```

---

## Task 3: 단위 상세에 품목가 동반 + 유효가격 표시

**Files:**
- Modify: `src/main/java/com/seu/seustock/model/dto/StockDetailDTO.java`
- Modify: `src/main/resources/mapper/StockMapper.xml` (resultMap `StockDetailResultMap`, sql fragment `StockDetailSelect`)
- Modify: `src/main/resources/templates/stocks/fragments/detail-row.html`
- Modify: `src/main/resources/messages.properties` (+ `_en`/`_ja`/`_zh_CN`/`_mn`)
- Test: `src/test/java/com/seu/seustock/mapper/StockMapperTest.java`

- [ ] **Step 1: StockDetailDTO에 itemPrice 필드 추가 (컴파일용 스캐폴딩)**

`StockDetailDTO.java`의 `private BigDecimal price;` 아래에 추가:

```java
    private BigDecimal itemPrice;
```

- [ ] **Step 2: 상세 조회가 itemPrice를 채우는지 테스트 추가 (failing test)**

`StockMapperTest.java`에 추가(필요 import: `StockDetailDTO`, `BigDecimal` — 이미 존재):

```java
    @Test
    void findDetailByExternalId_carriesItemPriceForInheritedUnit() {
        ItemDTO priced = new ItemDTO();
        priced.setUserId(userId);
        priced.setName("커피");
        priced.setPrice(new BigDecimal("3000"));
        itemMapper.insertItem(priced);

        StockDTO unit = new StockDTO();
        unit.setItemId(priced.getId());
        unit.setSpaceId(spaceId);
        unit.setPrice(null);            // 상속
        stockMapper.insertStock(unit);

        StockDetailDTO detail = stockMapper.findDetailByExternalId(unit.getExternalId(), userId).orElseThrow();

        assertThat(detail.getPrice()).isNull();                         // 원본 보존(상속이면 null)
        assertThat(detail.getItemPrice()).isEqualByComparingTo("3000"); // 품목가 동반
    }
```

- [ ] **Step 3: 테스트 실패 확인**

Run: `./gradlew test --tests "com.seu.seustock.mapper.StockMapperTest"`
Expected: FAIL — `detail.getItemPrice()`가 null (아직 SELECT/매핑 안 함) → `expected: 3000 but was: null`.

- [ ] **Step 4: resultMap + SQL 매핑 추가**

`StockMapper.xml`의 `StockDetailResultMap`에서 `<result property="price" column="price"/>` 아래에 추가:

```xml
        <result property="itemPrice"               column="item_price"/>
```

같은 파일 `<sql id="StockDetailSelect">` 내 `s.price,` 다음 줄에 추가:

```xml
               i.price AS item_price,
```

(해당 sql fragment는 이미 `JOIN items i ON s.item_id = i.id` 포함 — 추가 조인 불필요.)

- [ ] **Step 5: 테스트 통과 확인**

Run: `./gradlew test --tests "com.seu.seustock.mapper.StockMapperTest"`
Expected: PASS.

- [ ] **Step 6: i18n 키 추가 (5개 파일)**

각 파일에서 `view.stock.form.price.placeholder=...` 줄 **다음**에 한 줄 추가:

- `messages.properties`: `view.stock.price.inherited=상속`
- `messages_en.properties`: `view.stock.price.inherited=Inherited`
- `messages_ja.properties`: `view.stock.price.inherited=継承`
- `messages_zh_CN.properties`: `view.stock.price.inherited=继承`
- `messages_mn.properties`: `view.stock.price.inherited=Өвлөсөн`

(만약 `view.stock.form.price.placeholder`가 없는 파일이 있으면 `view.stock.form.price.label=...` 줄 다음에 추가.)

- [ ] **Step 7: detail-row.html 표시를 유효가격으로**

`detail-row.html`의 가격 표시 블록(현재 line 46-49)을 교체:

```html
        <p class="mt-0.5" th:if="${stock.price != null or stock.itemPrice != null}"
           th:with="effPrice=${stock.price != null ? stock.price : stock.itemPrice}">
            <span class="text-xs text-slate-400" th:text="#{view.stock.form.price.label}">가격</span>
            <span th:text="#{view.common.price.format(${#numbers.formatInteger(effPrice, 1, 'COMMA')})}">5,000원</span>
            <span th:if="${stock.price == null}" class="text-xs text-slate-300"
                  th:text="|(#{view.stock.price.inherited})|">(상속)</span>
        </p>
```

(편집 폼의 `th:field="*{price}"`(line 160)는 원본 price로 프리필되어 상속이면 빈칸 — 변경 불필요.)

- [ ] **Step 8: 전체 빌드 확인**

Run: `./gradlew build`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 9: Commit**

```bash
git add src/main/java/com/seu/seustock/model/dto/StockDetailDTO.java \
        src/main/resources/mapper/StockMapper.xml \
        src/main/resources/templates/stocks/fragments/detail-row.html \
        src/main/resources/messages*.properties \
        src/test/java/com/seu/seustock/mapper/StockMapperTest.java
git commit -m "feat(pricing): show effective unit price (inherited from item) in detail view"
```

---

## Task 4: 기존 데이터 백필 마이그레이션 (V7)

**Files:**
- Create: `src/main/resources/db/migration/V7__backfill_inherited_pricing.sql`

스키마 변경 없음(`stocks.price`는 이미 nullable) → `schema-test.sql` 수정 불필요. Flyway 전용(테스트에서 미실행).

- [ ] **Step 1: 마이그레이션 파일 작성**

`V7__backfill_inherited_pricing.sql`:

```sql
-- 상속형 가격 전환: 단위 가격이 소유 품목의 현재 가격과 같으면(=순수 스냅샷) NULL로 비워 상속시킨다.
-- 품목가와 다른 단위(개별 설정)는 그대로 보존한다.
UPDATE stocks s
SET price = NULL
FROM items i
WHERE s.item_id = i.id
  AND s.price IS NOT NULL
  AND i.price IS NOT NULL
  AND s.price = i.price;
```

- [ ] **Step 2: 마이그레이션 유효성 확인 (앱 기동)**

Docker 가용 시: `./gradlew bootRun` 로 Flyway가 V7을 적용하는지 로그 확인(`Migrating schema "public" to version "7 - backfill inherited pricing"`), 오류 없이 기동되면 중단. Docker 불가 환경이면 이 단계는 사용자에게 위임(`! ./gradlew bootRun`).

- [ ] **Step 3: Commit**

```bash
git add src/main/resources/db/migration/V7__backfill_inherited_pricing.sql
git commit -m "feat(pricing): V7 backfill — null out snapshot prices equal to item price"
```

---

## Task 5: 문서 동기화 (CLAUDE.md)

**Files:**
- Modify: `CLAUDE.md` (Pricing semantics 단락)

- [ ] **Step 1: Pricing semantics 단락 교체**

`CLAUDE.md`의 다음 문장을 찾아:

```
**Pricing semantics:** `stocks.price` is a **snapshot** copied from `items.price` at stock creation; editing an item's price is not retroactive (existing units keep their snapshot, and a unit's price can be edited individually). All prices are KRW integers (`NUMERIC(12,0)`, no decimals).
```

아래로 교체:

```
**Pricing semantics:** `stocks.price` is an **optional per-unit override**. When `NULL`, the unit *inherits* the owning item's current `items.price` (live); when set, it is a fixed custom price. The **effective price** is `COALESCE(stocks.price, items.price)` and is what every aggregate/display resolves (e.g., the space-list value rollup, `StockDetailDTO.itemPrice`). Changing an item's price therefore retroactively moves the value of all inherited units. Stock is created inheriting by default (price left `NULL` unless the form supplies an explicit price). All prices are KRW integers (`NUMERIC(12,0)`, no decimals).
```

- [ ] **Step 2: Commit**

```bash
git add CLAUDE.md
git commit -m "docs: update pricing semantics to inherited model"
```

---

## Self-Review (작성자 점검 결과)

**Spec coverage:**
- 의미론(상속/고정, 유효가격) → Task 1·2·3 ✓
- 생성 스냅샷 중단 → Task 1 ✓
- 집계 COALESCE → Task 2 ✓
- 상세 표시/itemPrice → Task 3 ✓
- 백필 마이그레이션 → Task 4 ✓
- 문서 동기화 → Task 5 ✓
- "새 테이블/체크박스/캐스케이드 없음" → 어떤 Task도 추가 안 함 ✓

**Placeholder scan:** 모든 코드 step에 실제 코드/명령/기대값 포함. TBD 없음.

**Type consistency:** `StockDetailDTO.itemPrice`(BigDecimal) ↔ resultMap `item_price` ↔ sql `i.price AS item_price` ↔ 테스트 `getItemPrice()` 일치. `SpaceSummaryDTO.totalValue`(BigDecimal) 합산식과 테스트 `isEqualByComparingTo` 일치. `findSummariesBySpaceIds`/`findDetailByExternalId` 시그니처는 기존 인터페이스와 동일.

**주의(실행 시):** quick-create 등 가격을 단언하는 기존 StockServiceTest가 더 있으면(Step 1·4) 상속(null) 기대로 함께 정정. 커밋은 사용자 정책상 실제 실행 단계에서 진행.
