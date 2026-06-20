# 품목별 시리얼·로트 정책(Serial/Lot Policy) 설계

작성일: 2026-05-31

## 1. 배경 / 목표

품목(item)마다 **시리얼 번호**와 **로트 번호**의 채번 방식을 정책으로 설정해두고, 재고를
입고할 때 그 정책에 따라 번호가 자동/수동으로 채워지게 한다. 핵심은 **로트와 시리얼의
종속 관계**를 데이터 모델로 표현하는 것이다.

```
입고 1회 (예: 30개)
   └─ item_lots  (로트 1개)                 ← 로트번호 정책으로 발번 (또는 수동 입력)
         ├─ stocks #1  serial=SEU-0001  lot_id=★
         ├─ stocks #2  serial=SEU-0002  lot_id=★
         ├─ ...
         └─ stocks #30 serial=SEU-0030  lot_id=★   ← 모두 같은 로트(★)에 종속
```

- **로트 → 시리얼**: `SELECT * FROM stocks WHERE lot_id = ★`
- **시리얼 → 로트**: 그 stock의 `lot_id`로 `item_lots` 조회

## 2. 확정된 결정 사항 (브레인스토밍 Q&A)

| 항목 | 결정 |
|------|------|
| 번호 출처 | **품목별 자동/수동 선택**. 시리얼·로트 각각 `NONE / AUTO / MANUAL` 모드를 가진다. |
| 로트 묶음 단위 | 입고 1회 = 로트 1개. **같은 로트번호면 기존 로트에 합침**(재사용). `uq_item_lots(item_id, lot_number)`. |
| 유통기한 | **로트 단위**로 관리. `items.expiration_period_days`가 있으면 입고일+기간으로 자동 계산, 없으면 수동(또는 미설정). 같은 로트의 모든 단위가 공유. |

## 3. 데이터 모델 (V6 마이그레이션)

V1(기본) + V2(`item_serial_policies`, `item_lot_policies`, `item_lots`, `stocks.lot_id`,
`items.expiration_period_days`)는 이미 존재하지만 **코드에 전혀 연결돼 있지 않다**.

**결정: AUTO 채번 설정을 `items` 컬럼에 통합한다.** V2의 별도 정책 테이블
(`item_serial_policies`, `item_lot_policies`)은 비어 있고 한 번도 연결된 적이 없으므로
V6에서 **drop**한다. 핵심 관계 테이블인 `item_lots`와 `stocks.lot_id`는 유지한다.

### items (모드 + AUTO 채번 설정 컬럼 추가)
시리얼:
- `serial_mode VARCHAR(10) NOT NULL DEFAULT 'NONE'` — `NONE | AUTO | MANUAL`
- `serial_prefix VARCHAR(100)` — AUTO 접두사 (예: "SEU-")
- `serial_padding_length INT NOT NULL DEFAULT 0` — 0이면 패딩 없음
- `serial_increment_unit INT NOT NULL DEFAULT 1`
- `serial_next_sequence BIGINT NOT NULL DEFAULT 0` — 가변 카운터(마지막 채번값)

로트:
- `lot_mode VARCHAR(10) NOT NULL DEFAULT 'NONE'` — `NONE | AUTO | MANUAL`
- `lot_vendor_code VARCHAR(100)` — AUTO 접두(거래처코드 등)
- `lot_date_format VARCHAR(32) DEFAULT 'yyyyMMdd'`
- `lot_include_sequence BOOLEAN NOT NULL DEFAULT TRUE`
- `lot_sequence_key VARCHAR(32)` — 마지막 발번 날짜키(시퀀스 리셋 판단)
- `lot_next_sequence INT NOT NULL DEFAULT 0` — 가변 카운터

기타:
- `expiration_period_days INT` — V2에서 이미 추가됨(그대로 사용).
- CHECK 제약: `serial_mode`/`lot_mode` 값 제한, `serial_padding_length >= 0`,
  `serial_increment_unit > 0`, `serial_next_sequence >= 0`, `lot_next_sequence >= 0`.

> 참고: 접두/접미 중 **suffix는 범위 밖**(9절)이라 컬럼에 두지 않는다. 필요해지면 추가.

### item_lots (로트 1행) — 유지
- V6에서 `lot_policy_id` 컬럼(및 FK)을 **drop**한다(참조 대상 테이블 제거).
- 사용 컬럼: `id`, `external_id`, `item_id`, `lot_number`, `expiration_date`, `created_at`.
- `uq_item_lots(item_id, lot_number)` 이미 존재 → 같은 번호 재사용의 근거.

### stocks
- `lot_id`(이미 존재): 시리얼 단위가 속한 로트.
- `serial_number`(이미 존재): AUTO/MANUAL 모두 여기에 저장.
- `expiration_date`(이미 존재): 로트의 유통기한을 비정규화 복사(기존 검색/상세 쿼리가
  `s.expiration_date`를 읽으므로 변경 최소화).
- `lot_number`(이미 존재, 자유 텍스트): **레거시**. 신규 코드는 `lot_id`로 로트를 참조하고,
  표시용 로트번호는 `item_lots` 조인으로 가져온다. 기존 행 호환을 위해 컬럼은 유지.
- V6에서 `uq_stocks_item_serial UNIQUE(item_id, serial_number)` 추가
  (Postgres/H2 모두 NULL 다중 허용 → 시리얼 미사용 단위는 제약 없음).

### 드롭되는 V2 잔재
- `item_serial_policies`, `item_lot_policies` 테이블 drop.
- `item_lots.lot_policy_id` 컬럼/FK drop.

## 4. 채번 규칙

### 시리얼 (AUTO)
입고 N개에 대해, `start = serial_next_sequence`:
```
for i in 1..N:
    seq    = start + i * serial_increment_unit
    serial = serial_prefix + zeroPad(seq, serial_padding_length)
저장 후 serial_next_sequence = start + N * serial_increment_unit
```
예) serial_prefix="SEU-", padding=4, increment=1, start=0, N=30 → SEU-0001 … SEU-0030.

### 시리얼 (MANUAL)
사용자가 단위 수만큼 시리얼을 직접 입력. 입력 폼은 **여러 줄 텍스트(한 줄 = 1개)** 로 받고,
줄 수가 곧 입고 수량이 된다. 배치 내 중복 및 기존 시리얼과의 중복을 검증한다.

### 로트 (AUTO) — 입고 1회당 1개 발번
```
key = format(today, lot_date_format)            # 기본 "yyyyMMdd"
seq = (key == lot_sequence_key) ? lot_next_sequence + 1 : 1
lotNumber = [lot_vendor_code] + key + (lot_include_sequence ? "-" + zeroPad(seq,3) : "")
저장: lot_next_sequence = seq, lot_sequence_key = key
```
예) lot_vendor_code 없음, key=20260531, seq=1 → "20260531-001".

### 로트 (MANUAL)
폼의 로트번호를 사용. 같은 품목에 이미 그 번호의 로트가 있으면 그 `lot_id` 재사용,
없으면 새 `item_lots` 행 생성.

### 유통기한
로트 생성 시 `item.expiration_period_days`가 있으면 `today + period`, 없으면 폼 입력값(또는 null).
같은 로트의 모든 stock에 동일하게 복사.

## 5. 입고 플로우 통합 (StockService)

`create(StockForm)`(표준 등록)에 적용:
1. 품목/위치 검증(기존 `getVerifiedItem`, `resolveVerifiedLocation`).
2. 로트 해결: `lot_mode`에 따라 `resolveLot(item, form, count)` → `lotId` + `expirationDate`.
   - AUTO: 새 로트 발번. MANUAL: 번호 재사용-또는-생성. NONE: lotId=null.
3. 시리얼 해결: `serial_mode`에 따라 단위별 serial 리스트 생성.
   - AUTO: N개 채번. MANUAL: 입력 줄 파싱(개수=줄 수). NONE: null.
4. 수량 확정: serial=MANUAL이면 `count`는 입력된 시리얼 줄 수로 덮어쓴다(폼의 count는 무시).
   이때도 기존 상한(1~50)을 검증한다. serial=AUTO/NONE이면 폼 `count`를 그대로 사용.
5. `stocks` 배치 insert(각 unit에 serial, lot_id, expiration_date, price) + `stock_transactions`(IN) 기록.

`createWithNewItem`(빠른 등록), `addUnits`(입고 모달)도 동일 헬퍼 사용. 빠른 등록은 품목을
새로 만들므로 정책은 기본 NONE → 기존 동작 유지(이후 품목 수정에서 정책 설정).

## 6. 정책 설정 UI

품목 생성/수정 모달(`items/fragments/modal.html`, `card.html`)에 접이식 섹션 추가:
- 시리얼: 모드(없음/자동/수동), 자동일 때 접두사·자릿수·시작번호.
- 로트: 모드(없음/자동/수동), 자동일 때 거래처코드·일련번호 포함 여부.
- 유통기한 기간(일) — 선택.

`ItemForm`에 해당 필드 추가, `ItemService.create/update`에서 정책 행 upsert.

## 7. 조회(양방향) UI

- **시리얼 → 로트**: 재고 상세(`detail-row`)에 로트번호 표시(`item_lots` 조인). 로트번호 클릭 시
  로트 상세 모달.
- **로트 → 시리얼**: `GET /lots/{externalId}` 모달 — 로트 정보 + 종속 시리얼 단위 목록.
  `ItemLotMapper.findUnitsByLotExternalId`로 조회(소유권 검증 포함).

## 8. 테스트

- **Mapper 테스트**(`@MybatisTest` + H2): ItemLotMapper(insert, findByItemAndNumber 재사용,
  findUnitsByLot), ItemMapper의 정책/채번 컬럼 insert·update·시퀀스 갱신, `uq_stocks_item_serial` 동작.
- **Service 테스트**(Mockito): 시리얼 AUTO 채번(패딩/시퀀스 누적), 로트 AUTO 발번(날짜 시퀀스
  리셋), 로트 MANUAL 재사용, 유통기한 자동계산, 소유권/검증. 채번 규칙은 순수 로직으로
  분리(`SerialNumberGenerator`, `LotNumberGenerator`)해 단위 테스트 용이하게.
- `schema-test.sql`에 V6 변경 반영(items 모드 컬럼, unique 제약 등 H2 호환 DDL).

## 9. 범위 밖 (YAGNI)
- 로트/시리얼 라벨 인쇄, 바코드/QR 자동 부착.
- 시리얼 suffix 의 복잡한 패턴, 로트 date_format 프리셋 다수(기본 yyyyMMdd만 노출).
- 출고 시 특정 시리얼 지정 선택(현재 FIFO 유지).

## 10. i18n
`messages*.properties` 5종(ko/en/ja/zh_CN/mn)에 정책/로트/시리얼/검증 키 추가.
