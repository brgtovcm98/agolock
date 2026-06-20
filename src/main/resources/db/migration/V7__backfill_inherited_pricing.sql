-- 품목의 현재 가격과 동일하게 스냅샷되어 있는 단위(Stock)의 가격을 null로 업데이트하여
-- 상속 모델(Inherited Pricing)로 전환합니다.
-- 이 마이그레이션은 기존 데이터에 대해 변경된 가격 정책을 일괄 적용하기 위함입니다.

UPDATE stocks
SET price = NULL
FROM items i
WHERE stocks.item_id = i.id
  AND stocks.price IS NOT NULL
  AND i.price IS NOT NULL
  AND stocks.price = i.price;
