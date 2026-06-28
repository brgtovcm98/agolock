# Agolock (SeuStock) Code Review Report

> **리뷰 일자**: 2026-06-28
> **리뷰 범위**: 전체 코드베이스 (Controller, Service, Mapper, Configuration, DB Migration, Test)
> **기준**: Spring Boot 4.0.6, Java 25, MyBatis 4.0.1, HTMX + Thymeleaf

---

## 총평 (Executive Summary)

프로젝트는 전반적으로 **일관된 아키텍처와 높은 코드 품질**을 보여줍니다. 계층 분리(Controller → Service → Mapper), 외부 노출용 UUID 사용, 소유권 검증 패턴, i18n 메시지 처리, HTMX Partial Response 처리 등 모던 웹 애플리케이션의 모범 사례가 잘 적용되어 있습니다.

다만 아래 영역에서 개선이 필요합니다:

| 영역 | 심각도 | 핵심 이슈 |
|------|--------|-----------|
| **보안** | 🔴 HIGH | 하드코딩된 시드 계정, i18n 누락된 하드코딩 메시지 |
| **코드 중복** | 🟡 MEDIUM | `getUser`, `getMsg`, `blankToNull`이 5개 이상의 서비스에 중복 |
| **동시성** | 🟡 MEDIUM | 출고/이동의 SELECT → UPDATE 사이 갭으로 인한 Race Condition 가능성 |
| **테스트 커버리지** | 🟡 MEDIUM | Service 계층 단위 테스트 전무, Mapper 테스트 부족 |
| **운영 관측성** | 🟢 LOW | Health Check, Metrics, Rate Limiting 부재 |
| **기술 부채** | 🟢 LOW | JDK 25 preview, Spring AI M6 버전 사용 |

---

## 1. 보안 (Security)

### 1.1 🔴 `DataInitializer` 시드 계정 하드코딩

**파일**: `configuration/DataInitializer.java:56-58`

```java
private static final String SEED_EMAIL = "test1234@test.com";
private static final String SEED_NICKNAME = "test1234";
private static final String SEED_PASSWORD = "test1234";
```

**문제**: 시드 계정 정보가 소스코드에 하드코딩되어 있습니다. `seustock.datainit.enabled=true`로 실행 시 누구나 이 계정으로 로그인 가능합니다.

**개선안**:
```java
// application.properties
seustock.datainit.seed-email=${SEED_EMAIL:admin@agolock.local}
seustock.datainit.seed-password=${SEED_PASSWORD:}
```
환경변수로 주입하고, `SEED_PASSWORD`가 설정되지 않으면 DataInitializer를 아예 실행하지 않도록 합니다.

### 1.2 🔴 컨트롤러 내 하드코딩된 한글 응답 메시지 (i18n 누락)

**파일**: `controller/StockController.java` (복수 위치)

| 라인 | 하드코딩 문자열 |
|------|---------------|
| 441 | `"재고가 삭제되었습니다."` |
| 451 | `"재고가 삭제되었습니다."` |
| 525 | `"입고되었습니다."` |
| 572 | `"출고되었습니다."` |
| 614 | `"재고가 이동되었습니다."` |

**문제**: 다른 부분은 `getMsg("toast.stock.created")` 처럼 i18n 키를 사용하는데, 위 5곳은 하드코딩되어 있습니다. `messages_*.properties` 번역 대상에서 누락됩니다.

**개선안**:
`messages.properties`에 키를 추가하고 모든 위치를 `getMsg("toast.stock.deleted")` 등으로 교체합니다.

### 1.3 🟡 이메일 중복 확인 엔드포인트의 계정 열거(Anti-Enumeration) 노출

**파일**: `controller/UserController.java:57-63`

```java
@GetMapping("/register/check-email")
public String checkEmail(@RequestParam(defaultValue = "") String email, Model model) {
    model.addAttribute("taken", !email.isBlank() && userService.existsByEmail(email));
    ...
}
```

**문제**: `/register/check-email?email=xxx` 를 호출하면 해당 이메일이 시스템에 등록되어 있는지 알 수 있습니다. 악의적인 사용자가 유효한 이메일 목록을 수집할 수 있습니다(계정 열거 공격).

**개선안**: Rate limiting을 적용하거나, 항상 `taken=false`를 반환하고 실제 판단은 폼 제출 시 검증만 수행하는 방식으로 변경합니다. (비밀번호 재설정은 이미 이 방식을 사용 중입니다)

### 1.4 🟢 비밀번호 재설정 토큰이 URL Query Parameter로 전송

**파일**: `service/PasswordResetService.java:50`

```java
mailSender.send(email, baseUrl + "/password/reset?token=" + token);
```

**문제**: 토큰이 URL 쿼리 파라미터로 전송되어 브라우저 히스토리, 서버 액세스 로그, Referer 헤더 등에 노출될 수 있습니다.

**개선안**: 메일 본문에서 POST 폼으로 제출하도록 유도하거나, 최소한 토큰 만료 시간을 짧게(현재 30분) 유지하고 일회성 소비(consume)를 확실히 합니다. (현재 `tokenStore.consume(token)` 구현은 양호합니다)

---

## 2. 코드 중복 (Duplication)

### 2.1 🟡 `getUser` / `getMsg` / `blankToNull` 헬퍼 메서드의 광범위한 중복

**발견 위치** (최소 5개 파일):

| 파일 | 중복 메서드 |
|------|-----------|
| `StockService.java` | `getUser()`, `getMsg()`, `blankToNull()` |
| `ItemService.java` | `getUser()`, `getMsg()`, `blankToNull()` |
| `SpaceService.java` | `getUser()`, `getMsg()` |
| `ShelfService.java` | `getUser()`, `getMsg()` |
| `BoxService.java` | `getUser()`, `getMsg()` |
| `StockInboundPreparer.java` | `getMsg()`, `blankToNull()` |

**개선안**: 공통 `BaseService` 추상 클래스를 도입합니다:

```java
public abstract class BaseService {
    protected final UserMapper userMapper;
    protected final MessageSource messageSource;

    protected UserDTO getUser(String username) {
        return userMapper.findByEmail(username)
            .orElseThrow(() -> new NoSuchElementException(getMsg("error.user.notFound")));
    }

    protected String getMsg(String key, Object... args) {
        return messageSource.getMessage(key, args, LocaleContextHolder.getLocale());
    }

    protected static String blankToNull(String value) {
        return (value == null || value.isBlank()) ? null : value.trim();
    }
}
```

### 2.2 🟡 소유권 검증(`verifyOwner`) 패턴의 반복

`ItemService`, `SpaceService`, `ShelfService`, `BoxService`, `StockService`에서 거의 동일한 패턴으로 소유권 검증을 수행합니다.

**개선안**: `OwnershipVerifier` 유틸리티를 만들거나 `BaseService`에 제네릭 검증 메서드를 추가합니다:

```java
protected <T> void verifyOwnership(T resource, Function<T, Long> ownerIdExtractor,
                                    UserDTO user, String resourceType) {
    if (!ownerIdExtractor.apply(resource).equals(user.getId())) {
        log.warn("access denied userId={} resource={} resourceId={}", ...);
        throw new SecurityException(getMsg("error.403.title"));
    }
}
```

### 2.3 🟡 페이징 보일러플레이트 (기존 리포트에서도 지적)

**발견 위치**: `StockService`, `ItemService`, `SpaceService`

```java
int totalCount = mapper.countQuery(...);
PageRequest pageRequest = PageRequest.of(page, totalCount);
List<DTO> items = mapper.fetchQuery(..., pageRequest.size(), pageRequest.offset());
return new PageResult<>(items, pageRequest.page(), pageRequest.size(), totalCount);
```

**개선안**: `PaginationHelper` 유틸리티 도입:

```java
public class PaginationHelper {
    public static <T> PageResult<T> execute(
            Integer page,
            Supplier<Integer> countSupplier,
            Function<PageRequest, List<T>> fetchFunction) {
        int totalCount = countSupplier.get();
        PageRequest pr = PageRequest.of(page, totalCount);
        return new PageResult<>(fetchFunction.apply(pr), pr.page(), pr.size(), totalCount);
    }
}
```

---

## 3. 동시성 (Concurrency)

### 3.1 🟡 출고(`dispatchUnits`)의 SELECT → UPDATE Race Condition

**파일**: `service/StockService.java:338-392`

```java
// 1. 출고 가능한 재고 목록 조회 (SELECT)
List<StockDTO> units = stockMapper.findDispatchableByItemAndBox(...);
// ... count 확인 ...

// 2. 각 유닛에 대해 상태 업데이트 (UPDATE - 단건씩)
for (StockDTO unit : units.subList(0, form.getCount())) {
    int updated = stockMapper.updateStatusIfInStock(unit.getId(), StockStatus.DISPATCHED);
    if (updated != 1) { ... throw ... }
}
```

**문제**: 현재 `updateStatusIfInStock`이 `WHERE id = #{id} AND status = 'IN_STOCK'` 조건으로 Optimistic Lock을 수행하므로, 중복 출고 자체는 방지됩니다. 그러나 동시 요청에서 A가 앞쪽 유닛을, B가 뒤쪽 유닛을 각각 소비하는 상황에서 A가 실패하면 전체 트랜잭션이 롤백되어 B도 영향을 받습니다.

**개선안**: `SELECT ... FOR UPDATE`를 사용하거나, `findDispatchableByItemAndBox` 쿼리에 `LIMIT`를 적용한 단일 UPDATE 구문으로 전환합니다:

```sql
UPDATE stocks SET status = 'DISPATCHED'
WHERE id IN (
    SELECT id FROM stocks
    WHERE item_id = #{itemId} AND box_id = #{boxId}
      AND status = 'IN_STOCK' AND is_kept = FALSE
    ORDER BY created_at LIMIT #{count}
    FOR UPDATE
)
```

### 3.2 🟡 시리얼/로트 번호 생성의 원자성

**파일**: `service/SerialNumberGenerator.java`, `service/LotNumberGenerator.java`

`StockInboundPreparer`에서 시리얼 번호를 생성한 후 `itemMapper.updateSerialNextSequence`로 시퀀스를 갱신하는데, 이 두 작업 사이에 다른 트랜잭션이 동일 item의 시리얼을 생성할 가능성이 있습니다.

**개선안**: `UPDATE items SET serial_next_sequence = serial_next_sequence + #{count} * #{incrementUnit} WHERE id = #{id}` 형태의 원자적 UPDATE로 변경하고, 증가된 값을 반환받아 사용합니다. (또는 PostgreSQL SEQUENCE 사용)

---

## 4. 테스트 (Testing)

### 4.1 🟡 Service 계층 단위 테스트 전무

`src/test/java/` 내에 `mapper/`, `controller/` 테스트는 존재하지만 `service/` 패키지에 대한 단위 테스트가 **전혀 없습니다**. 특히 `StockService`(685라인)의 복잡한 비즈니스 로직이 Mock 없이 전혀 검증되지 않고 있습니다.

**개선안**: 최소한 아래 서비스에 대한 단위 테스트를 작성합니다:

| 우선순위 | 대상 | 사유 |
|---------|------|------|
| 1 | `StockService` | 가장 복잡하고 버그 위험 높음 |
| 2 | `StockInboundPreparer` | 시리얼/로트 생성 규칙 검증 필요 |
| 3 | `PasswordResetService` | 보안 관련 로직 |
| 4 | `ImageFileValidator` | 파일 시그니처 검증 |

### 4.2 🟡 Mapper 테스트 부족

현재 `UserMapperTest`, `StockTransactionMapperTest` 두 개만 존재합니다. `StockMapper`(가장 많은 쿼리를 가진 Mapper), `ItemMapper`, `SpaceMapper` 등의 테스트가 없습니다.

### 4.3 🟢 `schema-test.sql`과 Production 스키마 불일치

**파일**: `src/test/resources/schema-test.sql`

H2 호환성을 위한 스키마 차이는 불가피하나, 컬럼 순서, 제약조건 이름, 데이터 타입(`SERIAL` vs `BIGINT GENERATED BY DEFAULT AS IDENTITY`)이 상이하여 Production에서만 발생하는 버그 가능성이 있습니다.

**개선안**: Testcontainers를 도입하여 실제 PostgreSQL로 통합 테스트를 수행합니다.

---

## 5. 데이터베이스 (Database)

### 5.1 🟡 키워드 검색 성능

**파일**: `resources/mapper/StockMapper.xml:367-390`

```sql
i.name LIKE CONCAT('%', #{keyword}, '%')
OR s.serial_number LIKE CONCAT('%', #{keyword}, '%')
OR COALESCE(il.lot_number, s.lot_number) LIKE CONCAT('%', #{keyword}, '%')
OR s.memo LIKE CONCAT('%', #{keyword}, '%')
```

**문제**: Leading wildcard(`%keyword%`) 검색은 B-tree 인덱스를 사용할 수 없어 Full Table Scan이 발생합니다. 재고가 많아질수록 응답 시간이 선형적으로 증가합니다.

**개선안**:
1. PostgreSQL Full-Text Search(`tsvector`, `tsquery`) 도입
2. 또는 `pg_trgm` 확장을 사용한 Trigram 인덱스(`GIN`) 생성
3. 최소한 `serial_number`, `lot_number` 컬럼에 대한 B-tree 인덱스 추가

### 5.2 🟡 `stocks` 테이블 외래키 인덱스 누락

`stocks` 테이블의 `shelf_id`, `box_id`, `space_id`, `item_id` 컬럼은 WHERE 조건에 빈번하게 사용되지만, 외래키 제약조건만 있고 별도 인덱스가 생성되지 않을 수 있습니다(PostgreSQL은 FK에 자동 인덱스를 생성하지 않습니다).

**개선안**: 마이그레이션에 아래 인덱스를 추가합니다:

```sql
CREATE INDEX idx_stocks_item_id ON stocks(item_id);
CREATE INDEX idx_stocks_space_id_status ON stocks(space_id, status);
CREATE INDEX idx_stocks_shelf_id_status ON stocks(shelf_id, status) WHERE shelf_id IS NOT NULL;
CREATE INDEX idx_stocks_box_id_status ON stocks(box_id, status) WHERE box_id IS NOT NULL;
```

### 5.3 🟢 `V1__initial_schema.sql`의 `users.username` → `email` 전환

V1 마이그레이션은 `users.username` 컬럼을 생성하고, V4에서 `email`로 전환합니다. 신규 환경에서는 V1 생성 직후 V4가 실행되므로 문제없으나, 이력을 추적할 때 혼란을 줄 수 있습니다.

---

## 6. 아키텍처 및 설계 (Architecture)

### 6.1 🟡 `StockService`의 과도한 책임 (기존 리포트 보완)

**파일**: `service/StockService.java` (685라인)

기존 리포트에서도 지적되었으나, 구체적인 분리 방안을 추가합니다:

| 책임 | 이동 대상 | 우선순위 |
|------|----------|---------|
| 이미지 첨부(`attachPrimaryImageIfPresent`) | `ItemImageService` (신설) | 1 |
| 품목 생성 + 재고 동시 생성(`createWithNewItem`) | `StockCreationFacade` (신설) | 2 |
| 검색/조회 메서드들 | `StockQueryService` (신설) | 3 |
| 소유권 검증(`getVerifiedItem`, `verifyItemOwner`) | `BaseService` 또는 `OwnershipVerifier` | 2 |

### 6.2 🟢 `HtmxResponse`의 수동 JSON 직렬화

**파일**: `configuration/HtmxResponse.java`

현재 JSON 문자열을 수동으로 조립하고 있어, 특수 문자 이스케이프 누락 시 HTMX Trigger 파싱 오류 가능성이 있습니다.

**개선안**: Jackson `ObjectMapper`를 사용해 안전하게 직렬화합니다:

```java
private static void toast(HttpServletResponse response, String type, String message) {
    Map<String, Map<String, String>> trigger =
        Map.of("app:toast", Map.of("type", type, "message", message));
    response.addHeader("HX-Trigger", objectMapper.writeValueAsString(trigger));
}
```

### 6.3 🟢 `ImageFileValidator` 하드코딩된 한글 에러 메시지

**파일**: `service/ImageFileValidator.java:15`

```java
private static final String INVALID_IMAGE_FORMAT_MESSAGE = "지원하지 않는 이미지 형식입니다.";
```

`IllegalStateException("이미지 파일을 읽을 수 없습니다.")` (51라인)도 하드코딩되어 i18n이 적용되지 않습니다.

---

## 7. 운영 관측성 (Observability)

### 7.1 🟢 Health Check / Metrics 부재

**현황**: Spring Boot Actuator가 의존성에 포함되어 있지 않습니다. `/health`, `/metrics` 엔드포인트가 없어 컨테이너 오케스트레이션 환경에서 헬스체크가 불가능합니다.

**개선안**:
```kotlin
implementation("org.springframework.boot:spring-boot-starter-actuator")
implementation("io.micrometer:micrometer-registry-prometheus")
```

### 7.2 🟢 Rate Limiting 부재

이미지 분석(`ImageController`), 비밀번호 재설정 요청 등 리소스 집약적이거나 보안 민감한 엔드포인트에 Rate Limiting이 없습니다.

**개선안**: Spring의 `Bucket4j` 또는 `Resilience4j`를 도입하거나, Reverse Proxy(Nginx)에서 처리합니다.

### 7.3 ✅ 구조화 로깅 양호

`log.info("stock created userId={} itemId={}", ...)` 패턴이 일관되게 적용되어 있어 로그 파싱에 유리합니다.

---

## 8. 기술 스택 위험 (Tech Stack Risk)

### 8.1 🟢 JDK 25 사용

**파일**: `build.gradle.kts:17`

```kotlin
languageVersion = JavaLanguageVersion.of(25)
```

JDK 25는 2025년 9월 GA 예정으로, 현재(2026년 6월) 기준 안정화되었을 수 있으나 LTS가 아닙니다. Production 배포 시 JDK 21 LTS 또는 25 LTS(출시 시) 검토가 필요합니다.

### 8.2 🟢 Spring AI 2.0.0-M6 (Milestone)

```kotlin
val springAiVersion by extra("2.0.0-M6")
```

Milestone 버전은 Breaking Change 가능성이 있습니다. 정식 릴리스로 업그레이드하거나, 최소한 Renovate/Dependabot으로 변경사항을 추적해야 합니다.

---

## 9. 프론트엔드 (Frontend)

### 9.1 🟢 CSRF 토큰 처리

**파일**: `static/js/csrf.js`

`CookieCsrfTokenRepository.withHttpOnlyFalse()`를 통해 XSRF-TOKEN 쿠키를 JS에서 읽을 수 있게 설정하고, 모든 AJAX 요청에 포함시키는 패턴이 잘 구현되어 있습니다.

### 9.2 검토 제외

Thymeleaf 템플릿, `ui.css`, `image-upload.js`, `tracking-policy-preview.js`는 이번 리뷰 범위에서 제외했습니다.

---

## 10. 우선순위별 개선 로드맵

### 🔴 긴급 (이번 스프린트)

1. **`DataInitializer` 시드 계정 환경변수화** (보안)
2. **컨트롤러 하드코딩 메시지 i18n 적용** (국제화)
3. **`stocks` 테이블 검색/필터 인덱스 추가** (성능)

### 🟡 단기 (1~2주)

4. **`BaseService` 도입으로 `getUser`/`getMsg`/`blankToNull` 중복 제거**
5. **`StockService` 단위 테스트 작성 (최소 Happy Path)**
6. **`PaginationHelper` 도입으로 페이징 보일러플레이트 제거**
7. **출고(`dispatchUnits`) SELECT FOR UPDATE 적용**

### 🟢 중기 (1~2개월)

8. **`StockService` 책임 분리 (CQRS + Facade)**
9. **Testcontainers 도입으로 Production DB 유사 환경 테스트**
10. **Spring Boot Actuator + Micrometer 도입**
11. **PostgreSQL Full-Text Search 도입**
12. **Rate Limiting 적용**

---

## 부록: 칭찬할 점

이 코드베이스에서 특히 잘 구현된 부분들입니다:

- ✅ **소유권 검증 패턴**: 모든 서비스에서 일관되게 리소스 접근 전 소유자를 확인합니다
- ✅ **Optimistic Locking**: `AND status = 'IN_STOCK'` 조건을 활용한 상태 변경 보호
- ✅ **ImageFileValidator**: Content-Type 선언값과 Magic Bytes를 모두 검증하는 2단계 이미지 검증
- ✅ **Anti-Enumeration**: 비밀번호 재설정 시 계정 존재 여부를 항상 동일한 응답으로 처리
- ✅ **CSRF + HTMX 통합**: `CookieCsrfTokenRepository`와 `CsrfCookieFilter`를 통한 HTMX 친화적 CSRF 보호
- ✅ **i18n 설계**: `messages.properties` + `messages_ko.properties`, `messages_en.properties` 등 4개 언어 지원
- ✅ **Image Deduplication**: `content_hash` 기반 중복 이미지 저장 방지 (`AbstractImageStorageService`)
- ✅ **Structured Logging**: `key=value` 형식의 일관된 로그 출력
- ✅ **Feature Flags**: `seustock.features.lot-serial-enabled`, `seustock.ai.yolo.enabled` 등으로 기능 ON/OFF 제어
