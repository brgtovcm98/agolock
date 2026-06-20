-- 품목/재고에 단가(price, 원 단위 정수) 컬럼을 추가한다.
-- items.price: 품목 카탈로그의 기준 단가(선택).
-- stocks.price: 재고 1단위의 단가. 생성 시 품목 단가를 스냅샷으로 복사하며,
--               이후 개별 재고 수정에서 변경할 수 있다(품목 단가 변경은 소급 적용되지 않음).
-- 한국 원화 기준이라 소수점이 없으므로 NUMERIC(12,0)을 사용한다.

ALTER TABLE items  ADD COLUMN price NUMERIC(12,0);
ALTER TABLE stocks ADD COLUMN price NUMERIC(12,0);

ALTER TABLE items  ADD CONSTRAINT chk_items_price  CHECK (price IS NULL OR price >= 0);
ALTER TABLE stocks ADD CONSTRAINT chk_stocks_price CHECK (price IS NULL OR price >= 0);
