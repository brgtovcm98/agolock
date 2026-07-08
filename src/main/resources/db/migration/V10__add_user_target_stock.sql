-- 사용자별 총 보유 수량 목표(소진/처리형). NULL = 목표 미설정.
-- 대시보드는 현재 총재고(IN_STOCK)와 이 목표를 비교해 "처리해야 할 물건"(초과분)을 산출한다.
ALTER TABLE users ADD COLUMN target_total_stock INT;
