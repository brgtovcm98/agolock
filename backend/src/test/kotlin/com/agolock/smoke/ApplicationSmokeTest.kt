package com.agolock.smoke

import com.agolock.support.TestcontainersConfiguration
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.web.client.TestRestTemplate
import org.springframework.context.annotation.Import
import org.springframework.http.HttpStatus
import org.springframework.test.context.ActiveProfiles

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
@Import(TestcontainersConfiguration::class)
class ApplicationSmokeTest
    @Autowired
    constructor(
        private val restTemplate: TestRestTemplate,
    ) {
        @Test
        fun `애플리케이션 컨텍스트와 liveness가 동작한다`() {
            val response = restTemplate.getForEntity("/actuator/health/liveness", String::class.java)

            assertThat(response.statusCode).isEqualTo(HttpStatus.OK)
            assertThat(response.body).contains("\"status\":\"UP\"")
        }

        @Test
        fun `readiness가 DB와 RabbitMQ를 확인한다`() {
            val response = restTemplate.getForEntity("/actuator/health/readiness", String::class.java)

            assertThat(response.statusCode).isEqualTo(HttpStatus.OK)
            assertThat(response.body).contains("\"status\":\"UP\"")
        }

        @Test
        fun `info API가 환경과 요청 ID를 반환한다`() {
            val response = restTemplate.getForEntity("/api/v1/info", Map::class.java)

            assertThat(response.statusCode).isEqualTo(HttpStatus.OK)
            assertThat(response.body?.get("name")).isEqualTo("agolock")
            assertThat(response.body?.get("environment")).isEqualTo("test")
            assertThat(response.headers.getFirst("X-Request-Id")).isNotBlank()
        }
    }
