# SeuStock 개발현황 리포트

> 작성일: 2026-06-01 · 대상 브랜치: `develop` · 범위: 전체 코드베이스 정적 검토
> 본 리포트는 **코드 정독 기반**의 평가이며, 자동화된 커버리지/실행 검증(CI·JaCoCo)이 없는 상태에서 작성되었다. 리팩토링 상세 분석은 [`code-review-report.md`](./code-review-report.md) 참조.

---

## 0. 요약 (Executive Summary)

SeuStock는 개인용 재고관리 웹 애플리케이션으로, **기능 구현 완성도는 매우 높은 단계**다. 인증·물리계층(공간/선반/박스)·품목·재고(시리얼/로트/가격 추적 포함)·이미지+AI 분석·QR·5개국어 i18n까지 핵심 기능이 모두 동작 가능한 형태로 구현되어 있고, 최근 두 설계 스펙(serial-lot-policy, inherited-pricing)도 마이그레이션·매퍼·DTO·UI 수준까지 반영 완료되었다.

반면 **개발 인프라가 사실상 부재**하다. CI 파이프라인이 없고, 코드 커버리지 측정(JaCoCo)·정적분석(Checkstyle/PMD/SpotBugs)·포매터(Spotless)·pre-commit 훅이 전혀 설정되어 있지 않다. 테스트는 348개로 양적으로 충분하나 일부 클래스(이미지 junction 매퍼, `ItemLotService`, `PasswordResetController` 등)가 미커버다. 그 외 커밋 위생(동일 메시지 중복 커밋), `uploads/` 미(未) gitignore 등 소소한 정리거리가 있다.

**한 줄 결론: "제품 기능은 출시 가능 수준, 개발 인프라는 0→1이 필요한 단계."**

### 영역별 완성도 한눈표

| 영역 | 상태 | 비고 |
|---|---|---|
| 인증 / 비밀번호 재설정 | ✅ 완료 | 프로필/닉네임 수정 엔드포인트만 부재 |
| 물리 계층 (공간→선반→박스) | ✅ 완료 | CRUD + 모달 + 인라인 수정 |
| 품목 + 시리얼/로트 추적 설정 | ✅ 완료 | 자동/수동 생성기 포함 |
| 재고 (입·출고/이동/상태/keep/메모) | ✅ 완료 | 27개 엔드포인트, 트랜잭션 원장 |
| 로트 (item_lots) | ✅ 완료 | 상세 모달 |
| 이미지 업로드 + AI 분석 | ✅ 완료 | Ollama/YOLO 외부 의존 |
| QR 생성·스캔 라우팅 | ✅ 완료 | 소유권 검증 포함 |
| i18n (5개국어) | ✅ 완료 | ko/en/ja/zh_CN/mn |
| 테스트 | 🟡 양호하나 갭 존재 | 348 테스트, 일부 클래스 미커버 |
| CI / 커버리지 / 정적분석 / 포매팅 | ❌ 부재 | 최우선 개선 대상 |

---

## 1. 시스템 개요 / 기술 스택

| 구분 | 채택 기술 |
|---|---|
| 언어 / 런타임 | **Java 25** (`JavaLanguageVersion.of(25)`) |
| 프레임워크 | **Spring Boot 4.0.6** |
| 빌드 | Gradle (Kotlin DSL). 플러그인: `java`, `org.springframework.boot`, `io.spring.dependency-management` |
| 영속성 | MyBatis (`mybatis-spring-boot-starter:4.0.1`) + PostgreSQL(운영) / H2(테스트) |
| 스키마 관리 | Flyway (`V1`~`V7`) |
| 뷰 | Thymeleaf SSR + HTMX (부분 갱신) |
| 세션/토큰 | Redis (세션 저장, 비밀번호 재설정 토큰) |
| 객체 스토리지 | MinIO (`@Primary`) + 로컬 디스크 폴백 |
| AI | Spring AI Ollama(`gemma3:4b`) + 외부 YOLO 탐지 |
| 기타 | ZXing(QR), Spring Security, Spring Mail |

**요청 흐름:** HTTP → `@Controller` → `Service`(소유권 검증·트랜잭션) → MyBatis `Mapper` 인터페이스 → XML 매퍼 → PostgreSQL. 모든 페이지는 Thymeleaf SSR이며, HTMX는 모달·인라인 편집·삭제 확인 등 타깃 부분 갱신에만 사용한다. 모든 외부 식별자는 `external_id`(UUID)로 노출하고 내부 `id`(SERIAL)는 FK 조인에만 사용한다.

---

## 2. 기능별 개발현황 / 완성도

총 **11개 `@Controller` · 73개 엔드포인트 매핑**(`@Get/Post/Put/Patch/DeleteMapping` 기준 집계). `ControllerLogSupport`는 컨트롤러가 아닌 로깅 헬퍼다.

### 컨트롤러별 엔드포인트 수

| 컨트롤러 | 매핑 수 | 영역 |
|---|---:|---|
| `StockController` | 27 | 재고 전 생애주기 |
| `ItemController` | 9 | 품목 카탈로그 |
| `SpaceController` | 8 | 공간 + 선반 생성 |
| `ShelfController` | 6 | 선반 |
| `BoxController` | 5 | 박스 |
| `PasswordResetController` | 4 | 비밀번호 재설정 |
| `QrController` | 4 | QR |
| `UserController` | 4 | 회원가입/로그인 |
| `ImageController` | 3 | 이미지 조회/분석 |
| `IndexController` | 2 | 인덱스 |
| `ItemLotController` | 1 | 로트 상세 |
| **합계** | **73** | |

### 2.1 인증 / 사용자 — ✅ 완료
- `UserController`(회원가입·로그인 폼·이메일 중복확인), `PasswordResetController`(요청·재발송·재설정), `SecurityConfig`(이메일 기반 폼 로그인, Cookie CSRF), `PasswordResetService`(Redis 단일사용 토큰 + 재발송 쿨다운, 이메일 열거 방지).
- 메일 전송은 인터페이스 + 조건부 구현 2종: `LoggingPasswordResetMailSender`(기본), `SmtpPasswordResetMailSender`(Gmail). 자격증명은 전부 env var.
- **갭:** 가입 후 닉네임/프로필 수정 엔드포인트가 없다(읽기 전용 표시만).

### 2.2 물리 계층 (공간→선반→박스) — ✅ 완료
- `SpaceController`/`ShelfController`/`BoxController`가 CRUD + 모달 생성 + 인라인 rename을 제공. 공간 상세에서 선반·재고 패널과 브레드크럼 네비게이션 지원.
- `chk_box_requires_shelf` 제약으로 "박스는 선반에 종속" 무결성을 DB가 강제.

### 2.3 품목 + 시리얼/로트 추적 설정 — ✅ 완료
- `ItemController`: 목록(검색 타입 name/description/code + 정렬 + 페이징), 생성 모달, 인라인 수정, 삭제, 공간별 재고 분포 모달, 거래이력 모달.
- 추적 설정은 `TrackingMode`(NONE/AUTO/MANUAL)로 시리얼·로트 각각 구성. 자동 생성은 `SerialNumberGenerator`/`LotNumberGenerator`가 담당.

### 2.4 재고 (stocks) — ✅ 완료
- `StockController` 27개 엔드포인트: 검색·목록, 위치 계층별 패널 조회(`/spaces|shelves|boxes/{id}/stocks`, `/stocks/all`), 단건/배치 생성, 입고(`/stocks/in`)·출고(`/stocks/out`)·이동(`/stocks/move`), quick-create(품목+재고 동시 생성), 단위별 상태 변경, keep 토글, 메모 관리(이력 기반 제안).
- `StockService`는 위치 계층 검증(`resolveVerifiedLocation`)·소유권 검증을 거쳐 `stocks` 변경 시 `stock_transactions` 원장 행을 같은 트랜잭션에 기록.

### 2.5 로트 (item_lots) — ✅ 완료
- `ItemLotController`(`GET /lots/{externalId}`) + `ItemLotService.findDetail()`로 로트 메타데이터 + 소속 단위 목록을 모달로 제공.

### 2.6 이미지 업로드 + AI 분석 — ✅ 완료 (외부 의존)
- `ImageController`: 이미지 조회(`/images/{externalId}`), 업로드 분석(`/images/analyze`), 저장 이미지 분석.
- 스토리지는 인터페이스 + `MinioImageStorageService`(@Primary)/`LocalImageStorageService` 폴백. 업로드는 `ImageFileValidator`가 선언 content-type과 **매직바이트**를 함께 검증(스푸핑 차단), SHA-256 해시로 중복 제거.
- AI는 `YoloGemmaImageAnalysisService`(YOLO 탐지 → Gemma 비전 → 한국어 name/description), 비동기 실행·재시도(온도·시드 변경) 포함.
- **주의:** 분석 기능은 Ollama/YOLO 로컬 인스턴스 실행을 전제로 한다(테스트에서는 비활성).

### 2.7 QR — ✅ 완료
- `QrController`: 모달(`/api/qr/modal`), PNG 생성(`/api/qr/generate`), 박스/선반 스캔 리다이렉트(`/qr/boxes|shelves/{externalId}`). 스캔 시 소유권 검증, 미인증 시 로그인 리다이렉트.

### 2.8 i18n — ✅ 완료
- 5개 번들: `messages.properties`(ko, 기본), `messages_en`, `messages_ja`, `messages_zh_CN`, `messages_mn`. `CookieLocaleResolver` + `?lang=` 즉시 전환. 열거형 라벨도 `enum.<Enum>.<CONST>` 키로 지역화.

---

## 3. 최근 설계 스펙 구현 상태

| 스펙 문서 | 상태 | 근거 |
|---|---|---|
| `superpowers/specs/2026-05-31-serial-lot-policy-design.md` | ✅ 완료 | `V6` 마이그레이션으로 별도 정책 테이블을 `items`에 통합, `uq_stocks_item_serial UNIQUE(item_id, serial_number)` 제약 추가, `SerialNumberGenerator`/`LotNumberGenerator` 구현 |
| `superpowers/specs/2026-06-01-inherited-pricing-design.md` + `superpowers/plans/2026-06-01-inherited-pricing.md` | ✅ 완료 (UI 소소 갭) | `SpaceMapper.xml:104`의 `COALESCE(SUM(COALESCE(st.price, i.price)), 0)` 집계, `StockDetailDTO.itemPrice` 추가, `V7__backfill_inherited_pricing.sql` 백필 |

**상속 가격(inherited pricing) 모델:** `stocks.price`가 `NULL`이면 `items.price`를 동적 상속하고, 명시 설정 시 개별 override. 유효가격은 `COALESCE(stocks.price, items.price)`로 계산. 모든 가격은 KRW 정수(`NUMERIC(12,0)`).

- **잔여 UI 갭(Low):** 상세 행 템플릿에서 개별가격은 "(개별가격)" 라벨을 표시하나, 상속가격에는 설계서가 의도한 "(상속)" 표식이 없어 일반 품목가로만 보인다. 기능 영향 없음, 표기 일관성 문제.

---

## 4. 테스트 / 빌드 / 설정 품질

### 4.1 테스트
- **규모:** 테스트 클래스 **43개**, `@Test` 메서드 **348개**.
- **구성:**
  - Mapper(`@MybatisTest` + H2 + `@Sql`): 9종(Box/Image/ItemLot/Item/Shelf/Space/Stock/StockTransaction/User).
  - Service(Mockito 단위): 다수(Box/Item/Shelf/Space/Stock/User, PasswordReset, Serial/Lot 생성기, ImageFileValidator, QrCode, 메일센더 등) + AI 서비스 3종.
  - Controller(MockMvc): 대부분의 컨트롤러 + 통합 1종(`QrControllerIntegrationTest`, `@SpringBootTest @Transactional`) + 백프레셔 테스트(`ImageControllerBackpressureTest`).
  - Configuration/DTO: 조건부 빈, `GlobalExceptionHandler`, `HtmxResponse`, `UserDTO` 등.
- **미커버(보강 후보):**
  - 매퍼: `ItemImageMapper`, `StockImageMapper`.
  - 서비스: `ItemLotService`, 스토리지 구현체, `PasswordResetTokenStore`, `GemmaVisionClient`, `ImageResizeService`.
  - 컨트롤러: `ItemLotController`, `PasswordResetController`.
- `@Disabled`/`@Ignore` 비활성 테스트 없음, 빈 catch 블록 없음, TODO/FIXME 주석 없음 → 코드 청결도 양호.

### 4.2 빌드
- 플러그인은 `java` / `org.springframework.boot` / `io.spring.dependency-management` 3개뿐. **품질 플러그인 전무**(JaCoCo·Spotless·Checkstyle·PMD·SpotBugs 미설정).

### 4.3 데이터베이스 / 설정
- 마이그레이션 `V1`~`V7` 존재. 테스트용 `src/test/resources/schema-test.sql`(약 7.8KB) 존재 — Flyway는 테스트에서 비활성, H2가 `schema-test.sql`을 직접 로드(스키마 변경 시 동기화 필수 관례).
- 프로파일: `application.properties`(기본) / `-local`(개발) / `-prod`(운영) / `-test`. **시크릿은 전부 env var 주입, 커밋된 자격증명 없음.**

---

## 5. 기술 부채 / 리스크

| # | 항목 | 심각도 | 내용 |
|---|---|---|---|
| 1 | **개발 인프라 부재** | High | `.github/workflows` 없음 → CI 없음. 커버리지/정적분석/포매터/pre-commit 훅 전무. 회귀·스타일 게이트가 사람 손에 의존. |
| 2 | `uploads/` 미 gitignore | Medium | 로컬 스토리지 폴백이 생성한 사용자 업로드 이미지 4개가 `uploads/images/`에 있으나 `.gitignore`에 없음 → 사용자 파일이 실수로 커밋될 위험. |
| 3 | 커밋 위생 | Medium | 동일 메시지 중복 커밋 `fbec246`/`eef21fa`(둘 다 "Refactor stock detail-row layout…")인데 실제 내용은 다름(후자는 inherited-pricing 전체 구현). 이력 가독성 저하. |
| 4 | 기존 리팩토링 부채 | Medium | `StockService` 비대화(God Object), 컨트롤러 검증/`Principal`/페이징 보일러플레이트 반복. 상세는 [`code-review-report.md`](./code-review-report.md). |
| 5 | Java 25 채택 | Low~Med | 최신 LTS 이후 버전 — 운영 검증·라이브러리/툴 지원이 뒤처질 수 있음. |
| 6 | AI 외부 의존 | Low | 이미지 분석이 Ollama/YOLO 가용성에 종속. 미가용 시 502 처리는 되어 있으나 핵심 경로는 아님. |
| 7 | 미커버 클래스 | Low | 4.1 참조. |

---

## 6. 개선 권고 (우선순위)

### High — 지금 바로
1. **CI 파이프라인 도입** — GitHub Actions로 `./gradlew build` + `test`를 PR/푸시 시 자동 실행. Java 25 toolchain 명시.
2. **JaCoCo 커버리지 측정** + 최소 임계치 게이트 추가(현재 커버리지 가시성 0).
3. **`uploads/` 를 `.gitignore`에 추가** 하고, 이미 추적되지 않았는지 확인(현재 untracked이나 무시 목록에도 없음).
4. **중복 커밋 정리** — `develop` 머지 전 squash/rebase로 메시지 정정.

### Medium — 가까운 시일
5. **누락 테스트 보강** — `ItemLotService`, `PasswordResetController`, `ItemLotController`, `ItemImageMapper`/`StockImageMapper`.
6. **정적분석/포매터 도입** — Spotless(google-java-format) + Checkstyle 또는 PMD로 스타일 자동 게이트.
7. **컨트롤러/서비스 보일러플레이트 제거** — `@AuthenticationPrincipal` 커스텀 리졸버, `PageResult` 팩토리/`PaginationHelper`, `StockService` 책임 분리(QueryService/검증 컴포넌트). ([`code-review-report.md`](./code-review-report.md) 권고와 연계)

### Low — 백로그
8. inherited-pricing 상세 행에 "(상속)" 라벨 추가(설계 일관성).
9. 사용자 프로필/닉네임 수정 엔드포인트.
10. 재고/품목 CSV·Excel 내보내기, 가격대 등 고급 검색 필터.

---

## 부록: 검증 메모
- 정량 수치는 `grep -rcE "@(Get|Post|Put|Patch|Delete)Mapping"`(엔드포인트 73), `grep -rh "@Test" | wc -l`(테스트 348), `find src/test -name "*Test*.java"`(클래스 43)로 집계한 **근사치**다.
- 버전·`COALESCE` 가격 집계(`SpaceMapper.xml:104`)·중복 커밋·`uploads/` 미무시·CI/품질 플러그인 부재는 직접 확인했다.
- 본 리포트는 코드 변경을 수반하지 않는 문서이므로 빌드/테스트 실행 검증은 적용하지 않았다.
