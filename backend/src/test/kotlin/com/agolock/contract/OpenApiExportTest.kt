package com.agolock.contract

import com.agolock.support.TestcontainersConfiguration
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.web.client.TestRestTemplate
import org.springframework.context.annotation.Import
import org.springframework.http.HttpStatus
import org.springframework.test.context.ActiveProfiles
import java.nio.file.Files
import java.nio.file.Path

/**
 * M1·M2 계약 미리보기용 테스트입니다.
 *
 * 일반 test task에서는 제외되고 `./gradlew :backend:generateOpenApiPreview`에서만 실행됩니다.
 * 결과는 docs/generated를 덮어쓰지 않고 루트 build/contracts에 기록합니다.
 */
@Tag("contract")
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
@Import(TestcontainersConfiguration::class)
class OpenApiExportTest
    @Autowired
    constructor(
        private val restTemplate: TestRestTemplate,
    ) {
        @Test
        fun `현재 컨트롤러의 OpenAPI를 build contracts에 기록한다`() {
            val response = restTemplate.getForEntity("/v3/api-docs", String::class.java)

            assertThat(response.statusCode).isEqualTo(HttpStatus.OK)
            assertThat(response.body).contains("/api/v1/info")

            val target =
                Path.of(
                    System.getProperty("contracts.dir") ?: "build/contracts",
                    "openapi.json",
                )
            Files.createDirectories(target.parent)
            Files.writeString(target, response.body)
        }
    }
