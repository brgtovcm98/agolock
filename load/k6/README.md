# k6 부하 시나리오

staging 또는 로컬 전용입니다. 운영 계정을 사용하지 마세요.

```bash
k6 run \
  -e BASE_URL=http://localhost:8080 \
  -e TEST_EMAIL=load-test@example.com \
  -e TEST_PASSWORD=load-test-password \
  load/k6/inventory-list.js

k6 run \
  -e BASE_URL=http://localhost:8080 \
  -e TEST_EMAIL=load-test@example.com \
  -e TEST_PASSWORD=load-test-password \
  -e SEARCH_Q=box \
  load/k6/search.js
```

- `inventory-list.js`: 목록 p95 500ms, 실패율 1% 미만
- `search.js`: 검색 p95 800ms, 실패율 1% 미만
- 결과는 `k6 run --summary-export=build/load-*.json`으로 남깁니다.
- 부하 테스트 전에 `scripts/explain_queries.sql`로 실행 계획을 확인합니다.
