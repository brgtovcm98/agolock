# 홈 대시보드 기능 코드 리뷰 (Dashboard Feature Code Review)

> **리뷰 일자**: 2026-07-08
> **리뷰 범위**: `develop` 브랜치의 `origin/develop` 대비 커밋된 변경분 + 워킹트리 미커밋 변경분 (총 85개 파일, +2,690/-532줄)
> **대상 기능**: 홈 대시보드(보유 현황/목표/상태별 분포/공간별 요약/최근 활동), 재고 목록 상태·유효기간 필터, 아이템 목록 프래그먼트 리팩터링
> **방법론**: 8개 관점(정확성 3종: 라인 단위 스캔 / 제거된 동작 감사 / 크로스파일 추적, 재사용, 단순화, 효율성, 아키텍처 적합성, CLAUDE.md 컨벤션)으로 독립 병렬 리뷰 후, 상위 후보를 실제 소스 코드 재확인으로 검증한 recall 우선 하이-이펙트 리뷰

---

## 총평 (Executive Summary)

대시보드 기능은 기존 아키텍처(서비스 소유권 검증 패턴, `DirectOnly` 집계 쿼리, `HtmxResponse` 토스트 헬퍼 등)를 대체로 잘 따르고 있습니다. 다만 새 기능이 기존 화면(재고 목록, 공간 생성 모달)에 미치는 파급 효과를 검증하는 과정에서 **HTMX 대상 불일치로 화면이 깨지는 버그**와 **보안 관련 테스트 2건이 의도치 않게 삭제된 회귀**가 발견되어 우선 조치가 필요합니다.

| 영역 | 심각도 | 핵심 이슈 |
|------|--------|-----------|
| **정확성 (UI 파괴)** | 🔴 HIGH | 공간 추가 모달의 HTMX 대상 불일치로 목록 컨테이너가 파괴됨 |
| **정확성 (표시 오류)** | 🟡 MEDIUM | 대시보드 링크로 진입 시 재고 목록에 모순된 안내 문구 2줄 동시 표시 |
| **테스트 커버리지 회귀** | 🔴 HIGH | 보안 관련 테스트 2건이 무관한 커밋에서 삭제됨 |
| **국제화(i18n)** | 🟡 MEDIUM | 한국어 로케일만 "출고"→"사용" 용어 변경, 나머지 4개 로케일 미반영 |
| **기능 누락** | 🟢 LOW | 대시보드 목표(target) 값을 UI에서 해제(clear)할 방법이 없음 |
| **컨벤션 위반** | 🟢 LOW | `DashboardMapper.findSummaryByUserId`가 `Optional<T>` 규칙 미준수 |
| **효율성** | 🟡 MEDIUM | 홈 화면 로드마다 사용자 조회 3회 중복, 요약 집계가 9개 개별 서브쿼리 |
| **아키텍처 적합성** | 🟢 LOW | 신규 필터 파라미터가 검증 없이 raw String으로 매퍼까지 전달, SQL 중복 |

---

## 1. 정확성 (Correctness)

### 1.1 🔴 공간 추가 모달의 HTMX 대상(target) 불일치 — 목록 컨테이너 파괴

**파일**: `controller/SpaceController.java:97-99`, `templates/spaces/fragments/modal.html:25-29`

이번 diff에서 공간 생성 폼의 `hx-target`이 `#modal` → `#space-list-section`으로 변경되었습니다(전체 목록 새로고침 패턴 적용).

```html
<!-- templates/spaces/fragments/modal.html -->
<form th:object="${form}" method="post" hx-post="/spaces"
      hx-target="#space-list-section" hx-swap="outerHTML" ...>
```

하지만 유효성 검증 실패 시 컨트롤러는 여전히 `#modal` 루트 엘리먼트를 반환합니다.

```java
// controller/SpaceController.java
if (result.hasErrors()) {
    ...
    if ("true".equals(htmxRequest)) {
        return "spaces/fragments/modal :: modal";   // <div id="modal" ...> 반환
    }
    ...
}
```

**문제**: `hx-swap="outerHTML"`이므로, 반환된 `<div id="modal">` 마크업이 `#space-list-section` 엘리먼트 전체를 대체해 버립니다. 결과적으로:
1. 검증 오류가 (숨겨진) 모달 오버레이 안에 렌더링되어 사용자에게 보이지 않음
2. `id="space-list-section"`을 가진 실제 목록 컨테이너가 DOM에서 사라짐 → 정렬/페이지네이션/수정/삭제 등 해당 컨테이너를 대상으로 하는 모든 후속 HTMX 요청이 새로고침 전까지 무반응 상태가 됨

동일 문제가 이미 `ItemController.java:202-203`에서는 `HX-Retarget`/`HX-Reswap` 헤더로 올바르게 처리되어 있어, 이번 변경에서만 누락된 것으로 보입니다.

**재현 시나리오**: "새 공간 추가" 모달을 열고 공간명을 비운 채 제출 → 오류 메시지가 보이지 않고 공간 목록 영역이 사라짐 → 다른 목록 조작(정렬, 페이지 이동 등)이 페이지 새로고침 전까지 동작하지 않음.

**개선안**: `ItemController`와 동일하게 검증 실패 시 `HX-Retarget: #modal`, `HX-Reswap: outerHTML` 헤더를 설정하거나, 폼의 `hx-target`을 유지한 채 별도 OOB(`hx-swap-oob`) 블록으로 목록을 갱신하는 방식으로 통일합니다.

---

### 1.2 🟡 재고 목록의 `filtered` 플래그가 신규 `filter` 파라미터를 누락 — 모순된 안내 문구

**파일**: `controller/StockController.java:73-79`, `templates/stocks/list.html:16-20`

```java
model.addAttribute(
    "filtered",
    itemExternalId != null
        || spaceExternalId != null
        || shelfExternalId != null
        || boxExternalId != null
        || (keyword != null && !keyword.isBlank()));   // filter 파라미터 미포함
```

```html
<!-- templates/stocks/list.html -->
<p th:text="${filtered} ? #{view.stock.list.description.filtered} : #{view.stock.list.description.all}"></p>
<th:block th:if="${filter != null}" ...>
    <p ...>필터 적용됨</p>
</th:block>
```

**문제**: 대시보드에서 새로 추가된 상태/유효기간 배지 링크(`/stocks?filter=dispatched`, `/stocks?filter=expiring` 등)로 진입하면 `filter`만 설정되고 `itemExternalId`/`spaceExternalId`/`shelfExternalId`/`boxExternalId`/`keyword`는 모두 비어 있어 `filtered`가 `false`로 평가됩니다. 그 결과 "현재 보유 중인 개별 재고입니다(전체)"라는 문구와 "사용된 재고만 보고 있습니다(필터 적용됨)"라는 문구가 동시에 렌더링되어 모순됩니다.

**개선안**: `filtered` 계산식에 `filter != null` 조건을 추가하거나, 두 문구를 하나의 조건부 렌더링으로 통합합니다.

---

### 1.3 🟢 대시보드 목표(target) 값을 UI에서 해제할 방법이 없음

**파일**: `model/form/DashboardTargetForm.java:13`, `controller/IndexController.java:46-68`, `service/DashboardService.java:56-62`

```java
// DashboardService.java
/** 사용자별 총 보유 수량 목표를 설정한다. {@code null}이면 목표 해제. */
public void updateTarget(String username, Integer target) { ... }
```

```java
// DashboardTargetForm.java
@NotNull(message = "{valid.dashboard.target.notNull}")
@Min(value = 0, message = "{valid.dashboard.target.min}")
private Integer target;
```

**문제**: 서비스 계층은 `target == null`을 "목표 해제"로 명시적으로 지원하도록 문서화되어 있고 `UserMapper`/DB도 NULL을 문제없이 저장하지만, 유일한 호출 경로인 `IndexController.updateTarget()`은 `@Valid DashboardTargetForm`에 `@NotNull`이 걸려 있어 빈 값 제출 시 검증 오류로 막힙니다. `dashboard.html`에도 별도의 "목표 해제" 버튼/링크가 없습니다.

**개선안**: 목표 해제 전용 버튼(예: `POST /dashboard/target/clear`)을 추가하거나, `target` 필드를 옵셔널로 바꾸고 빈 입력을 명시적으로 null로 처리하는 UX를 마련합니다.

---

## 2. 테스트 커버리지 회귀 (Test Coverage Regression)

같은 커밋(`30eef47`, 커밋 메시지: "test: remove unnecessary lenient stubs")에서 아래 두 테스트 파일이 **전체 삭제**되었습니다. 두 파일 모두 Mockito `lenient()` 스텁을 전혀 사용하지 않는 순수 어서션 테스트라 커밋 메시지가 설명하는 정리 작업과 무관해 보이며, 실수로 함께 삭제되었을 가능성이 높습니다.

### 2.1 🔴 `SecurityConfigCookieTest` 삭제 — CSRF 쿠키 Secure 플래그 검증 소실

**파일(삭제됨)**: `src/test/java/com/seu/seustock/configuration/SecurityConfigCookieTest.java`

```java
@TestPropertySource(properties = "seustock.security.cookie-secure=true")
class SecurityConfigCookieTest {
    @Test
    void csrfCookie_isSecureWhenEnabled() throws Exception {
        mockMvc.perform(get("/login"))
               .andExpect(status().isOk())
               .andExpect(cookie().secure("XSRF-TOKEN", true));
    }
}
```

`SecurityConfig`의 쿠키 커스터마이저 자체는 이번 diff에서 변경되지 않았으나, `seustock.security.cookie-secure=true`일 때 `XSRF-TOKEN` 쿠키에 `Secure` 플래그가 실제로 설정되는지 검증하는 유일한 테스트였습니다. 다른 테스트(`ImageControllerTest`, `SpaceControllerTest`)는 쿠키 존재 여부만 확인하고 `Secure` 플래그는 확인하지 않습니다.

**위험**: 향후 `SecurityConfig`의 쿠키 커스터마이저를 리팩터링하다가 `Secure` 플래그가 실수로 빠져도 테스트 스위트가 이를 잡아내지 못합니다. HTTPS 운영 환경에서 CSRF 쿠키가 `Secure` 없이 전송될 수 있습니다.

**개선안**: 삭제된 테스트를 복원합니다.

### 2.2 🔴 `LoggingPasswordResetMailSenderTest` 삭제 — 토큰/이메일 로그 유출 방지 가드 검증 소실

**파일(삭제됨)**: `src/test/java/com/seu/seustock/service/LoggingPasswordResetMailSenderTest.java`

```java
@ExtendWith(OutputCaptureExtension.class)
class LoggingPasswordResetMailSenderTest {
    @Test
    void sendDoesNotLogRecipientOrResetToken(CapturedOutput output) {
        LoggingPasswordResetMailSender sender = new LoggingPasswordResetMailSender();
        sender.send("user@example.com", "https://example.com/password/reset?token=secret-token");

        assertThat(output).contains("password reset mail prepared");
        assertThat(output).doesNotContain("user@example.com");
        assertThat(output).doesNotContain("secret-token");
        assertThat(output).doesNotContain("/password/reset?token=");
    }
}
```

CLAUDE.md는 `LoggingPasswordResetMailSender`가 "로그만 남기고, 수신자나 토큰은 절대 로그에 남기지 않는다"는 것을 명시적 요구사항으로 규정하고 있습니다. 이 테스트가 그 요구사항을 검증하는 유일한 장치였습니다.

**위험**: 향후 로깅 구현을 수정하다가 실수로 원본 토큰이나 이메일 주소를 로그에 남기게 되어도 감지할 테스트가 없습니다. 토큰이 로그로 유출되면 비밀번호 재설정 링크를 탈취당할 수 있습니다(계정 탈취로 이어질 수 있는 실질적 보안 리스크).

**개선안**: 삭제된 테스트를 복원합니다.

---

## 3. 국제화 (i18n)

### 3.1 🟡 한국어 로케일만 "출고" → "사용" 용어 변경, 나머지 4개 로케일 미반영

**파일**: `messages.properties` (변경) vs `messages_en/ja/zh_CN/mn.properties` (미변경)

이번 diff는 대시보드용 신규 키 43개를 5개 로케일 파일 모두에 동일하게 추가했지만, 이와 별개로 재고 출고(`OUT`/`DISPATCHED`) 관련 기존 한국어 문구를 "출고" → "사용"으로 변경하면서 이 부분은 한국어 파일에만 반영했습니다.

```diff
# messages.properties
-enum.StockStatus.DISPATCHED=출고
+enum.StockStatus.DISPATCHED=사용
-enum.TransactionType.OUT=출고
+enum.TransactionType.OUT=사용
-view.stock.modal.out.title=출고
+view.stock.modal.out.title=사용
-toast.stock.out=출고되었습니다.
+toast.stock.out=사용되었습니다.
```

다른 4개 로케일 파일은 여전히 기존 번역을 유지 중입니다:

| 로케일 | `enum.StockStatus.DISPATCHED` | `toast.stock.out` |
|--------|-------------------------------|--------------------|
| en | `Dispatched` | `Stock-out completed.` |
| ja | `発送済み` | `出庫されました。` |
| zh_CN | `已发货` | `已出库。` |
| mn | `Илгээгдсэн` | `Зарлага гаргалаа.` |
| ko (변경됨) | `사용` | `사용되었습니다.` |

**문제**: CLAUDE.md의 "UI 문자열을 추가/변경할 때는 5개 파일 모두에 키를 추가한다"는 규칙 위반입니다. 영어/일본어/중국어/몽골어 사용자에게는 여전히 "출고(Dispatch)" 개념으로 노출되어, 한국어 UI와 용어가 어긋납니다.

**개선안**: 나머지 4개 로케일 파일에도 동일한 용어 변경을 반영합니다.

---

## 4. MyBatis 컨벤션 위반

### 4.1 🟢 `DashboardMapper.findSummaryByUserId`가 `Optional<T>` 규칙 미준수

**파일**: `mapper/DashboardMapper.java:16`

```java
DashboardSummaryDTO findSummaryByUserId(
    @Param("userId") Long userId, @Param("today") LocalDate today, @Param("soonCutoff") LocalDate soonCutoff);
```

CLAUDE.md는 "단일 결과를 반환하는 SELECT 메서드는 `Optional<T>`를 반환해야 한다"고 명시하고 있으며, `UserMapper`, `BoxMapper`, `SpaceMapper`, `StockMapper`, `ItemMapper`, `ShelfMapper` 등 프로젝트 내 모든 단일 결과 조회 메서드가 이 규칙을 따릅니다. `findSummaryByUserId`만 `Optional` 래핑 없이 원시 DTO를 반환합니다.

**위험**: 현재는 유일한 호출부인 `DashboardService.getSummary()`가 호출 전에 `getUser()`로 사용자 존재를 이미 검증하므로 실질적인 NPE는 발생하지 않지만, 향후 이 매퍼 메서드를 사전 검증 없이 호출하는 코드가 추가되면 잠재적 NPE 위험이 있고, 코드베이스 전역 컨벤션과도 어긋납니다.

**개선안**: 반환 타입을 `Optional<DashboardSummaryDTO>`로 변경합니다.

---

## 5. 효율성 (Efficiency)

### 5.1 🟡 홈 화면(`/`) 로드마다 사용자 조회 3회 중복 실행

**파일**: `controller/IndexController.java:34-38`, `service/DashboardService.java` (`getSummary`, `getSpaceSnapshot`, `getRecentActivity`)

```java
@GetMapping("/")
public String index(Principal principal, Model model) {
    String username = principal.getName();
    model.addAttribute("summary", dashboardService.getSummary(username));           // getUser() 1회
    model.addAttribute("spaceSnapshot", dashboardService.getSpaceSnapshot(username)); // getUser() 1회
    model.addAttribute("recentActivity", dashboardService.getRecentActivity(username)); // getUser() 1회
    ...
}
```

세 메서드가 각각 독립적으로 `BaseService.getUser(username)`(=`UserMapper.findByEmail`)를 호출합니다. 앱에서 가장 자주 방문되는 홈 화면이 방문할 때마다 동일한 사용자 조회 쿼리를 3번 실행합니다. 동일한 중복이 `IndexController.updateTarget()`의 검증 실패 분기에도 반복됩니다.

**개선안**: 컨트롤러에서 `UserDTO`를 한 번만 조회해 `DashboardService`의 각 메서드에 `userId`를 직접 전달하거나, `DashboardService`에 세 위젯을 한 번에 채우는 파사드 메서드(`getHome(username)`)를 추가합니다.

### 5.2 🟡 대시보드 요약 집계가 9개의 개별 상관 서브쿼리로 구성됨

**파일**: `resources/mapper/DashboardMapper.xml:10` (`findSummaryByUserId`)

`total_item_count`, `total_stock_count`, `total_value`, `dispatched_count`, `lost_count`, `damaged_count`, `disposed_count`, `kept_count`, `expiring_count`, `expired_count` 각각이 `stocks st JOIN items i ON st.item_id = i.id WHERE i.user_id = u.id`를 매번 다시 조인/필터링하는 개별 서브쿼리로 작성되어 있습니다. 홈 화면 방문마다 동일한 행 집합을 9번 스캔합니다.

**개선안**: `COUNT(*) FILTER (WHERE ...)` / `SUM(...) FILTER (WHERE ...)` (또는 `CASE`) 기반의 단일 GROUP BY 집계 쿼리로 통합해 1회 스캔으로 축소합니다.

---

## 6. 아키텍처 적합성 (Altitude)

### 6.1 🟢 신규 상태/유효기간 필터가 검증 없이 raw String으로 매퍼까지 전달, SQL 블록 중복

**파일**: `resources/mapper/StockMapper.xml:360-380`, `440-460`, `controller/StockController.java`, `service/StockService.java`

신규 `filter` 파라미터(`dispatched`/`lost`/`damaged`/`disposed`/`damagedLost`/`kept`/`expiring`/`expired`)가 `StockController` → `StockService` → `StockMapper.xml`까지 원시 `String`으로 전달되어 MyBatis `<choose>`/`<when test="filter == '...'">`에서 문자열 리터럴 비교로 처리됩니다. CLAUDE.md는 "enum은 항상 상수로 다루고 매퍼 파라미터에 원시 문자열을 비교하지 않는다"를 명시하고 있으며, 같은 서비스 파일에 이미 `normalizeSearchType()`/`normalizeSort()`라는 문자열 정규화·검증 패턴이 존재함에도 `filter`는 이 정규화 단계를 거치지 않습니다. 인식할 수 없는 값은 조용히 `<otherwise>`(기본 `IN_STOCK`) 분기로 빠집니다.

또한 동일한 7분기 `<choose>` 블록이 `searchDetails`와 `countSearchDetails` 두 `<select>`에 그대로 복사되어 있어(이 파일에 이미 `StockDetailSelect`라는 재사용 `<sql>` 프래그먼트 관례가 있음에도), 한쪽만 수정되면 목록과 총 개수(페이지네이션)가 어긋날 위험이 있습니다.

**개선안**: `StockService`에서 `filter` 값을 정규화/검증(알 수 없는 값이면 `IllegalArgumentException`)한 뒤 매퍼에는 안전한 값만 전달하고, `<choose>` 블록을 `<sql id="stockStatusFilter">`로 추출해 두 쿼리에서 `<include>`로 공유합니다.

---

## 7. 우선순위별 조치 로드맵

### 🔴 긴급

1. **공간 추가 모달 HTMX 대상 불일치 수정** ([§1.1](#11--공간-추가-모달의-htmx-대상target-불일치--목록-컨테이너-파괴)) — 화면이 실제로 깨지는 사용자 체감 버그
2. **삭제된 보안 테스트 2건 복원** ([§2.1](#21--securityconfigcookietest-삭제--csrf-쿠키-secure-플래그-검증-소실), [§2.2](#22--loggingpasswordresetmailsendertest-삭제--토큰이메일-로그-유출-방지-가드-검증-소실))

### 🟡 단기

3. **재고 목록 `filtered` 플래그에 `filter` 파라미터 반영** ([§1.2](#12--재고-목록의-filtered-플래그가-신규-filter-파라미터를-누락--모순된-안내-문구))
4. **4개 로케일 파일에 "출고→사용" 용어 변경 반영** ([§3.1](#31--한국어-로케일만-출고--사용-용어-변경-나머지-4개-로케일-미반영))
5. **홈 화면 사용자 조회 3중 호출 통합** ([§5.1](#51--홈-화면-로드마다-사용자-조회-3회-중복-실행))
6. **`filter` 파라미터 정규화 + `<choose>` 블록 공유 프래그먼트화** ([§6.1](#61--신규-상태유효기간-필터가-검증-없이-raw-string으로-매퍼까지-전달-sql-블록-중복))

### 🟢 중기

7. **대시보드 요약 집계 쿼리를 단일 GROUP BY로 통합** ([§5.2](#52--대시보드-요약-집계가-9개의-개별-상관-서브쿼리로-구성됨))
8. **대시보드 목표 해제 UX 추가** ([§1.3](#13--대시보드-목표target-값을-ui에서-해제할-방법이-없음))
9. **`DashboardMapper.findSummaryByUserId`를 `Optional<T>`로 정정** ([§4.1](#41--dashboardmapperfindsummarybyuserid가-optionalt-규칙-미준수))

---

## 참고: 리뷰 방법론

이 리뷰는 `git diff @{upstream}...HEAD`(커밋된 변경분)와 `git diff HEAD`(미커밋 워킹트리 변경분)를 합친 전체 diff(85개 파일, +2,690/-532줄)를 대상으로, 아래 8개 관점의 독립 에이전트가 각각 최대 6건의 후보를 수집한 뒤, 중복 제거 및 실제 소스 재확인(직접 `Read`/`grep`)을 거쳐 검증된 항목만 정리한 것입니다.

- **정확성 — 라인 단위 스캔**: 변경된 모든 hunk를 줄 단위로 검토
- **정확성 — 제거된 동작 감사**: diff에서 삭제/대체된 코드가 지키던 불변조건이 새 코드에도 유지되는지 확인
- **정확성 — 크로스파일 추적**: 변경된 함수의 호출자/피호출자 호환성 확인
- **재사용(Reuse)**: 기존 유틸리티/공유 프래그먼트를 두고 새로 재구현한 부분 탐색
- **단순화(Simplification)**: 불필요하게 복잡해진 코드 탐색
- **효율성(Efficiency)**: 새로 추가된 낭비적 연산(N+1, 중복 스캔 등) 탐색
- **아키텍처 적합성(Altitude)**: 기존 공유 인프라 위에 임시방편(bandaid)으로 얹힌 특수 케이스 탐색
- **CLAUDE.md 컨벤션**: 프로젝트 규칙 문서에 명시된 규칙 위반 탐색

이번 리뷰에서 발견되었으나 지면상 본문에 포함하지 않은 저우선순위 정리(Cleanup) 항목: 4개 재고 관련 폼 클래스(`QuickStockForm`/`StockForm`/`StockInOutForm`/`StockMoveForm`)에 `allView`/`keyword`/`sortBy` 필드가 각각 복사·붙여넣기된 점, 거래유형 배지 색상 삼항연산자가 템플릿 3곳에 중복된 점, `DashboardActivityDTO.memo` 필드가 조회는 되지만 어디에서도 렌더링되지 않는 점, 공간별 요약 위젯이 서버가 아닌 템플릿에서 매직 넘버 `6`으로 잘리는 점, `kept` 필터가 기존 부분 인덱스 `idx_stocks_available`(`WHERE is_kept = FALSE`)의 여집합이라 인덱스 혜택을 받지 못하는 점 등은 추후 코드 정리 시 함께 검토할 것을 권장합니다.
