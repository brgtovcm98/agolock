import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.spring)
    alias(libs.plugins.spring.boot)
    alias(libs.plugins.spring.dependency.management)
    alias(libs.plugins.ktlint)
    alias(libs.plugins.detekt)
}

group = "com.agolock"
version = "0.1.0"
description = "AGO Lock backend bootstrap"

// Spring Boot의 BOM이 Kotlin 표준 라이브러리를 낮은 버전으로 고정하지 않도록
// 플러그인과 같은 버전을 명시합니다.
extra["kotlin.version"] = libs.versions.kotlin.get()

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(21))
    }
}

kotlin {
    compilerOptions {
        freeCompilerArgs.add("-Xjsr305=strict")
        jvmTarget.set(JvmTarget.JVM_21)
    }
}

dependencies {
    implementation(libs.spring.boot.starter.web)
    implementation(libs.spring.boot.starter.validation)
    implementation(libs.spring.boot.starter.actuator)
    implementation(libs.spring.boot.starter.security)
    implementation(libs.spring.boot.starter.amqp)
    implementation(libs.spring.boot.starter.mail)
    implementation(libs.spring.session.jdbc)
    implementation(libs.bouncycastle)
    implementation(libs.aws.s3)
    implementation(libs.aws.url.connection.client)
    implementation(libs.webp.imageio)
    implementation(libs.springdoc.openapi)
    implementation(libs.mybatis.spring.boot.starter)
    implementation(libs.logstash.logback.encoder)
    implementation("org.jetbrains.kotlin:kotlin-reflect")
    implementation("com.fasterxml.jackson.module:jackson-module-kotlin")

    implementation("org.flywaydb:flyway-core")
    implementation("org.flywaydb:flyway-database-postgresql")
    runtimeOnly("org.postgresql:postgresql")

    testImplementation(libs.spring.boot.starter.test)
    testImplementation(libs.spring.boot.testcontainers)
    testImplementation(libs.testcontainers.junit.jupiter)
    testImplementation(libs.testcontainers.postgresql)
    testImplementation(libs.testcontainers.rabbitmq)
    testImplementation(libs.spring.security.test)
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

tasks.test {
    useJUnitPlatform {
        excludeTags("contract", "search-perf")
    }
}

tasks.register<Test>("searchPerformanceTest") {
    description = "10,000 Item·50,000 Inventory 검색 성능과 실행 계획을 검증합니다."
    group = "verification"
    testClassesDirs =
        sourceSets.test
            .get()
            .output.classesDirs
    classpath = sourceSets.test.get().runtimeClasspath
    useJUnitPlatform { includeTags("search-perf") }
}

val contractTest by tasks.registering(Test::class) {
    description = "M0 계약 미리보기를 생성하는 테스트"
    group = "verification"
    testClassesDirs =
        sourceSets.test
            .get()
            .output.classesDirs
    classpath = sourceSets.test.get().runtimeClasspath
    useJUnitPlatform {
        includeTags("contract")
    }
    systemProperty(
        "contracts.dir",
        rootProject.layout.buildDirectory
            .dir("contracts")
            .get()
            .asFile.absolutePath,
    )
    shouldRunAfter(tasks.test)
}

tasks.register("generateOpenApiPreview") {
    description = "현재 구현된 컨트롤러에서 OpenAPI 미리보기를 생성합니다."
    group = "documentation"
    dependsOn(contractTest)
}

ktlint {
    filter {
        exclude { element -> element.file.path.contains("generated") }
    }
}

detekt {
    buildUponDefaultConfig = true
    parallel = true
}
