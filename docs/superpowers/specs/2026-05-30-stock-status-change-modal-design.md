# 재고 상태 변경 모달 설계

작성일: 2026-05-30

## 배경 (Context)

`/stocks` 목록 페이지(`stocks/list.html`)는 각 재고 unit을 행으로 보여주며, 상태 컬럼에
현재 상태 뱃지(`재고중`)와 선택적 `보관` 뱃지를 표시한다. 현재 이 목록은
`StockMapper.searchDetails`/`countSearchDetails`가 `s.status = 'IN_STOCK'`으로 필터하므로
**재고중(IN_STOCK) 상태의 unit만** 노출된다.

사용자는 상태 뱃지를 클릭해 모달을 띄우고, 상태를 선택해 변경할 수 있기를 원한다.
또한 변경 시 선택적으로 사유 메모를 입력하면 해당 unit의 기존 메모 아래에 날짜와 함께
덧붙여 기록되기를 원한다.

이 기능은 개별 unit을 목록에서 바로 `출고/분실/파손/폐기` 처리하는 빠른 경로를 제공한다.

## 목표 (Goals)

- 상태 컬럼의 `재고중` 뱃지를 클릭하면 상태 변경 모달이 열린다.
- 모달에서 `출고(DISPATCHED) / 분실(LOST) / 파손(DAMAGED) / 폐기(DISPOSED)` 중 하나를 선택해 변경한다.
- 선택적 메모를 입력하면 unit의 기존 `memo` 아래에 `[yyyy-MM-dd] 내용` 형식으로 덧붙인다.
- 상태 변경 시 `stock_transactions` 원장에 1행을 기록한다(append-only 규칙 준수).
- 변경 후 해당 행은 목록에서 제거되고 모달이 닫히며 토스트가 표시된다.

## 비목표 (Non-goals)

- 목록을 IN_STOCK 외 상태까지 보여주도록 바꾸지 않는다(현행 IN_STOCK 전용 유지).
- `재고중`으로 되돌리는 전환은 제공하지 않는다(목록의 모든 행은 이미 IN_STOCK).
- 상태 필터 UI 추가 없음.
- 패널(`panel`) 화면의 기존 배치 입·출고 흐름은 변경하지 않는다.

## 아키텍처 / 컴포넌트

기존 `memo-modal` + `keep` 토글 패턴을 그대로 따른다.

| 레이어 | 변경/추가 |
|---|---|
| Template | `stocks/fragments/detail-row.html`의 상태 뱃지를 `<button>`으로 변경(모달 트리거) |
| Template | 신규 `stocks/fragments/status-modal.html` — `modal`(모달 UI), `removed`(행 제거 + 모달 닫기 응답) 프래그먼트 |
| Controller | `StockController` — `GET /stocks/{id}/status`, `PUT /stocks/{id}/status` 추가 |
| Service | `StockService.changeStatus(externalId, status, memo, username)` 추가 |
| Mapper | `StockMapper.updateStatusAndMemoIfInStock(externalId, userId, status, memo)` + XML 추가 |
| i18n | 신규 메시지 키를 5개 `messages*.properties`(ko/en/ja/zh_CN/mn)에 추가 |

## 상세 동작

### UX / HTMX 흐름

1. 상태 컬럼 `재고중` 뱃지(현재 `detail-row.html:42-43`의 `<span>`)를 `<button>`으로 변경:
   - `hx-get=@{/stocks/{id}/status(id=${stock.externalId})}`, `hx-target="#modal"`.
   - hover 어포던스(커서/배경) 추가. `보관` 뱃지는 그대로 유지(별도 keep 토글 버튼 존재).
2. `GET /stocks/{id}/status` → `status-modal :: modal`을 `#modal`에 렌더.
   - 변경 가능한 상태 라디오(IN_STOCK 제외 4종) + 선택 메모 textarea + `변경`/`취소` 버튼.
   - 라벨은 `enum.StockStatus.*` 키 재사용. 취소는 `hx-get="/empty"`로 `#modal` 비움(기존 패턴).
3. `변경` 제출 → `PUT /stocks/{id}/status`, 파라미터 `status`(enum 이름), `memo`(선택).
   - 폼: `hx-target='#stock-' + ${stock.externalId}`, `hx-swap="outerHTML"`.
4. 성공 응답 = `status-modal :: removed`:
   - 본문(타깃 행으로 swap될 내용)은 비어 있음 → 행 outerHTML 제거.
   - OOB `<div id="modal" hx-swap-oob="true"></div>` → 모달 닫힘.
   - `HtmxResponse.success(response, toast.stock.statusChanged)` 토스트.

### 서비스 로직 (`StockService.changeStatus`)

서비스 소유권 패턴(CLAUDE.md)을 그대로 따른다.

1. `getUser(username)`.
2. `stockMapper.findByExternalId(externalId)` → `NoSuchElementException` if empty.
   (현재 unit의 내부 id, 기존 memo, 현재 status 확보)
3. `itemMapper.findById(stock.getItemId())` → `verifyItemOwner(item, user)`.
4. 입력 status 검증: `IN_STOCK`이거나 enum이 아니면 `IllegalArgumentException`.
5. 새 메모 계산: `appendMemo(stock.getMemo(), memo, today)` (아래 규칙).
6. `updated = stockMapper.updateStatusAndMemoIfInStock(externalId, user.getId(), status, newMemo)`.
   - `updated != 1` 이면(이미 IN_STOCK 아님/타 사용자) `NoSuchElementException`.
7. `stock_transactions` 기록:
   - `transactionType`: `DISPATCHED → OUT`, 그 외 → `ADJUST`.
   - `memo`: 입력 사유가 있으면 사유, 없으면 상태 라벨(예: "파손").
   - `stockId`: 2단계에서 얻은 내부 id.
8. 구조화 로그 남김(`userId`, `stockExternalId`, `status` — 민감정보 제외).
9. 반환 타입은 `void`(또는 변경된 status). 컨트롤러는 `removed` 프래그먼트 반환.

### 메모 append 규칙 (`appendMemo`)

- 입력 사유가 공백/`null`이면 메모 변경 없음(기존 memo 그대로 전달).
- 사유가 있으면 `[yyyy-MM-dd] 사유` 한 줄을 만든다.
  - 기존 memo가 비어 있으면 그 줄만 저장.
  - 기존 memo가 있으면 `기존\n[yyyy-MM-dd] 사유`.
- 날짜는 `LocalDate.now()` 기준 `yyyy-MM-dd`(기존 템플릿 포맷과 동일).
- 상태 라벨은 메모에 포함하지 않는다(사용자 확정).

### Mapper SQL

`updateIsKept`/`updateDetails`와 동일한 소유권·IN_STOCK 가드.

```xml
<update id="updateStatusAndMemoIfInStock">
    UPDATE stocks
    SET status = #{status},
        memo   = #{memo}
    WHERE external_id = #{externalId}
      AND status = 'IN_STOCK'
      AND item_id IN (
          SELECT id FROM items WHERE user_id = #{userId}
      )
</update>
```

인터페이스:
```java
int updateStatusAndMemoIfInStock(@Param("externalId") UUID externalId,
                                 @Param("userId") Long userId,
                                 @Param("status") StockStatus status,
                                 @Param("memo") String memo);
```

## 에러 처리

`GlobalExceptionHandler` 규약 재사용:
- 대상 없음 / 동시성으로 이미 IN_STOCK 아님 / 타 사용자 → `NoSuchElementException` (404).
- `IN_STOCK` 선택 또는 알 수 없는 status → `IllegalArgumentException` (400).
- 소유권 불일치(`verifyItemOwner`) → `SecurityException` (403).

## 메시지 키 (5개 로케일 모두)

- `view.stock.modal.status.title` — 모달 제목(예: "상태 변경").
- `view.stock.modal.status.memoLabel` — 메모 입력 라벨(또는 기존 `view.stock.form.memo.*` 재사용).
- `view.stock.modal.status.confirm` — 변경 버튼(예: "변경").
- `toast.stock.statusChanged` — 토스트(예: "상태가 변경되었습니다.").
- 상태 라벨은 기존 `enum.StockStatus.*` 재사용.

## 테스트 계획

- **StockMapperTest** (`@MybatisTest`, H2):
  - `updateStatusAndMemoIfInStock`: IN_STOCK일 때 status·memo 갱신, 반환 1.
  - 이미 DISPATCHED인 unit → 갱신 안 됨, 반환 0.
  - 타 사용자 소유 unit → 반환 0(소유권 가드).
- **StockServiceTest** (Mockito):
  - 소유권 불일치 → `SecurityException`, 갱신/원장 호출 없음.
  - IN_STOCK 가드 실패(`updated=0`) → `NoSuchElementException`.
  - 메모 append: 기존 memo 있음/없음 두 경우 `[yyyy-MM-dd] 사유` 생성, 사유 공백이면 변경 없음.
  - 원장 타입 분기: DISPATCHED → OUT, 그 외 → ADJUST. 원장 memo = 사유 또는 상태 라벨.

## 검증 방법

```bash
# 단위/매퍼 테스트(H2, Docker 불필요)
./gradlew test --tests "com.seu.seustock.mapper.StockMapperTest"
./gradlew test --tests "com.seu.seustock.service.StockServiceTest"
./gradlew test
```

수동(E2E, `./gradlew bootRun` + Docker):
1. `/stocks`에서 한 행의 `재고중` 뱃지 클릭 → 상태 변경 모달이 열린다.
2. `파손` 선택 + 메모 입력 후 `변경` → 행이 목록에서 사라지고 토스트 표시, 모달 닫힘.
3. 같은 품목/위치의 패널 또는 원장에서 해당 unit이 `파손` 상태이고 메모에
   `[2026-05-30] 입력내용`이 덧붙은 것, 원장에 ADJUST 1행이 남은 것을 확인.
4. `출고` 선택으로 변경한 unit은 원장에 OUT으로 기록되는지 확인.
