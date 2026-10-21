# syntax=docker/dockerfile:1

FROM eclipse-temurin:21-jdk-jammy AS build
WORKDIR /workspace
COPY gradlew ./
COPY gradle ./gradle
COPY settings.gradle.kts build.gradle.kts ./
COPY backend/build.gradle.kts ./backend/build.gradle.kts
RUN chmod +x gradlew && ./gradlew :backend:dependencies --no-daemon >/dev/null 2>&1 || true
COPY backend/src ./backend/src
RUN ./gradlew :backend:bootJar --no-daemon -x test \
    && cp "$(find backend/build/libs -maxdepth 1 -name 'backend-*.jar' ! -name '*-plain.jar' | head -1)" /workspace/app.jar

FROM eclipse-temurin:21-jre-jammy
RUN apt-get update \
    && apt-get install -y --no-install-recommends curl \
    && rm -rf /var/lib/apt/lists/* \
    && groupadd --system app \
    && useradd --system --gid app --home /app app
WORKDIR /app
COPY --from=build /workspace/app.jar /app/app.jar
USER app
EXPOSE 8080
HEALTHCHECK --interval=30s --timeout=5s --start-period=45s --retries=3 \
  CMD curl -fsS http://127.0.0.1:8080/actuator/health/readiness || exit 1
ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75", "-jar", "/app/app.jar"]
