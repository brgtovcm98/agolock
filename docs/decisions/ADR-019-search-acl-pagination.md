# ADR-019: 검색 ACL과 Item 그룹 페이지네이션

- 상태: 승인됨
- 날짜: 2026-10-21

## 맥락

M6 검색은 Item 10,000·Inventory 50,000 규모에서 공유 ACL을 적용하고, Inventory 결과를 Item 그룹으로 묶어 커서 페이지네이션해야 합니다. 목표 p95는 300ms입니다.

현재 ACL은 [AccessService](../../backend/src/main/kotlin/com/agolock/hierarchy/service/AccessService.kt)가 target 단위로 판정하고, [ShareGrantLookup](../../backend/src/main/kotlin/com/agolock/sharing/service/ShareGrantLookup.kt)이 Room/Shelf Grant를 조회합니다. 검색 결과 행마다 이 경로를 호출할 수 없고, M6 계획이 전제한 `ShareAclMapper.xml`과 `AccessService.accessibleRoomIds`·`accessibleShelfIds`도 아직 없습니다. ACL 판정을 검색 SQL에 복제하면 공유 철회·상속·교차 Space 규칙이 이중화될 위험이 있습니다.

그룹 페이지네이션은 그룹당 Inventory를 5개만 보여주면서 `inventoryCount`·`hasMoreInventories`를 제공해야 하고, 그룹별 추가 조회(N+1)와 OFFSET을 피해야 합니다. 성능이 부족할 때 읽기 모델을 바로 도입하면 갱신·재색인·outbox 복잡도가 먼저 늘어납니다.

## 결정

1. 검색 ACL의 의미는 `AccessService`가 계속 소유합니다. M6.0에서는 `AccessService`가 접근 가능한 Room/Shelf ID와 소유 Space를 계산하고, 검색 후보 CTE에 그 값을 주입합니다. 결과를 가져온 뒤 애플리케이션에서 필터링하지 않습니다.
2. `SearchAclParityTest`로 검색 결과의 가시성이 `AccessService.effectivePermission`과 일치하는지 소유자·Room/Shelf Grant·철회·교차 Space·이동 시나리오에서 검증합니다. 이 테스트는 SQL 최적화 전후 모두 통과해야 합니다.
3. p95 gate를 실패하면 `SearchAclMapper.xml`의 SQL EXISTS 조각으로 최적화할 수 있습니다. 이때도 ACL 의미는 `AccessService`와 parity test로 고정하며, Grant 판정 결과를 캐시하지 않습니다.
4. `inventory_search` 읽기 모델은 p95 gate를 두 번 연속 실패할 때만 별도 ADR로 검토합니다. 그 전에는 직접 조회와 인덱스 튜닝을 우선합니다.
5. 그룹 페이지네이션은 Item 그룹, `limit + 1` 커서, 그룹당 `LEFT JOIN LATERAL` 6개(5개 반환)로 구현합니다. 그룹별 추가 조회와 OFFSET은 사용하지 않습니다.
6. `POST /items/{itemId}/view`를 M6.0 계약에 포함하고, 30분 디바운스·`204`/`404`로 고정합니다.
7. `DISPOSED` Inventory는 기본 검색·그룹 집계에서 제외하고, 명시한 경우에만 포함합니다. 공유 결과는 `shared`와 `ownerName`을 함께 반환합니다.

## 결과

- M6.0에서 ACL을 먼저 검증하므로 검색 UI를 만들기 전에 권한 누락·과다 노출을 발견할 수 있습니다.
- 접근 가능한 Room/Shelf ID를 배열로 전달하므로 초기 구현과 테스트가 단순합니다. 공유 범위가 매우 큰 사용자에서는 배열 크기와 쿼리 계획을 측정해야 합니다.
- p95가 목표를 넘으면 SQL EXISTS 최적화 여지가 남아 있고, 읽기 모델은 최후 수단으로 유지됩니다.
- 검색 결과는 이미지 URL이 아니라 `primaryImageId`를 반환하고, 클라이언트는 M5의 배치 URL API를 사용합니다.
- 이미지 상태·권한 판정·응답 계약의 정합성은 검색 변경에 맞춰 확인합니다. M5.5·M6.0의 초기 검토 순서는 현재 기능 작업의 착수 조건으로 재적용하지 않습니다.

## 대안

- 처음부터 SQL EXISTS/include: 성능에는 유리할 수 있지만 현재 `ShareAclMapper`가 없고 ACL 의미가 SQL로 복제될 위험이 있어 M6.0 이후 최적화로 미룹니다.
- 애플리케이션에서 결과를 가져온 뒤 ACL 필터링: 구현은 쉽지만 후보 overfetch, 대량 데이터 누락, p95 악화 때문에 채택하지 않습니다.
- `inventory_search` 읽기 모델을 처음부터 도입: 갱신 일관성·재색인·outbox 복잡도가 먼저 커지므로 두 번의 p95 실패 전에는 채택하지 않습니다.
- OFFSET 페이지네이션: 깊은 페이지에서 성능이 나빠지고 그룹 커서의 안정성이 떨어지므로 채택하지 않습니다.

## 링크

- [검색과 추천](../search.md)
- [공유와 권한](../sharing.md)
- [ADR-003 누적 권한 모델](ADR-003-permission-model.md)
- [ADR-004 MyBatis와 Flyway](ADR-004-mybatis.md)
