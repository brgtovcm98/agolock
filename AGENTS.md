# Repository Guidelines

## Project Structure & Module Organization

Agolock is a Java Spring Boot web application built with Gradle Kotlin DSL. Main code lives under `src/main/java/com/seu/seustock`: `controller` handles MVC and HTMX partial responses, `service` contains business logic (with `service/ai` for YOLO/Gemma image analysis), `mapper` defines MyBatis interfaces, `configuration` holds framework wiring (security, session, exception handling, type handlers), and `model` contains DTOs, forms, enums, and pagination types.

SQL mapper XML files are in `src/main/resources/mapper`, Flyway migrations in `src/main/resources/db/migration`, Thymeleaf pages and HTMX fragments in `src/main/resources/templates`, static CSS/JS/images in `src/main/resources/static`, and localization files in `messages*.properties` (ko, en, ja, mn). The CSS design token system lives in `static/css/ui.css` and provides `ui-*` utility classes.

Tests mirror main packages under `src/test/java`; H2 test configuration and schema are in `src/test/resources`.

## Build, Test, and Development Commands

Use the Gradle wrapper rather than a system Gradle install.

```bash
./gradlew bootRun --args='--spring.profiles.active=local'
./gradlew build
./gradlew test
./gradlew test --tests "com.seu.seustock.mapper.UserMapperTest"
./gradlew spotlessApply          # Auto-format Java with google-java-format
./gradlew jacocoTestReport       # Generate coverage report after tests
./gradlew sonar                  # Run SonarQube static analysis
```

`bootRun` starts the app on port `8080`. The `local` profile enables PostgreSQL, Redis, and MinIO via Docker Compose (`compose.yaml`). `build` compiles, runs tests with JaCoCo coverage, and packages the application. `test` runs JUnit 5 tests against H2 and does not require Docker.

## Coding Style & Naming Conventions

Spotless enforces google-java-format (1.27.0), removes unused imports, trims trailing whitespace, and adds a final newline. Use 4-space indentation for Java and lowercase package names. DTOs use Lombok `@Getter`, `@Setter`, and `@ToString`; avoid `@Data` for query result DTOs. External-facing identifiers in model attributes use short-form keys: `space`, `shelf`, `box`, `item` (not `spaceExternalId`, etc.). MyBatis XML namespaces must match mapper interface names, and database columns rely on underscore-to-camel-case mapping unless an existing mapper uses a `resultMap`.

## Testing Guidelines

Mapper tests use `@MybatisTest`, `@ActiveProfiles("test")`, `@Sql("classpath:schema-test.sql")`, and H2 PostgreSQL compatibility. Service tests use Mockito with `@ExtendWith(MockitoExtension.class)` and should cover validation, ownership checks, and stock ledger behavior. Controller tests extend `AbstractControllerTest` for Spring MVC with Spring Security support. Name tests after the unit under test, such as `StockServiceTest` or `UserMapperTest`, and run focused tests with `./gradlew test --tests "...ClassName"`.

## Commit & Pull Request Guidelines

Recent history uses Conventional Commit style subjects (e.g., `feat: ...`, `refactor: ...`, `fix: ...`, `style(ui): ...`, `docs: ...`). Keep commits focused on one feature or layer. Pull requests should include a short behavior summary, linked issue or plan when applicable, test commands run, screenshots for UI changes, and notes for schema, configuration, or localization updates.

## Security & Configuration Tips

Do not commit credentials. Sensitive values use environment variables (gated by `MAIL_TYPE`, `MAIL_USERNAME`, `MAIL_PASSWORD`, etc.). Feature flags in `application.properties` control optional capabilities: `seustock.features.lot-serial-enabled` gates serial/lot tracking UI, `seustock.datainit.enabled` controls seed data generation, and `seustock.ai.yolo.enabled` toggles YOLO preprocessing. Persistence changes require a new Flyway migration plus updates to `src/test/resources/schema-test.sql` and relevant mapper tests.

## Key Dependencies & Architecture Notes

- **Session**: Redis-backed via `RedisSerializer.json()`. Local profile can fall back to `none`.
- **Image Storage**: MinIO (default) with `LocalImageStorageService` fallback. Configured via `seustock.image-storage.type`.
- **AI Pipeline**: YOLO HTTP detection → Gemma/Ollama vision analysis, with graceful degradation when services are unavailable.
- **QR Codes**: ZXing-based; `app.qr-base-url` must not change after QR codes are printed.
- **Password Reset**: Redis-backed token store with configurable TTL and resend cooldown. Mail backend switches between `logging` (dev) and `smtp` (prod).
