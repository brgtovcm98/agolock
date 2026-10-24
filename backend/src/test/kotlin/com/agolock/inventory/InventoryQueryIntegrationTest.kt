package com.agolock.inventory

import com.agolock.identity.service.InMemoryRateLimiter
import com.agolock.identity.service.LoginAttemptService
import com.agolock.messaging.outbox.OutboxRelayService
import com.agolock.support.AuthTestClient
import com.agolock.support.FakeMailConfiguration
import com.agolock.support.FakeMailPort
import com.agolock.support.TestcontainersConfiguration
import com.fasterxml.jackson.databind.JsonNode
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
import java.util.UUID

@SpringBootTest(
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
    properties = ["auth.signup.invite-required=false"],
)
@ActiveProfiles("test")
@Import(TestcontainersConfiguration::class, FakeMailConfiguration::class)
class InventoryQueryIntegrationTest
    @Autowired
    constructor(
        private val restTemplate: TestRestTemplate,
        private val objectMapper: ObjectMapper,
        private val rateLimiter: InMemoryRateLimiter,
        private val loginAttempts: LoginAttemptService,
        private val mailPort: FakeMailPort,
        private val outboxRelay: OutboxRelayService,
    ) {
        private lateinit var client: AuthTestClient

        @Autowired
        private lateinit var jdbc: org.springframework.jdbc.core.JdbcTemplate

        @BeforeEach
        fun setUp() {
            rateLimiter.clear()
            loginAttempts.clear()
            client = AuthTestClient(restTemplate, objectMapper)
            client.signup("inventory-${UUID.randomUUID()}@example.com", "password-1234", "지민")
        }

        @Test
        fun `대상 경로 검색은 조상 이름을 포함하고 삭제된 조상의 하위 위치는 제외한다`() {
            val room = json(client.post("/api/v1/rooms", mapOf("name" to "경로 부모")).body)
            val roomId = room["id"].asText()
            val shelf = json(client.post("/api/v1/rooms/$roomId/shelves", mapOf("name" to "선반")).body)
            val box = json(client.post("/api/v1/shelves/${shelf["id"].asText()}/boxes", mapOf("name" to "상자")).body)
            val item = json(client.post("/api/v1/items", mapOf("name" to "대상 물건")).body)
            val itemId = item["id"].asText()
            assertThat(client.get("/api/v1/items?q=${"x".repeat(201)}").statusCode).isEqualTo(HttpStatus.BAD_REQUEST)
            assertThat(client.get("/api/v1/items/$itemId/inventory-targets?q=${"x".repeat(201)}").statusCode)
                .isEqualTo(HttpStatus.BAD_REQUEST)
            val targets = json(client.get("/api/v1/items/$itemId/inventory-targets?q=경로").body)
            assertThat(targets["items"].map { it["type"].asText() }).containsExactly("ROOM", "SHELF", "BOX")
            assertThat(targets["items"][2]["id"].asText()).isEqualTo(box["id"].asText())
            assertThat(targets["items"][2]["path"].asText()).isEqualTo("경로 부모 > 선반 > 상자")
            jdbc.update("UPDATE room SET deleted_at = now() WHERE id = ?", UUID.fromString(roomId))
            assertThat(json(client.get("/api/v1/items/$itemId/inventory-targets").body)["items"].size()).isZero()
        }

        @Test
        fun `공유 카테고리는 보이는 참조만 허용하고 폐기 재고 기본 목록을 제외한다`() {
            val room = json(client.post("/api/v1/rooms", mapOf("name" to "공유 분류 방")).body)
            val category = json(client.post("/api/v1/categories", mapOf("name" to "보이는 분류")).body)
            val hidden = json(client.post("/api/v1/categories", mapOf("name" to "숨은 분류")).body)
            val hiddenItem = json(client.post("/api/v1/items", mapOf("name" to "숨은 물건")).body)
            val itemBody = mapOf("name" to "공유 물건", "categoryId" to category["id"].asText())
            val item = json(client.post("/api/v1/items", itemBody).body)
            val itemId = item["id"].asText()
            val inventory =
                json(
                    client.post("/api/v1/inventories", mapOf("itemId" to itemId, "roomId" to room["id"].asText())).body,
                )
            client.patch(
                "/api/v1/inventories/${inventory["id"].asText()}",
                mapOf(
                    "status" to "DISPOSED",
                    "confirmDisposal" to true,
                    "version" to 0,
                ),
            )
            val email = "category-guest-${UUID.randomUUID()}@example.com"
            val guest = invitedGuest(room["id"].asText(), email)
            val hiddenItemId = hiddenItem["id"].asText()
            assertThat(guest.get("/api/v1/items/$hiddenItemId/inventories").statusCode)
                .isEqualTo(HttpStatus.NOT_FOUND)
            assertThat(guest.get("/api/v1/categories?itemId=$hiddenItemId").statusCode)
                .isEqualTo(HttpStatus.NOT_FOUND)
            assertThat(json(guest.get("/api/v1/items/$itemId/inventory-targets").body)["items"].size()).isZero()
            val categories = json(guest.get("/api/v1/categories?itemId=$itemId").body)
            assertThat(categories["items"].map { it["id"].asText() }).containsExactly(category["id"].asText())
            assertThat(json(guest.get("/api/v1/items/$itemId/inventories").body)["items"].size()).isZero()
            assertThat(json(guest.get("/api/v1/items/$itemId/inventories?status=DISPOSED").body)["items"].size())
                .isEqualTo(1)
            val forbidden =
                guest.patch(
                    "/api/v1/inventories/${inventory["id"].asText()}",
                    mapOf(
                        "categoryIdOverride" to hidden["id"].asText(),
                        "version" to 1,
                    ),
                )
            assertThat(forbidden.statusCode).isEqualTo(HttpStatus.NOT_FOUND)
            val allowed =
                guest.patch(
                    "/api/v1/inventories/${inventory["id"].asText()}",
                    mapOf(
                        "categoryIdOverride" to category["id"].asText(),
                        "version" to 1,
                    ),
                )
            assertThat(allowed.statusCode).isEqualTo(HttpStatus.OK)
        }

        @Test
        fun `연결 재고와 대상은 101개를 누락 없이 페이지하고 필터 변경 커서를 거절한다`() {
            val room = json(client.post("/api/v1/rooms", mapOf("name" to "페이지 방")).body)
            val item = json(client.post("/api/v1/items", mapOf("name" to "페이지 물건")).body)
            val itemId = UUID.fromString(item["id"].asText())
            val spaceId = jdbc.queryForObject("SELECT space_id FROM item WHERE id = ?", UUID::class.java, itemId)!!
            seedPaginationResources(spaceId, itemId, UUID.fromString(room["id"].asText()))
            val inventories = json(client.get("/api/v1/items/$itemId/inventories?limit=100").body)
            assertThat(inventories["items"].size()).isEqualTo(100)
            val cursor = inventories["nextCursor"].asText()
            val last = json(client.get("/api/v1/items/$itemId/inventories?limit=100&cursor=$cursor").body)
            assertThat(last["items"].size()).isEqualTo(1)
            assertThat(last.hasNonNull("nextCursor")).isFalse()
            assertThat(client.get("/api/v1/items/$itemId/inventories?status=DISPOSED&cursor=$cursor").statusCode)
                .isEqualTo(HttpStatus.BAD_REQUEST)
            val targets = json(client.get("/api/v1/items/$itemId/inventory-targets?limit=100").body)
            assertThat(targets["items"].size()).isEqualTo(100)
            val targetLast =
                json(
                    client
                        .get(
                            "/api/v1/items/$itemId/inventory-targets?limit=100" +
                                "&cursor=${targets["nextCursor"].asText()}",
                        ).body,
                )
            assertThat(targetLast["items"].size()).isEqualTo(2)
            val categories = json(client.get("/api/v1/categories?itemId=$itemId&limit=100").body)
            assertThat(categories["items"].size()).isEqualTo(100)
            val categoryLast =
                json(
                    client
                        .get(
                            "/api/v1/categories?itemId=$itemId&limit=100&cursor=${categories["nextCursor"].asText()}",
                        ).body,
                )
            assertThat(categoryLast["items"].size()).isEqualTo(1)
        }

        @Test
        fun `영향 미리보기는 변경 필드 합집합과 폐기 재고를 집계한다`() {
            val room = json(client.post("/api/v1/rooms", mapOf("name" to "영향 방")).body)
            val item = json(client.post("/api/v1/items", mapOf("name" to "원본", "description" to "설명")).body)
            val itemId = item["id"].asText()
            val spaceId =
                jdbc.queryForObject(
                    "SELECT space_id FROM item WHERE id = ?",
                    UUID::class.java,
                    UUID.fromString(itemId),
                )!!
            jdbc.update(
                "INSERT INTO inventory(id,space_id,item_id,room_id,status,name_override,created_at,updated_at) " +
                    "VALUES (?,?,?,?,'DISPOSED','보호 이름',now(),now())",
                UUID.randomUUID(),
                spaceId,
                UUID.fromString(itemId),
                UUID.fromString(room["id"].asText()),
            )
            jdbc.update(
                "INSERT INTO inventory(id,space_id,item_id,room_id,status,description_override," +
                    "created_at,updated_at) " +
                    "VALUES (?,?,?,?,'STORED','',now(),now())",
                UUID.randomUUID(),
                spaceId,
                UUID.fromString(itemId),
                UUID.fromString(room["id"].asText()),
            )
            val impact =
                json(
                    client
                        .post(
                            "/api/v1/items/$itemId/update-impact",
                            mapOf("name" to "새 이름", "description" to "새 설명", "version" to 0),
                        ).body,
                )
            assertThat(impact["totalInventoryCount"].asInt()).isEqualTo(2)
            assertThat(impact["affectedInventoryCount"].asInt()).isEqualTo(2)
            assertThat(impact["fullyProtectedInventoryCount"].asInt()).isZero()
            assertThat(impact["fieldImpacts"].map { it["affectedInventoryCount"].asInt() }).containsExactly(1, 1)
            val noChange =
                json(
                    client.post("/api/v1/items/$itemId/update-impact", mapOf("name" to "원본", "version" to 0)).body,
                )
            assertThat(noChange["changedFields"].size()).isZero()
            assertThat(noChange["affectedInventoryCount"].asInt()).isZero()
        }

        private fun seedPaginationResources(
            spaceId: UUID,
            itemId: UUID,
            roomId: UUID,
        ) {
            repeat(101) { index ->
                jdbc.update(
                    "INSERT INTO inventory(id,space_id,item_id,room_id,status,created_at,updated_at) " +
                        "VALUES (?,?,?,?,'STORED',now(),now())",
                    UUID.randomUUID(),
                    spaceId,
                    itemId,
                    roomId,
                )
                jdbc.update(
                    "INSERT INTO room(id,space_id,name,created_at,updated_at) VALUES (?,?,?,now(),now())",
                    UUID.randomUUID(),
                    spaceId,
                    "페이지 $index",
                )
                jdbc.update(
                    "INSERT INTO category(id,space_id,name,normalized_name,sort_order,created_at,updated_at) " +
                        "VALUES (?,?,?,?,?,now(),now())",
                    UUID.randomUUID(),
                    spaceId,
                    "분류 $index",
                    "분류 $index",
                    index,
                )
            }
        }

        private fun invitedGuest(
            roomId: String,
            email: String,
        ): AuthTestClient {
            val invitation = mapOf("roomId" to roomId, "invitedEmail" to email, "permission" to "EDIT")
            assertThat(client.post("/api/v1/share-invitations", invitation).statusCode).isEqualTo(HttpStatus.CREATED)
            outboxRelay.relay()
            val guest = AuthTestClient(restTemplate, objectMapper)
            guest.signup(email, "password-1234", "공유 분류 게스트")
            val token = mailPort.invitationMessages.last { it.email == email }.token
            assertThat(guest.post("/api/v1/share-invitations/accept", mapOf("token" to token)).statusCode)
                .isEqualTo(HttpStatus.OK)
            return guest
        }

        private fun json(body: String?): JsonNode = objectMapper.readTree(body ?: "{}")
    }
