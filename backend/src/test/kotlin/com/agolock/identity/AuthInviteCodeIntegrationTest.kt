package com.agolock.identity

import com.agolock.identity.service.InviteCodeService
import com.agolock.support.AuthTestClient
import com.agolock.support.FakeMailConfiguration
import com.agolock.support.TestcontainersConfiguration
import com.fasterxml.jackson.databind.ObjectMapper
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.web.client.TestRestTemplate
import org.springframework.context.annotation.Import
import org.springframework.http.HttpStatus
import org.springframework.test.context.ActiveProfiles
import java.time.OffsetDateTime
import java.util.UUID

@SpringBootTest(
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
    properties = ["auth.signup.invite-required=true"],
)
@ActiveProfiles("test")
@Import(TestcontainersConfiguration::class, FakeMailConfiguration::class)
class AuthInviteCodeIntegrationTest
    @Autowired
    constructor(
        private val restTemplate: TestRestTemplate,
        private val objectMapper: ObjectMapper,
        private val inviteCodeService: InviteCodeService,
    ) {
        private lateinit var client: AuthTestClient

        @BeforeEach
        fun setUp() {
            client = AuthTestClient(restTemplate, objectMapper)
        }

        @Test
        fun `초대코드가 없거나 잘못되면 가입할 수 없다`() {
            val missing = client.signup(email(), PASSWORD, "지민")
            assertThat(missing.statusCode).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY)
            assertThat(objectMapper.readTree(missing.body)["code"].asText()).isEqualTo("INVITE_CODE_REQUIRED")

            val invalid = client.signup(email(), PASSWORD, "지민", "AGO-INVALID-CODE")
            assertThat(invalid.statusCode).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY)
            assertThat(objectMapper.readTree(invalid.body)["code"].asText()).isEqualTo("INVITE_CODE_INVALID")
        }

        @Test
        fun `초대코드는 한 번만 사용할 수 있다`() {
            val created = inviteCodeService.create("test", maxUses = 1, expiresAt = null, createdBy = null)

            val first = client.signup(email(), PASSWORD, "지민", created.rawCode)
            assertThat(first.statusCode).isEqualTo(HttpStatus.CREATED)

            val second = client.signup(email(), PASSWORD, "민지", created.rawCode)
            assertThat(second.statusCode).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY)
            assertThat(objectMapper.readTree(second.body)["code"].asText()).isEqualTo("INVITE_CODE_INVALID")
        }

        @Test
        fun `만료되거나 철회된 초대코드는 사용할 수 없다`() {
            val expired =
                inviteCodeService.create(
                    "expired",
                    maxUses = 1,
                    expiresAt = OffsetDateTime.now().minusDays(1),
                    createdBy = null,
                )
            assertThat(client.signup(email(), PASSWORD, "지민", expired.rawCode).statusCode)
                .isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY)

            val revoked = inviteCodeService.create("revoked", maxUses = 1, expiresAt = null, createdBy = null)
            inviteCodeService.revoke(revoked.inviteCodeId)
            assertThat(client.signup(email(), PASSWORD, "민지", revoked.rawCode).statusCode)
                .isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY)
        }

        @Test
        fun `초대코드 형식은 정규화되어 하이픈 없이도 사용할 수 있다`() {
            val created = inviteCodeService.create("normalize", maxUses = 1, expiresAt = null, createdBy = null)
            val normalized = created.rawCode.replace("-", "")

            val response = client.signup(email(), PASSWORD, "지민", normalized)

            assertThat(response.statusCode).isEqualTo(HttpStatus.CREATED)
        }

        private fun email(): String = "invite-${UUID.randomUUID()}@example.com"

        private companion object {
            const val PASSWORD = "password-1234"
        }
    }
