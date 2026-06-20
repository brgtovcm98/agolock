# Repository Guidelines

## Project Structure & Module Organization

SeuStock is a Java 25 Spring Boot 4 web application built with Gradle Kotlin DSL. Main code lives under `src/main/java/com/seu/seustock`: `controller` handles MVC requests, `service` holds business logic, `mapper` contains MyBatis interfaces, `configuration` contains framework wiring, and `model` contains DTOs, forms, enums, and pagination types. SQL mapper XML files are in `src/main/resources/mapper`, Flyway migrations in `src/main/resources/db/migration`, Thymeleaf pages and HTMX fragments in `src/main/resources/templates`, static assets in `src/main/resources/static`, and messages in `messages*.properties`. Tests mirror main packages under `src/test/java`; H2 setup is in `src/test/resources`.

## Build, Test, and Development Commands

Use the Gradle wrapper, not a system Gradle install.

```bash
./gradlew bootRun
./gradlew bootRun --args='--spring.profiles.active=local'
./gradlew build
./gradlew test
./gradlew test --tests "com.seu.seustock.mapper.UserMapperTest"
```

`bootRun` starts the app on port `8080`; Docker Compose support can start PostgreSQL, MinIO, and Redis from `compose.yaml` when Docker is available. `build` compiles, tests, and packages the app. `test` runs JUnit 5 against H2 and does not require Docker.

## Coding Style & Naming Conventions

Use 4-space indentation for Java and keep package names lowercase. Follow established Spring patterns in nearby code. DTOs use Lombok `@Getter`, `@Setter`, and `@ToString`; do not introduce `@Data` for query result DTOs. External-facing references use `externalId` UUIDs, never internal numeric `id` values. MyBatis XML namespaces must match mapper interface names, and columns should rely on underscore-to-camel-case mapping unless a mapper already uses a `resultMap`.

## Testing Guidelines

Mapper tests use `@MybatisTest`, `@ActiveProfiles("test")`, `@Sql("classpath:schema-test.sql")`, and H2 PostgreSQL compatibility. Service tests use Mockito with `@ExtendWith(MockitoExtension.class)` and should cover ownership checks, validation, and ledger behavior. Name tests after the unit under test, for example `StockServiceTest` or `StockMapperTest`, and run focused tests with `./gradlew test --tests "...ClassName"`.

## Commit & Pull Request Guidelines

Recent history uses imperative, sentence-style commit messages such as `Add stock status change modal` and `Fix stock keep release: select is_kept in StockResultMap queries`. Keep commits focused and mention the affected feature or layer. Pull requests should include a short behavior summary, linked issue or plan when applicable, test commands run, screenshots for UI changes, and notes for schema, configuration, or localization updates.

## Security & Configuration Tips

Do not commit credentials. Local and production settings belong in `application-local.properties`, `application-prod.properties`, or environment variables. When changing persistence, add a new Flyway migration and update `src/test/resources/schema-test.sql` plus relevant mapper tests.
