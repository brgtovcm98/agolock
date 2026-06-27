# Repository Guidelines

## Project Structure & Module Organization

SeuStock is a Java Spring Boot web application built with Gradle Kotlin DSL. Main code lives under `src/main/java/com/seu/seustock`: `controller` handles MVC requests, `service` contains business logic, `mapper` defines MyBatis interfaces, `configuration` holds framework wiring, and `model` contains DTOs, forms, enums, and pagination types. SQL mapper XML files are in `src/main/resources/mapper`, Flyway migrations in `src/main/resources/db/migration`, Thymeleaf pages and HTMX fragments in `src/main/resources/templates`, static CSS/JS/images in `src/main/resources/static`, and localization files in `messages*.properties`. Tests mirror main packages under `src/test/java`; H2 test configuration is in `src/test/resources`.

## Build, Test, and Development Commands

Use the Gradle wrapper rather than a system Gradle install.

```bash
./gradlew bootRun
./gradlew bootRun --args='--spring.profiles.active=local'
./gradlew build
./gradlew test
./gradlew test --tests "com.seu.seustock.mapper.UserMapperTest"
```

`bootRun` starts the app on port `8080`. The `local` profile is for local configuration. `build` compiles, tests, and packages the application. `test` runs JUnit 5 tests against H2 and does not require Docker. `compose.yaml` can provide local services such as PostgreSQL, MinIO, and Redis when needed.

## Coding Style & Naming Conventions

Use 4-space indentation for Java and lowercase package names. Follow established Spring MVC, service, and mapper patterns in nearby code. DTOs commonly use Lombok `@Getter`, `@Setter`, and `@ToString`; avoid introducing `@Data` for query result DTOs. External-facing identifiers should use `externalId` UUIDs rather than internal numeric `id` values. MyBatis XML namespaces must match mapper interface names, and database columns should rely on underscore-to-camel-case mapping unless an existing mapper uses a `resultMap`.

## Testing Guidelines

Mapper tests use `@MybatisTest`, `@ActiveProfiles("test")`, `@Sql("classpath:schema-test.sql")`, and H2 PostgreSQL compatibility. Service tests use Mockito with `@ExtendWith(MockitoExtension.class)` and should cover validation, ownership checks, and stock ledger behavior. Name tests after the unit under test, such as `StockServiceTest` or `UserMapperTest`, and run focused tests with `./gradlew test --tests "...ClassName"`.

## Commit & Pull Request Guidelines

Recent history uses concise imperative or Conventional Commit style subjects, for example `feat: ...`, `refactor: ...`, and `Initial commit: ...`. Keep commits focused on one feature or layer. Pull requests should include a short behavior summary, linked issue or plan when applicable, test commands run, screenshots for UI changes, and notes for schema, configuration, or localization updates.

## Security & Configuration Tips

Do not commit credentials. Local and production settings belong in `application-local.properties`, `application-prod.properties`, or environment variables. Persistence changes require a new Flyway migration plus updates to `src/test/resources/schema-test.sql` and relevant mapper tests.
