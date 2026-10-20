package com.agolock.identity

import com.agolock.support.AuthTestClient
import com.agolock.support.FakeMailConfiguration
import com.agolock.support.FakeMailPort
import com.agolock.support.TestcontainersConfiguration
import com.fasterxml.jackson.databind.ObjectMapper
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.web.client.TestRestTemplate
import org.springframework.context.annotation.Import
import org.springframework.http.HttpEntity
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.test.context.ActiveProfiles
import java.util.UUID

@SpringBootTest(
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
    properties = ["auth.signup.invite-required=false"],
)
@ActiveProfiles("test")
@Import(TestcontainersConfiguration::class, FakeMailConfiguration::class)
class AuthWebIntegrationTest
    @Autowired
    constructor(
        private val restTemplate: TestRestTemplate,
        private val objectMapper: ObjectMapper,
        private val fakeMailPort: FakeMailPort,
        private val jdbcTemplate: JdbcTemplate,
    ) {
        private lateinit var client: AuthTestClient

        @BeforeEach
        fun setUp() {
            client = AuthTestClient(restTemplate, objectMapper)
            fakeMailPort.messages.clear()
        }

        @Test
        fun `가입하면 Space와 세션이 생성되고 me가 조회된다`() {
            val email = email()
            val response = client.signup(email, PASSWORD, "지민")

            assertThat(response.statusCode).isEqualTo(HttpStatus.CREATED)
            assertThat(client.sessionCookieValue()).isNotNull()

            val me = client.get("/api/v1/auth/me")
            assertThat(me.statusCode).isEqualTo(HttpStatus.OK)
            val body = objectMapper.readTree(me.body)
            assertThat(body["user"]["email"].asText()).isEqualTo(email)
            assertThat(body["space"]["name"].asText()).isEqualTo("지민의 Space")
            assertThat(body["space"]["ownerUserId"].asText()).isEqualTo(body["user"]["id"].asText())
        }

        @Test
        fun `로그아웃하면 세션과 전체 세션이 무효화된다`() {
            val email = email()
            client.signup(email, PASSWORD, "지민")
            val secondClient = AuthTestClient(restTemplate, objectMapper)
            secondClient.login(email, PASSWORD)

            val logout = client.post("/api/v1/auth/logout", emptyMap<String, String>())

            assertThat(logout.statusCode).isEqualTo(HttpStatus.NO_CONTENT)
            assertThat(client.get("/api/v1/auth/me").statusCode).isEqualTo(HttpStatus.UNAUTHORIZED)
            assertThat(secondClient.get("/api/v1/auth/me").statusCode).isEqualTo(HttpStatus.UNAUTHORIZED)
        }

        @Test
        fun `로그인은 세션 ID를 회전하고 자격 증명 오류를 구분하지 않는다`() {
            val email = email()
            client.signup(email, PASSWORD, "지민")
            val before = client.sessionCookieValue()

            val login = client.login(email, PASSWORD)
            assertThat(login.statusCode).isEqualTo(HttpStatus.OK)
            assertThat(client.sessionCookieValue()).isNotNull().isNotEqualTo(before)

            val wrong = client.login(email, "wrong-password-123")
            assertThat(wrong.statusCode).isEqualTo(HttpStatus.UNAUTHORIZED)
            assertThat(objectMapper.readTree(wrong.body)["code"].asText()).isEqualTo("INVALID_CREDENTIALS")
        }

        @Test
        fun `최대 세션 수를 넘으면 가장 오래된 세션이 만료된다`() {
            val email = email()
            val clients = mutableListOf<AuthTestClient>()
            repeat(6) {
                val sessionClient = AuthTestClient(restTemplate, objectMapper)
                if (clients.isEmpty()) {
                    assertThat(sessionClient.signup(email, PASSWORD, "지민").statusCode)
                        .isEqualTo(HttpStatus.CREATED)
                } else {
                    assertThat(sessionClient.login(email, PASSWORD).statusCode).isEqualTo(HttpStatus.OK)
                }
                clients += sessionClient
            }

            assertThat(clients.first().get("/api/v1/auth/me").statusCode).isEqualTo(HttpStatus.UNAUTHORIZED)
            assertThat(clients.last().get("/api/v1/auth/me").statusCode).isEqualTo(HttpStatus.OK)
        }

        @Test
        fun `CSRF 토큰 없이 상태 변경 요청을 보내면 403이다`() {
            val headers = HttpHeaders().apply { contentType = MediaType.APPLICATION_JSON }
            val response =
                restTemplate.postForEntity(
                    "/api/v1/auth/signup",
                    HttpEntity(
                        objectMapper.writeValueAsString(
                            mapOf("email" to email(), "password" to PASSWORD, "displayName" to "지민"),
                        ),
                        headers,
                    ),
                    String::class.java,
                )

            assertThat(response.statusCode).isEqualTo(HttpStatus.FORBIDDEN)
            assertThat(objectMapper.readTree(response.body)["code"].asText()).isEqualTo("CSRF_INVALID")
        }

        @Test
        fun `중복 이메일은 409이고 다른 Space는 수정할 수 없다`() {
            val email = email()
            client.signup(email, PASSWORD, "지민")
            val other = AuthTestClient(restTemplate, objectMapper)
            val otherSignup = other.signup(email(), PASSWORD, "민지")
            val otherSpaceId = objectMapper.readTree(otherSignup.body)["space"]["id"].asText()
            val clientSignup = client.get("/api/v1/auth/me")
            val ownSpaceId = objectMapper.readTree(clientSignup.body)["space"]["id"].asText()

            val duplicate = AuthTestClient(restTemplate, objectMapper).signup(email, PASSWORD, "지민")
            assertThat(duplicate.statusCode).isEqualTo(HttpStatus.CONFLICT)

            val updateOther = client.patch("/api/v1/spaces/$otherSpaceId", mapOf("name" to "탈취"))
            assertThat(updateOther.statusCode).isEqualTo(HttpStatus.NOT_FOUND)

            val updateOwn = client.patch("/api/v1/spaces/$ownSpaceId", mapOf("name" to "내 방"))
            assertThat(updateOwn.statusCode).isEqualTo(HttpStatus.OK)
            assertThat(objectMapper.readTree(updateOwn.body)["name"].asText()).isEqualTo("내 방")
        }

        @Test
        fun `표시 이름과 Space 이름을 수정한다`() {
            client.signup(email(), PASSWORD, "지민")
            val me = objectMapper.readTree(client.get("/api/v1/auth/me").body)
            val spaceId = me["space"]["id"].asText()

            val profile = client.patch("/api/v1/auth/me", mapOf("displayName" to "새 이름"))
            assertThat(profile.statusCode).isEqualTo(HttpStatus.OK)
            assertThat(objectMapper.readTree(profile.body)["displayName"].asText()).isEqualTo("새 이름")

            val space = client.patch("/api/v1/spaces/$spaceId", mapOf("name" to "새 Space"))
            assertThat(space.statusCode).isEqualTo(HttpStatus.OK)
            assertThat(objectMapper.readTree(space.body)["name"].asText()).isEqualTo("새 Space")
        }

        @Test
        fun `soft delete된 이메일은 새 계정으로 다시 사용할 수 있다`() {
            val email = email()
            assertThat(client.signup(email, PASSWORD, "지민").statusCode).isEqualTo(HttpStatus.CREATED)
            jdbcTemplate.update(
                "UPDATE app_user SET deleted_at = now(), status = 'DELETED' WHERE email = ?",
                email,
            )

            val again = AuthTestClient(restTemplate, objectMapper).signup(email, PASSWORD, "새 사용자")

            assertThat(again.statusCode).isEqualTo(HttpStatus.CREATED)
        }

        @Test
        fun `이메일 대소문자가 달라도 중복으로 판정한다`() {
            val email = email()
            assertThat(client.signup(email, PASSWORD, "지민").statusCode).isEqualTo(HttpStatus.CREATED)

            val duplicate = AuthTestClient(restTemplate, objectMapper).signup(email.uppercase(), PASSWORD, "지민")

            assertThat(duplicate.statusCode).isEqualTo(HttpStatus.CONFLICT)
        }

        @Test
        fun `비밀번호 재설정 메일을 보내고 토큰은 한 번만 사용할 수 있다`() {
            val email = email()
            client.signup(email, PASSWORD, "지민")

            val request = client.post("/api/v1/auth/password-reset/request", mapOf("email" to email))
            assertThat(request.statusCode).isEqualTo(HttpStatus.ACCEPTED)
            assertThat(fakeMailPort.messages).hasSize(1)
            val token = fakeMailPort.messages.single().token

            val confirm =
                client.post(
                    "/api/v1/auth/password-reset/confirm",
                    mapOf("token" to token, "newPassword" to NEW_PASSWORD),
                )
            assertThat(confirm.statusCode).isEqualTo(HttpStatus.NO_CONTENT)
            assertThat(client.get("/api/v1/auth/me").statusCode).isEqualTo(HttpStatus.UNAUTHORIZED)

            val reused =
                client.post(
                    "/api/v1/auth/password-reset/confirm",
                    mapOf("token" to token, "newPassword" to "another-password-123"),
                )
            assertThat(reused.statusCode).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY)

            assertThat(client.login(email, PASSWORD).statusCode).isEqualTo(HttpStatus.UNAUTHORIZED)
            assertThat(client.login(email, NEW_PASSWORD).statusCode).isEqualTo(HttpStatus.OK)
        }

        @Test
        fun `존재하지 않는 이메일의 재설정 요청도 202이다`() {
            val response =
                client.post(
                    "/api/v1/auth/password-reset/request",
                    mapOf("email" to "unknown-${UUID.randomUUID()}@example.com"),
                )

            assertThat(response.statusCode).isEqualTo(HttpStatus.ACCEPTED)
            assertThat(fakeMailPort.messages).isEmpty()
        }

        private fun email(): String = "user-${UUID.randomUUID()}@example.com"

        private companion object {
            const val PASSWORD = "password-1234"
            const val NEW_PASSWORD = "new-password-1234"
        }
    }
