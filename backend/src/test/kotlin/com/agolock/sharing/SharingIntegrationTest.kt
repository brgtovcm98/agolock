@file:Suppress("MaxLineLength")

package com.agolock.sharing

import com.agolock.identity.service.InMemoryRateLimiter
import com.agolock.identity.service.LoginAttemptService
import com.agolock.support.AuthTestClient
import com.agolock.support.TestcontainersConfiguration
import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.web.client.TestRestTemplate
import org.springframework.context.annotation.Import
import org.springframework.http.HttpStatus
import org.springframework.test.context.ActiveProfiles
import java.util.UUID

@SpringBootTest(
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
    properties = ["auth.signup.invite-required=false"],
)
@ActiveProfiles("test")
@Import(TestcontainersConfiguration::class)
class SharingIntegrationTest
    @Autowired
    constructor(
        private val restTemplate: TestRestTemplate,
        private val objectMapper: ObjectMapper,
        private val rateLimiter: InMemoryRateLimiter,
        private val loginAttempts: LoginAttemptService,
    ) {
        @org.junit.jupiter.api.BeforeEach
        fun resetLimits() {
            rateLimiter.clear()
            loginAttempts.clear()
        }

        @Test
        @Suppress("LongMethod")
        fun `초대를 수락하면 공유 목록에 나타나고 철회하면 사라진다`() {
            val owner = AuthTestClient(restTemplate, objectMapper)
            val ownerEmail = "owner-${UUID.randomUUID()}@example.com"
            val guestEmail = "guest-${UUID.randomUUID()}@example.com"
            owner.signup(ownerEmail, "password-1234", "소유자")
            val room = json(owner.post("/api/v1/rooms", mapOf("name" to "공유 거실")).body)
            val roomId = room["id"].asText()
            val item = json(owner.post("/api/v1/items", mapOf("name" to "공유 물건")).body)
            val inventory =
                json(
                    owner
                        .post(
                            "/api/v1/inventories",
                            mapOf("itemId" to item["id"].asText(), "roomId" to roomId),
                        ).body,
                )

            val invite =
                json(
                    owner
                        .post(
                            "/api/v1/share-invitations",
                            mapOf(
                                "roomId" to roomId,
                                "invitedEmail" to guestEmail,
                                "permission" to "VIEW",
                            ),
                        ).body,
                )
            assertThat(invite["status"].asText()).isEqualTo("PENDING")

            val guest = AuthTestClient(restTemplate, objectMapper)
            guest.signup(guestEmail, "password-1234", "게스트")
            val received = json(guest.get("/api/v1/share-invitations/received").body)
            assertThat(received["items"].size()).isEqualTo(1)
            val invitationId = received["items"][0]["id"].asText()

            val accepted = guest.post("/api/v1/share-invitations/received/$invitationId/accept", mapOf("x" to "y"))
            assertThat(accepted.statusCode).isEqualTo(HttpStatus.OK)
            assertThat(json(accepted.body)["resourceName"].asText()).isEqualTo("공유 거실")

            val shared = json(guest.get("/api/v1/shared").body)
            assertThat(shared["items"].size()).isEqualTo(1)
            val shareId = shared["items"][0]["id"].asText()

            val sharedRoom = guest.get("/api/v1/shared/rooms/$roomId")
            assertThat(sharedRoom.statusCode).isEqualTo(HttpStatus.OK)
            assertThat(json(sharedRoom.body)["isShared"].asBoolean()).isTrue()
            assertThat(json(sharedRoom.body)["effectivePermission"].asText()).isEqualTo("VIEW")
            val sharedInventory = guest.get("/api/v1/shared/inventories/${inventory["id"].asText()}")
            assertThat(sharedInventory.statusCode).isEqualTo(HttpStatus.OK)
            assertThat(guest.get("/api/v1/rooms/$roomId").statusCode).isEqualTo(HttpStatus.OK)
            assertThat(guest.get("/api/v1/inventories/${inventory["id"].asText()}").statusCode)
                .isEqualTo(HttpStatus.OK)
            val alreadyShared =
                owner.post(
                    "/api/v1/share-invitations",
                    mapOf("roomId" to roomId, "invitedEmail" to guestEmail, "permission" to "EDIT"),
                )
            assertThat(alreadyShared.statusCode).isEqualTo(HttpStatus.CONFLICT)
            val invalidPermission = owner.patch("/api/v1/shares/$shareId", mapOf("permission" to "OWNER"))
            assertThat(invalidPermission.statusCode).isEqualTo(HttpStatus.BAD_REQUEST)

            assertThat(owner.delete("/api/v1/shares/$shareId").statusCode).isEqualTo(HttpStatus.NO_CONTENT)
            assertThat(json(guest.get("/api/v1/shared").body)["items"].size()).isEqualTo(0)
            assertThat(guest.get("/api/v1/shared/rooms/$roomId").statusCode).isEqualTo(HttpStatus.NOT_FOUND)
            assertThat(guest.get("/api/v1/rooms/$roomId").statusCode).isEqualTo(HttpStatus.NOT_FOUND)
        }

        @Test
        fun `다른 이메일의 초대는 수락할 수 없고 자기 자신은 초대할 수 없다`() {
            val owner = AuthTestClient(restTemplate, objectMapper)
            val ownerEmail = "owner-${UUID.randomUUID()}@example.com"
            owner.signup(ownerEmail, "password-1234", "소유자")
            val room = json(owner.post("/api/v1/rooms", mapOf("name" to "방")).body)
            val roomId = room["id"].asText()

            val self =
                owner.post(
                    "/api/v1/share-invitations",
                    mapOf("roomId" to roomId, "invitedEmail" to ownerEmail, "permission" to "VIEW"),
                )
            assertThat(self.statusCode).isEqualTo(HttpStatus.BAD_REQUEST)

            val invite =
                json(
                    owner
                        .post(
                            "/api/v1/share-invitations",
                            mapOf("roomId" to roomId, "invitedEmail" to "other-${UUID.randomUUID()}@example.com", "permission" to "EDIT"),
                        ).body,
                )
            val guest = AuthTestClient(restTemplate, objectMapper)
            guest.signup("guest-${UUID.randomUUID()}@example.com", "password-1234", "다른 사용자")
            val accepted = guest.post("/api/v1/share-invitations/received/${invite["id"].asText()}/accept", mapOf("x" to "y"))
            assertThat(accepted.statusCode).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY)
            assertThat(json(accepted.body)["code"].asText()).isEqualTo("INVITATION_EMAIL_MISMATCH")
        }

        private fun json(body: String?): JsonNode = objectMapper.readTree(body ?: "{}")
    }
