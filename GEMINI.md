# GEMINI.md - SeuStock Project Instructions

This file provides context and instructions for the **SeuStock** project, a web-based inventory management system.

## Project Overview
SeuStock은 **Team Ugui**에서 개발하는 개인/소규모 팀용 재고 관리 애플리케이션입니다. 공간 → 선반 → 박스로 이어지는 계층적 위치 구조를 통해 물품을 추적하며, HTMX 기반의 인터랙티브한 UI와 AI 기반 이미지 분석 기능을 제공합니다.

### Core Tech Stack
- **Language:** Java 25
- **Framework:** Spring Boot 4.0.6
- **UI:** Thymeleaf (SSR), HTMX 2 (Partial Updates), Tailwind CSS
- **Persistence:** MyBatis, Flyway (Migration), PostgreSQL (Prod/Local), H2 (Test)
- **Session/Cache:** Redis (Spring Session)
- **Object Storage:** MinIO (Local fallback available)
- **AI/ML:** Spring AI (Ollama/Gemma), YOLO (HTTP API)
- **Tools:** Gradle, Docker Compose, ZXing (QR)

---

## Architecture & Design Patterns

### 1. Rendering Strategy
- **Standard SSR:** Full-page navigations and initial loads use standard Thymeleaf SSR.
- **HTMX Partial Updates:** Used for modals, inline row editing (Items/Spaces/Shelves/Boxes), delete confirmations, and toast notifications.
- **Fragments:** HTMX responses are located in `templates/<entity>/fragments/`. Controllers return these specific fragments when an HTMX header is present or for specific HTMX-targeted routes.

### 2. Identity & Security
- **Dual IDs:** Every table uses a `serial` `id` for internal FK joins and a `UUID` `external_id` for all external references (URLs, DTOs). **Never** expose internal `id`s.
- **Ownership Pattern:** All user-owned data access must be validated. Fetch the entity by UUID → fetch owning user → compare against `Principal.getName()` → throw `SecurityException` if they don't match.
- **Authentication:** Spring Security manages login/register. Routes are protected by default. Controllers use `Principal principal` to identify the user.

### 3. Inventory Model
- **Items:** The "Catalog" entries. No location or quantity data.
- **Stocks:** The physical unit. **One row = One physical unit**. No quantity column; counts are derived.
- **VerifiedLocation:** `StockService` uses `resolveVerifiedLocation()` to validate the full Space → Shelf → Box hierarchy in one atomic step.
- **Transactions:** Every stock change (IN, OUT, MOVE, ADJUST) must record an entry in `stock_transactions` within the same `@Transactional` block.

---

## Development Conventions

### MyBatis & Database
- **Naming:** `snake_case` in DB maps to `camelCase` in DTOs via `map-underscore-to-camel-case=true`.
- **Mappers:** SQL in `src/main/resources/mapper/`, interfaces in `com.seu.seustock.mapper`.
- **Type Handlers:** `UUIDTypeHandler` is used for UUID mapping.
- **Migrations:** Use Flyway (`src/main/resources/db/migration`). Do **not** use `init.sql` for schema changes.

### AI Image Analysis
- **Pipeline:** Resize (1024px) → YOLO (Optional Detect) → Gemma (Ollama/Spring AI) → ImageAnalysisDTO.
- **Interface:** `ImageAnalysisService` is the entry point.
- **UI:** Triggered via `image-upload.js` `onImageReady` callback in modals.

### Image Storage
- **Primary:** `MinioImageStorageService` (MinIO).
- **Fallback:** `LocalImageStorageService` (Local disk).
- **Deduplication:** Managed via `contentHash` (SHA-256).

---

## Key Commands

### Environment
```bash
# Start infrastructure (PostgreSQL, MinIO, Redis)
docker compose up -d

# Stop infrastructure
docker compose down
```

### Build & Run
```bash
# Run application with 'local' profile
./gradlew bootRun --args='--spring.profiles.active=local'

# Build project
./gradlew build
```

### Testing
```bash
# Run all tests (uses H2)
./gradlew test

# Run specific tests
./gradlew test --tests "com.seu.seustock.mapper.*"
./gradlew test --tests "com.seu.seustock.service.*"
```

---

## Project Structure
- `src/main/java/com/seu/seustock/configuration`: App, Security, MinIO, and MVC config.
- `src/main/java/com/seu/seustock/controller`: Thymeleaf/HTMX controllers.
- `src/main/java/com/seu/seustock/mapper`: MyBatis interfaces.
- `src/main/java/com/seu/seustock/model`: DTOs, Enums, Forms, and Pagination models.
- `src/main/java/com/seu/seustock/service`: Core logic and AI pipelines.
- `src/main/resources/db/migration`: Flyway SQL files.
- `src/main/resources/mapper`: MyBatis XML files.
- `src/main/resources/templates`: Thymeleaf templates and fragments.
- `src/test/resources/schema-test.sql`: H2-compatible schema for tests.

---

## Development Workflow
1. **Infrastructure:** Ensure Docker is running and `docker compose up -d` is executed.
2. **Schema Changes:** Add a new SQL file to `db/migration` and update `src/test/resources/schema-test.sql` for H2 compatibility.
3. **Logic:** Implement mappers (interface + XML), then service (with ownership checks), then controllers.
4. **UI:** Create/Update Thymeleaf templates. Use HTMX for interactive elements like modals or inline edits.
5. **Test:** Write Mapper tests (slice tests) and Service tests (mock tests). Use `./gradlew test` for verification.

---

## Deployment & Production Considerations
- **Branding:** 모든 공개 UI와 문서에는 **Team Ugui** 저작권 표기를 유지합니다.
- **Secrets:** 운영 환경(`prod`)에서는 환경 변수를 통해 민감 정보를 주입합니다. `.env` 파일이나 CI/CD 시크릿을 활용하세요.
- **Infrastructure:** Docker Compose를 활용하여 배포하되, 운영 환경에서는 Redis와 MinIO(또는 S3)를 필수로 구성해야 합니다.
- **SSL:** Nginx 등의 리버스 프록시를 통해 HTTPS를 필수로 적용해야 합니다.
