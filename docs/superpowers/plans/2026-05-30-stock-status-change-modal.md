# 재고 상태 변경 모달 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** `/stocks` 목록의 상태 뱃지를 클릭하면 모달이 열려 재고중 unit을 출고/분실/파손/폐기로 변경하고, 선택 사유 메모를 기존 메모 아래에 날짜와 함께 덧붙이며, 원장에 1행을 기록한다.

**Architecture:** 기존 `memo-modal` + `keep 토글` 패턴을 그대로 따른다. 상태 뱃지(`<span>`)를 HTMX 버튼으로 바꿔 `#modal`에 모달을 띄우고, `PUT /stocks/{id}/status`로 상태+메모를 전송한다. 서비스는 소유권·IN_STOCK 가드 후 `updateStatusAndMemoIfInStock`(신규 원자적 UPDATE)로 상태와 메모를 함께 갱신하고 `stock_transactions`에 기록한다. 변경된 unit은 더 이상 IN_STOCK이 아니므로 목록에서 행을 제거한다.

**Tech Stack:** Spring Boot 4 / Java 25 / MyBatis(XML 매퍼) / Thymeleaf + HTMX / JUnit5 · Mockito · `@MybatisTest`(H2).

**Spec:** `docs/superpowers/specs/2026-05-30-stock-status-change-modal-design.md`

---

## File Structure

| 파일 | 책임 | 변경 |
|---|---|---|
| `src/main/java/com/seu/seustock/mapper/StockMapper.java` | 매퍼 인터페이스 | `updateStatusAndMemoIfInStock` 추가 |
| `src/main/resources/mapper/StockMapper.xml` | SQL | 동명 `<update>` 추가 |
| `src/main/java/com/seu/seustock/service/StockService.java` | 비즈니스 로직 | `changeStatus` + `appendMemo` 추가 |
| `src/main/java/com/seu/seustock/controller/StockController.java` | HTTP 핸들러 | `GET/PUT /stocks/{id}/status` 추가 |
| `src/main/resources/templates/stocks/fragments/status-modal.html` | 모달 + 제거 응답 프래그먼트 | 신규 |
| `src/main/resources/templates/stocks/fragments/detail-row.html` | 행 뷰 | 상태 뱃지 → 버튼 |
| `src/main/resources/messages*.properties` (5개) | i18n | 키 6종 추가 |
| `src/test/java/com/seu/seustock/mapper/StockMapperTest.java` | 매퍼 테스트 | 3개 추가 |
| `src/test/java/com/seu/seustock/service/StockServiceTest.java` | 서비스 테스트 | 4개 추가 |

작업 브랜치는 현재 `develop`. 별도 브랜치 생성 불필요.

---

## Task 1: Mapper `updateStatusAndMemoIfInStock`

IN_STOCK 상태이고 사용자 소유인 unit에 한해 `status`와 `memo`를 원자적으로 갱신한다.
`updateIsKept`/`updateDetails`와 동일한 `external_id + status='IN_STOCK' + item_id IN (user 소유)` 가드.

**Files:**
- Modify: `src/main/java/com/seu/seustock/mapper/StockMapper.java`
- Modify: `src/main/resources/mapper/StockMapper.xml`
- Test: `src/test/java/com/seu/seustock/mapper/StockMapperTest.java`

- [ ] **Step 1: 매퍼 테스트 3개 작성**

`StockMapperTest.java`의 `findByExternalId_notFound_returnsEmpty()` 테스트 바로 아래(이미 추가된 `updateIsKept_togglesKeepFlagBothDirections` 부근)에 추가:

```java
    @Test
    void updateStatusAndMemoIfInStock_updatesWhenInStock() {
        StockDTO stock = buildStock();
        stock.setMemo("기존 메모");
        stockMapper.insertStock(stock);
        UUID externalId = stockMapper.findById(stock.getId()).orElseThrow().getExternalId();

        int updated = stockMapper.updateStatusAndMemoIfInStock(
                externalId, userId, StockStatus.DAMAGED, "기존 메모\n[2026-05-30] 파손 확인");

        assertThat(updated).isEqualTo(1);
        StockDTO found = stockMapper.findById(stock.getId()).orElseThrow();
        assertThat(found.getStatus()).isEqualTo(StockStatus.DAMAGED);
        assertThat(found.getMemo()).isEqualTo("기존 메모\n[2026-05-30] 파손 확인");
    }

    @Test
    void updateStatusAndMemoIfInStock_skipsWhenNotInStock() {
        StockDTO stock = buildStock();
        stockMapper.insertStock(stock);
        stockMapper.updateStatusIfInStock(stock.getId(), StockStatus.DISPATCHED);
        UUID externalId = stockMapper.findById(stock.getId()).orElseThrow().getExternalId();

        int updated = stockMapper.updateStatusAndMemoIfInStock(externalId, userId, StockStatus.LOST, "x");

        assertThat(updated).isZero();
        assertThat(stockMapper.findById(stock.getId()).orElseThrow().getStatus())
                .isEqualTo(StockStatus.DISPATCHED);
    }

    @Test
    void updateStatusAndMemoIfInStock_rejectsOtherUserUnit() {
        UserDTO otherUser = new UserDTO();
        otherUser.setEmail("otheruser2@test.com");
        otherUser.setNickname("otheruser2");
        otherUser.setPassword("password");
        userMapper.insertUser(otherUser);
        ItemDTO otherItem = new ItemDTO();
        otherItem.setUserId(otherUser.getId());
        otherItem.setName("다른 사용자 품목");
        itemMapper.insertItem(otherItem);
        StockDTO stock = buildStock();
        stock.setItemId(otherItem.getId());
        stockMapper.insertStock(stock);
        UUID externalId = stockMapper.findById(stock.getId()).orElseThrow().getExternalId();

        int updated = stockMapper.updateStatusAndMemoIfInStock(externalId, userId, StockStatus.LOST, "x");

        assertThat(updated).isZero();
    }
```

- [ ] **Step 2: 테스트 실패 확인 (컴파일 에러)**

Run: `./gradlew test --tests "com.seu.seustock.mapper.StockMapperTest"`
Expected: FAIL — `cannot find symbol: method updateStatusAndMemoIfInStock`.

- [ ] **Step 3: 매퍼 인터페이스 메서드 추가**

`StockMapper.java`에서 기존 `updateIsKept(...)` 선언 바로 아래에 추가:

```java
    int updateStatusAndMemoIfInStock(@Param("externalId") UUID externalId,
                                     @Param("userId") Long userId,
                                     @Param("status") StockStatus status,
                                     @Param("memo") String memo);
```

(`StockStatus`, `UUID`, `@Param`은 이미 import되어 있음.)

- [ ] **Step 4: XML `<update>` 추가**

`StockMapper.xml`에서 기존 `<update id="updateIsKept">...</update>` 바로 아래에 추가:

```xml
    <update id="updateStatusAndMemoIfInStock">
        UPDATE stocks
        SET status = #{status},
            memo   = #{memo}
        WHERE external_id = #{externalId}
          AND status = 'IN_STOCK'
          AND item_id IN (
              SELECT id
              FROM items
              WHERE user_id = #{userId}
          )
    </update>
```

- [ ] **Step 5: 테스트 통과 확인**

Run: `./gradlew test --tests "com.seu.seustock.mapper.StockMapperTest"`
Expected: PASS (신규 3개 포함 전체 통과).

- [ ] **Step 6: 커밋**

```bash
git add src/main/java/com/seu/seustock/mapper/StockMapper.java \
        src/main/resources/mapper/StockMapper.xml \
        src/test/java/com/seu/seustock/mapper/StockMapperTest.java
git commit -m "Add StockMapper.updateStatusAndMemoIfInStock with ownership/in-stock guard

Co-Authored-By: Claude Opus 4.8 <noreply@anthropic.com>"
```

---

## Task 2: Service `StockService.changeStatus`

소유권 확인 → IN_STOCK 가드 → 상태+메모 갱신 → 원장 기록. 출고는 OUT, 그 외는 ADJUST.
사유가 있으면 기존 메모 아래에 `[yyyy-MM-dd] 사유`를 덧붙이고, 비우면 메모는 그대로 둔다.

**Files:**
- Modify: `src/main/java/com/seu/seustock/service/StockService.java`
- Test: `src/test/java/com/seu/seustock/service/StockServiceTest.java`

- [ ] **Step 1: 서비스 테스트 4개 작성**

`StockServiceTest.java` 상단 import에 다음을 추가(없으면):

```java
import static org.mockito.ArgumentMatchers.eq;
```

그리고 `dispatchUnits_updatesStatusAndRecordsTransaction` 테스트 부근에 추가:

```java
    @Test
    void changeStatus_rejectsInStockSelection() {
        assertThatThrownBy(() -> stockService.changeStatus(STOCK_EXTERNAL_ID, StockStatus.IN_STOCK, null, USERNAME))
                .isInstanceOf(IllegalArgumentException.class);

        verify(stockMapper, never()).updateStatusAndMemoIfInStock(any(), any(), any(), any());
        verify(transactionMapper, never()).insertTransaction(any());
    }

    @Test
    void changeStatus_rejectsItemOwnedByAnotherUser() {
        StockDTO stock = stock(700L);
        stock.setItemId(otherUserItem.getId());
        when(stockMapper.findByExternalId(STOCK_EXTERNAL_ID)).thenReturn(Optional.of(stock));
        when(itemMapper.findById(otherUserItem.getId())).thenReturn(Optional.of(otherUserItem));

        assertThatThrownBy(() -> stockService.changeStatus(STOCK_EXTERNAL_ID, StockStatus.DAMAGED, null, USERNAME))
                .isInstanceOf(SecurityException.class);

        verify(stockMapper, never()).updateStatusAndMemoIfInStock(any(), any(), any(), any());
        verify(transactionMapper, never()).insertTransaction(any());
    }

    @Test
    void changeStatus_rejectsWhenNotInStock() {
        StockDTO stock = stock(700L);
        stock.setItemId(item.getId());
        stock.setMemo("기존 메모");
        when(stockMapper.findByExternalId(STOCK_EXTERNAL_ID)).thenReturn(Optional.of(stock));
        when(itemMapper.findById(item.getId())).thenReturn(Optional.of(item));
        when(stockMapper.updateStatusAndMemoIfInStock(eq(STOCK_EXTERNAL_ID), eq(user.getId()),
                eq(StockStatus.LOST), anyString())).thenReturn(0);

        assertThatThrownBy(() -> stockService.changeStatus(STOCK_EXTERNAL_ID, StockStatus.LOST, "분실 추정", USERNAME))
                .isInstanceOf(NoSuchElementException.class);

        verify(transactionMapper, never()).insertTransaction(any());
    }

    @Test
    void changeStatus_appendsMemoAndRecordsAdjustTransaction() {
        StockDTO stock = stock(700L);
        stock.setItemId(item.getId());
        stock.setMemo("기존 메모");
        when(stockMapper.findByExternalId(STOCK_EXTERNAL_ID)).thenReturn(Optional.of(stock));
        when(itemMapper.findById(item.getId())).thenReturn(Optional.of(item));
        when(stockMapper.updateStatusAndMemoIfInStock(eq(STOCK_EXTERNAL_ID), eq(user.getId()),
                eq(StockStatus.DAMAGED), anyString())).thenReturn(1);

        stockService.changeStatus(STOCK_EXTERNAL_ID, StockStatus.DAMAGED, "파손 확인", USERNAME);

        String today = java.time.LocalDate.now().toString();
        ArgumentCaptor<String> memoCaptor = ArgumentCaptor.forClass(String.class);
        verify(stockMapper).updateStatusAndMemoIfInStock(eq(STOCK_EXTERNAL_ID), eq(user.getId()),
                eq(StockStatus.DAMAGED), memoCaptor.capture());
        assertThat(memoCaptor.getValue()).isEqualTo("기존 메모\n[" + today + "] 파손 확인");

        ArgumentCaptor<StockTransactionDTO> txCaptor = ArgumentCaptor.forClass(StockTransactionDTO.class);
        verify(transactionMapper).insertTransaction(txCaptor.capture());
        assertThat(txCaptor.getValue().getTransactionType()).isEqualTo(TransactionType.ADJUST);
        assertThat(txCaptor.getValue().getStockId()).isEqualTo(700L);
        assertThat(txCaptor.getValue().getMemo()).isEqualTo("파손 확인");
    }

    @Test
    void changeStatus_dispatchedUsesOutTransactionAndKeepsMemoWhenReasonBlank() {
        StockDTO stock = stock(700L);
        stock.setItemId(item.getId());
        stock.setMemo("기존 메모");
        when(stockMapper.findByExternalId(STOCK_EXTERNAL_ID)).thenReturn(Optional.of(stock));
        when(itemMapper.findById(item.getId())).thenReturn(Optional.of(item));
        when(stockMapper.updateStatusAndMemoIfInStock(eq(STOCK_EXTERNAL_ID), eq(user.getId()),
                eq(StockStatus.DISPATCHED), anyString())).thenReturn(1);

        stockService.changeStatus(STOCK_EXTERNAL_ID, StockStatus.DISPATCHED, "   ", USERNAME);

        ArgumentCaptor<String> memoCaptor = ArgumentCaptor.forClass(String.class);
        verify(stockMapper).updateStatusAndMemoIfInStock(eq(STOCK_EXTERNAL_ID), eq(user.getId()),
                eq(StockStatus.DISPATCHED), memoCaptor.capture());
        assertThat(memoCaptor.getValue()).isEqualTo("기존 메모");   // 사유 공백 → 메모 변경 없음

        ArgumentCaptor<StockTransactionDTO> txCaptor = ArgumentCaptor.forClass(StockTransactionDTO.class);
        verify(transactionMapper).insertTransaction(txCaptor.capture());
        assertThat(txCaptor.getValue().getTransactionType()).isEqualTo(TransactionType.OUT);
        assertThat(txCaptor.getValue().getMemo()).isEqualTo(StockStatus.DISPATCHED.getLabel());  // "출고"
    }
```

참고: `changeStatus_rejectsInStockSelection`는 `getUser`가 먼저 호출되도록 구현해 setUp의
`userMapper.findByEmail` 스텁이 사용되게 한다(아래 Step 3의 순서가 그 이유다).

- [ ] **Step 2: 테스트 실패 확인 (컴파일 에러)**

Run: `./gradlew test --tests "com.seu.seustock.service.StockServiceTest"`
Expected: FAIL — `cannot find symbol: method changeStatus`.

- [ ] **Step 3: 서비스 메서드 + 헬퍼 구현**

`StockService.java` 상단 import에 추가(없으면):

```java
import java.time.LocalDate;
```

`setKeepStatus(...)` 메서드 바로 아래에 추가:

```java
    @Transactional
    public void changeStatus(UUID stockExternalId, StockStatus status, String memo, String username) {
        UserDTO user = getUser(username);
        if (status == null || status == StockStatus.IN_STOCK) {
            throw new IllegalArgumentException(getMsg("error.stock.invalidStatus"));
        }
        StockDTO stock = stockMapper.findByExternalId(stockExternalId)
                .orElseThrow(() -> new NoSuchElementException(getMsg("error.stock.notFound")));

        ItemDTO item = itemMapper.findById(stock.getItemId())
                .orElseThrow(() -> new NoSuchElementException(getMsg("error.item.notFound")));
        verifyItemOwner(item, user);

        String newMemo = appendMemo(stock.getMemo(), memo);
        int updated = stockMapper.updateStatusAndMemoIfInStock(stockExternalId, user.getId(), status, newMemo);
        if (updated != 1) {
            log.warn("stock status change rejected userId={} stockExternalId={} reason=not_in_stock",
                    user.getId(), stockExternalId);
            throw new NoSuchElementException(getMsg("error.stock.notFound"));
        }

        StockTransactionDTO tx = new StockTransactionDTO();
        tx.setStockId(stock.getId());
        tx.setTransactionType(status == StockStatus.DISPATCHED ? TransactionType.OUT : TransactionType.ADJUST);
        tx.setMemo((memo != null && !memo.isBlank()) ? memo.strip() : status.getLabel());
        transactionMapper.insertTransaction(tx);

        log.info("stock status changed userId={} stockExternalId={} status={}",
                user.getId(), stockExternalId, status);
    }

    private String appendMemo(String existing, String reason) {
        if (reason == null || reason.isBlank()) {
            return existing;
        }
        String line = "[" + LocalDate.now() + "] " + reason.strip();
        if (existing == null || existing.isBlank()) {
            return line;
        }
        return existing + "\n" + line;
    }
```

(`StockStatus`, `TransactionType`, `StockTransactionDTO`, `ItemDTO`, `UserDTO`, `NoSuchElementException`은 이미 import되어 있음.)

- [ ] **Step 4: 테스트 통과 확인**

Run: `./gradlew test --tests "com.seu.seustock.service.StockServiceTest"`
Expected: PASS (신규 5개 포함 전체 통과).

- [ ] **Step 5: 커밋**

```bash
git add src/main/java/com/seu/seustock/service/StockService.java \
        src/test/java/com/seu/seustock/service/StockServiceTest.java
git commit -m "Add StockService.changeStatus: per-unit status change with memo append and ledger entry

Co-Authored-By: Claude Opus 4.8 <noreply@anthropic.com>"
```

---

## Task 3: Controller `GET/PUT /stocks/{id}/status`

모달 GET, 변경 PUT 두 핸들러. 변경 성공 시 행 제거 + 모달 닫기 프래그먼트 반환 + 토스트.
(코드베이스에 StockController용 자동화 테스트는 없으므로 빌드 + Task 6 수동 검증으로 확인.)

**Files:**
- Modify: `src/main/java/com/seu/seustock/controller/StockController.java`

- [ ] **Step 1: import 추가**

`StockController.java`의 import 블록에 추가(없으면):

```java
import com.seu.seustock.model.enumeration.StockStatus;
import java.util.Arrays;
```

- [ ] **Step 2: 두 핸들러 추가**

기존 `toggleKeep(...)` 메서드 바로 아래에 추가:

```java
    @GetMapping("/stocks/{stockExternalId}/status")
    public String statusModal(@PathVariable UUID stockExternalId,
                              Principal principal, Model model) {
        String username = principal.getName();
        model.addAttribute("stock", stockService.findDetailByExternalId(stockExternalId, username));
        model.addAttribute("statuses", Arrays.stream(StockStatus.values())
                .filter(s -> s != StockStatus.IN_STOCK)
                .toList());
        return "stocks/fragments/status-modal :: modal";
    }

    @PutMapping("/stocks/{stockExternalId}/status")
    public String changeStatus(@PathVariable UUID stockExternalId,
                               @RequestParam StockStatus status,
                               @RequestParam(required = false) String memo,
                               Principal principal,
                               HttpServletResponse response) {
        String username = principal.getName();
        stockService.changeStatus(stockExternalId, status, memo, username);
        HtmxResponse.success(response, getMsg("toast.stock.statusChanged"));
        return "stocks/fragments/status-modal :: removed";
    }
```

- [ ] **Step 3: 컴파일 확인**

Run: `./gradlew compileJava`
Expected: BUILD SUCCESSFUL (템플릿은 Task 4에서 생성하므로 컴파일만 확인).

- [ ] **Step 4: 커밋**

```bash
git add src/main/java/com/seu/seustock/controller/StockController.java
git commit -m "Add stock status change endpoints (GET modal, PUT change)

Co-Authored-By: Claude Opus 4.8 <noreply@anthropic.com>"
```

---

## Task 4: Templates — status-modal + 상태 뱃지 버튼

**Files:**
- Create: `src/main/resources/templates/stocks/fragments/status-modal.html`
- Modify: `src/main/resources/templates/stocks/fragments/detail-row.html`

- [ ] **Step 1: status-modal.html 생성**

다음 내용으로 새 파일 작성(`memo-modal.html`의 구조·클래스를 그대로 따름):

```html
<!DOCTYPE html>
<html th:lang="${#locale.language}" xmlns:th="http://www.thymeleaf.org">
<body>

<!-- 상태 변경 모달 -->
<div th:fragment="modal"
     id="modal"
     class="fixed inset-0 z-50 flex items-center justify-center bg-black/40">
    <div class="bg-white rounded-xl shadow-xl w-full max-w-md mx-4 p-6 max-h-[90vh] overflow-y-auto flex flex-col">

        <h2 class="text-lg font-semibold text-slate-800 mb-1" th:text="#{view.stock.modal.status.title}">상태 변경</h2>
        <p class="text-xs text-slate-400 mb-4" th:text="${stock.itemName}">품목명</p>

        <form th:attr="hx-put=@{/stocks/{id}/status(id=${stock.externalId})},hx-target='#stock-' + ${stock.externalId}"
              hx-swap="outerHTML">

            <div class="space-y-1.5 mb-4">
                <label th:each="s : ${statuses}"
                       class="flex items-center gap-2 rounded-md border border-slate-200 px-3 py-2 text-sm text-slate-700 cursor-pointer hover:bg-slate-50 transition">
                    <input type="radio" name="status" th:value="${s.name()}" required
                           class="text-blue-600 focus:ring-blue-500">
                    <span th:text="#{${'enum.StockStatus.' + s.name()}}">상태</span>
                </label>
            </div>

            <div class="mb-6">
                <label class="mb-1 block text-xs font-medium text-slate-500"
                       th:text="#{view.stock.modal.status.memoLabel}">사유 메모 (선택)</label>
                <textarea name="memo" rows="3"
                          class="w-full resize-y rounded-md border border-slate-300 px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-blue-500 focus:border-blue-500 transition"
                          th:placeholder="#{view.stock.modal.status.memoPlaceholder}"></textarea>
            </div>

            <div class="flex gap-2 justify-end">
                <button type="button"
                        hx-get="/empty"
                        hx-target="#modal"
                        hx-swap="outerHTML"
                        class="rounded-md border border-slate-300 px-4 py-2 text-sm text-slate-600 hover:bg-slate-50 transition"
                        th:text="#{view.common.cancel}">취소</button>
                <button type="submit"
                        class="rounded-md bg-blue-600 px-4 py-2 text-sm font-semibold text-white hover:bg-blue-700 transition"
                        th:text="#{view.stock.modal.status.confirm}">변경</button>
            </div>
        </form>
    </div>
</div>

<!-- 변경 완료: 행 제거 + 모달 닫기 -->
<th:block th:fragment="removed"><div id="modal" hx-swap-oob="true"></div></th:block>

</body>
</html>
```

동작 설명: `removed` 프래그먼트의 본문은 OOB `#modal` 닫기뿐이다. HTMX가 OOB 요소를
먼저 추출하면 타깃(`#stock-{id}`)으로 swap될 본문은 비게 되어 `outerHTML`로 행이 제거된다.
이는 `detail-row.html`의 `view-with-modal-close`(행 교체 + OOB 닫기)의 대칭형이다.

- [ ] **Step 2: 상태 뱃지를 버튼으로 변경**

`detail-row.html` 41-43행의 상태 `<span>`을:

```html
        <span class="inline-flex rounded-full bg-blue-50 px-2.5 py-0.5 text-xs font-semibold text-blue-700"
              th:text="#{${'enum.StockStatus.' + stock.status.name()}}">재고중</span>
```

다음 버튼으로 교체(뒤따르는 `보관` 뱃지 `<span>`과 유통기한 `<p>`는 그대로 둠):

```html
        <button type="button"
                th:attr="hx-get=@{/stocks/{id}/status(id=${stock.externalId})}"
                hx-target="#modal"
                class="inline-flex rounded-full bg-blue-50 px-2.5 py-0.5 text-xs font-semibold text-blue-700 hover:bg-blue-100 cursor-pointer transition"
                th:text="#{${'enum.StockStatus.' + stock.status.name()}}">재고중</button>
```

- [ ] **Step 3: 빌드(템플릿 파싱 포함) 확인**

Run: `./gradlew compileJava test --tests "com.seu.seustock.controller.*"` 가 없으면 `./gradlew build -x test`
Expected: BUILD SUCCESSFUL (Thymeleaf는 런타임 파싱이므로 Task 6 수동 검증에서 최종 확인).

- [ ] **Step 4: 커밋**

```bash
git add src/main/resources/templates/stocks/fragments/status-modal.html \
        src/main/resources/templates/stocks/fragments/detail-row.html
git commit -m "Add stock status change modal and make status badge clickable

Co-Authored-By: Claude Opus 4.8 <noreply@anthropic.com>"
```

---

## Task 5: i18n — 메시지 키 6종 (5개 로케일)

각 파일에서 `view.stock.modal.*` 4종은 기존 `view.stock.modal.*` 키 근처에, `toast.stock.statusChanged`는
`toast.stock.unkept` 근처에, `error.stock.invalidStatus`는 `error.stock.*` 키 근처에 추가한다.
(.properties는 순서 무관이므로 해당 섹션 끝에 붙여도 무방.)

**Files:**
- Modify: `src/main/resources/messages.properties`
- Modify: `src/main/resources/messages_en.properties`
- Modify: `src/main/resources/messages_ja.properties`
- Modify: `src/main/resources/messages_zh_CN.properties`
- Modify: `src/main/resources/messages_mn.properties`

- [ ] **Step 1: `messages.properties` (ko)**

```properties
view.stock.modal.status.title=상태 변경
view.stock.modal.status.memoLabel=사유 메모 (선택)
view.stock.modal.status.memoPlaceholder=예: 창고 이전 중 파손 확인
view.stock.modal.status.confirm=변경
toast.stock.statusChanged=상태가 변경되었습니다.
error.stock.invalidStatus=변경할 수 없는 상태입니다.
```

- [ ] **Step 2: `messages_en.properties`**

```properties
view.stock.modal.status.title=Change Status
view.stock.modal.status.memoLabel=Reason (optional)
view.stock.modal.status.memoPlaceholder=e.g., Damaged during warehouse move
view.stock.modal.status.confirm=Change
toast.stock.statusChanged=Status has been changed.
error.stock.invalidStatus=Invalid status.
```

- [ ] **Step 3: `messages_ja.properties`**

```properties
view.stock.modal.status.title=状態変更
view.stock.modal.status.memoLabel=理由メモ（任意）
view.stock.modal.status.memoPlaceholder=例: 倉庫移動中に破損確認
view.stock.modal.status.confirm=変更
toast.stock.statusChanged=状態が変更されました。
error.stock.invalidStatus=変更できない状態です。
```

- [ ] **Step 4: `messages_zh_CN.properties`**

```properties
view.stock.modal.status.title=变更状态
view.stock.modal.status.memoLabel=原因备注（可选）
view.stock.modal.status.memoPlaceholder=例如：仓库搬迁中发现损坏
view.stock.modal.status.confirm=变更
toast.stock.statusChanged=状态已变更。
error.stock.invalidStatus=无效的状态。
```

- [ ] **Step 5: `messages_mn.properties`**

```properties
view.stock.modal.status.title=Төлөв өөрчлөх
view.stock.modal.status.memoLabel=Шалтгаан тэмдэглэл (заавал биш)
view.stock.modal.status.memoPlaceholder=Жишээ: Агуулах нүүлгэх үед эвдрэл илрүүлсэн
view.stock.modal.status.confirm=Өөрчлөх
toast.stock.statusChanged=Төлөв өөрчлөгдлөө.
error.stock.invalidStatus=Хүчингүй төлөв.
```

- [ ] **Step 6: 커밋**

```bash
git add src/main/resources/messages*.properties
git commit -m "Add i18n keys for stock status change modal (ko/en/ja/zh_CN/mn)

Co-Authored-By: Claude Opus 4.8 <noreply@anthropic.com>"
```

---

## Task 6: 전체 빌드 + 수동 E2E 검증

- [ ] **Step 1: 전체 테스트**

Run: `./gradlew test`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 2: 앱 실행 (Docker 필요)**

Run: `./gradlew bootRun` → http://localhost:8080 로그인.

- [ ] **Step 3: 수동 시나리오 확인**

1. `/stocks`에서 한 행의 `재고중` 뱃지 클릭 → 상태 변경 모달이 열린다(출고/분실/파손/폐기 라디오 + 메모란).
2. `파손` 선택 + 메모 "파손 확인" 입력 후 `변경` → 행이 목록에서 사라지고 "상태가 변경되었습니다." 토스트, 모달 닫힘.
3. 패널 화면(해당 품목 위치) 또는 DB에서 해당 unit이 `DAMAGED`이고 메모 끝에 `\n[오늘날짜] 파손 확인`이 붙은 것, `stock_transactions`에 ADJUST 1행이 남은 것을 확인.
4. 다른 unit을 `출고`로 변경 → 원장에 OUT으로 기록되는지 확인.
5. 모달에서 `취소` → 변경 없이 모달만 닫힘.

- [ ] **Step 4: (선택) 최종 커밋**

이전 태스크에서 모두 커밋됐으면 추가 커밋 불필요.

---

## Self-Review (작성자 체크 완료)

- **Spec coverage:** 상태 뱃지 클릭(T4) · 4종 상태 선택(T3·T4) · 메모 append(T2) · 원장 기록·타입 분기(T2) · 행 제거+모달 닫기(T3·T4) · IN_STOCK 전용 유지(목록 미변경) · i18n(T5) · 테스트(T1·T2) 모두 태스크에 매핑됨.
- **Placeholder scan:** 모든 코드/명령 실값 포함, TBD 없음.
- **Type consistency:** `updateStatusAndMemoIfInStock(UUID, Long, StockStatus, String)` 시그니처가 인터페이스·서비스 호출·테스트에서 일치. `changeStatus(UUID, StockStatus, String, String)` 컨트롤러·서비스·테스트 일치. 원장 타입 분기(OUT/ADJUST)와 메모 규칙이 spec과 일치.
