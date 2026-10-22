package com.agolock.common.config

import com.agolock.support.TestcontainersConfiguration
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.web.client.TestRestTemplate
import org.springframework.boot.test.web.server.LocalManagementPort
import org.springframework.context.annotation.Import
import org.springframework.http.HttpEntity
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.test.context.ActiveProfiles

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
@Import(TestcontainersConfiguration::class)
class SecurityConfigTest
    @Autowired
    constructor(
        private val restTemplate: TestRestTemplate,
        @LocalManagementPort private val managementPort: Int,
    ) {
        @Test
        fun `인증 없이 info API를 호출하면 401이다`() {
            val response = restTemplate.getForEntity("/api/v1/info", String::class.java)

            assertThat(response.statusCode).isEqualTo(HttpStatus.UNAUTHORIZED)
        }

        @Test
        fun `인증 없이 auth config를 호출하면 200이다`() {
            val response = restTemplate.getForEntity("/api/v1/auth/config", String::class.java)

            assertThat(response.statusCode).isEqualTo(HttpStatus.OK)
            assertThat(response.body).contains("inviteRequired")
        }

        @Test
        fun `인증 없이 rooms API를 호출하면 401이다`() {
            val response = restTemplate.getForEntity("/api/v1/rooms", String::class.java)

            assertThat(response.statusCode).isEqualTo(HttpStatus.UNAUTHORIZED)
        }

        @Test
        fun `liveness는 관리 포트에서 200이다`() {
            val response =
                restTemplate.getForEntity(
                    "http://127.0.0.1:$managementPort/actuator/health/liveness",
                    String::class.java,
                )

            assertThat(response.statusCode).isEqualTo(HttpStatus.OK)
            assertThat(response.body).contains("\"status\":\"UP\"")
        }

        @Test
        fun `CSRF 토큰 없는 로그인 POST는 403이다`() {
            val response =
                restTemplate.postForEntity(
                    "/api/v1/auth/login",
                    HttpEntity(LOGIN_BODY, jsonHeaders()),
                    String::class.java,
                )

            assertThat(response.statusCode).isEqualTo(HttpStatus.FORBIDDEN)
        }

        @Test
        fun `CSRF 토큰을 포함한 로그인 POST는 자격 증명 오류로 거절된다`() {
            val csrf = csrfTokenAndCookie()
            val headers =
                jsonHeaders().apply {
                    add(HttpHeaders.COOKIE, csrf.cookie)
                    add("X-XSRF-TOKEN", csrf.token)
                }
            val response =
                restTemplate.postForEntity(
                    "/api/v1/auth/login",
                    HttpEntity(LOGIN_BODY, headers),
                    String::class.java,
                )

            assertThat(response.statusCode).isIn(HttpStatus.BAD_REQUEST, HttpStatus.UNAUTHORIZED)
        }

        private fun csrfTokenAndCookie(): Csrf {
            val response = restTemplate.getForEntity("/api/v1/auth/csrf", Map::class.java)
            assertThat(response.statusCode).isEqualTo(HttpStatus.OK)
            val token = requireNotNull(response.body?.get("token") as? String) { "CSRF 토큰이 없습니다." }
            val cookie =
                requireNotNull(
                    response.headers[HttpHeaders.SET_COOKIE]
                        .orEmpty()
                        .map { it.substringBefore(";") }
                        .firstOrNull { it.startsWith("XSRF-TOKEN=") },
                ) { "XSRF-TOKEN 쿠키가 없습니다." }
            return Csrf(token, cookie)
        }

        private fun jsonHeaders(): HttpHeaders =
            HttpHeaders().apply {
                contentType = MediaType.APPLICATION_JSON
            }

        private data class Csrf(
            val token: String,
            val cookie: String,
        )

        private companion object {
            const val LOGIN_BODY = """{"email":"security-config-test@example.com","password":"wrong-password-123"}"""
        }
    }
