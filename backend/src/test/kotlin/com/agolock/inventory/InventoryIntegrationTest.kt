package com.agolock.inventory

import com.agolock.identity.service.InMemoryRateLimiter
import com.agolock.identity.service.LoginAttemptService
import com.agolock.support.AuthTestClient
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
@Import(TestcontainersConfiguration::class)
class InventoryIntegrationTest
    @Autowired
    constructor(
        private val restTemplate: TestRestTemplate,
        private val objectMapper: ObjectMapper,
        private val rateLimiter: InMemoryRateLimiter,
        private val loginAttempts: LoginAttemptService,
    ) {
        private lateinit var client: AuthTestClient

        @BeforeEach
        fun setUp() {
            rateLimiter.clear()
            loginAttempts.clear()
            client = AuthTestClient(restTemplate, objectMapper)
            client.signup("inventory-${UUID.randomUUID()}@example.com", "password-1234", "지민")
        }

        @Test
        @Suppress("LongMethod")
        fun `카테고리 Item Inventory를 만들고 오버라이드와 이동을 처리한다`() {
            val room = json(client.post("/api/v1/rooms", mapOf("name" to "거실")).body)
            val shelf = json(client.post("/api/v1/rooms/${room["id"].asText()}/shelves", mapOf("name" to "책장")).body)
            val category =
                json(
                    client
                        .post(
                            "/api/v1/categories",
                            mapOf("name" to "도서", "color" to "#ff5a1f"),
                        ).body,
                )
            val item =
                json(
                    client
                        .post(
                            "/api/v1/items",
                            mapOf(
                                "name" to "책",
                                "description" to "마스터 설명",
                                "categoryId" to category["id"].asText(),
                                "priceAmount" to 12000,
                            ),
                        ).body,
                )
            assertThat(item["currency"].asText()).isEqualTo("KRW")

            val createdInventory =
                json(
                    client
                        .post(
                            "/api/v1/inventories",
                            mapOf("itemId" to item["id"].asText(), "roomId" to room["id"].asText()),
                        ).body,
                )
            val inventoryId = createdInventory["id"].asText()
            assertThat(createdInventory["displayName"].asText()).isEqualTo("책")
            assertThat(createdInventory["status"].asText()).isEqualTo("STORED")
            assertThat(createdInventory["location"]["type"].asText()).isEqualTo("ROOM")

            val updated =
                client.patch(
                    "/api/v1/inventories/$inventoryId",
                    mapOf(
                        "nameOverride" to "내 책",
                        "status" to "IN_USE",
                        "version" to 0,
                    ),
                )
            assertThat(updated.statusCode).isEqualTo(HttpStatus.OK)
            assertThat(json(updated.body)["displayName"].asText()).isEqualTo("내 책")
            assertThat(json(updated.body)["status"].asText()).isEqualTo("IN_USE")

            val moved =
                client.patch(
                    "/api/v1/inventories/$inventoryId/location",
                    mapOf("shelfId" to shelf["id"].asText(), "version" to 1),
                )
            assertThat(moved.statusCode).isEqualTo(HttpStatus.OK)
            assertThat(json(moved.body)["location"]["type"].asText()).isEqualTo("SHELF")

            val movements = json(client.get("/api/v1/inventories/$inventoryId/movements").body)
            assertThat(movements.size()).isEqualTo(1)
            assertThat(movements[0]["from"]["type"].asText()).isEqualTo("ROOM")
            assertThat(movements[0]["to"]["type"].asText()).isEqualTo("SHELF")

            assertThat(client.delete("/api/v1/rooms/${room["id"].asText()}").statusCode)
                .isEqualTo(HttpStatus.CONFLICT)
            assertThat(client.delete("/api/v1/shelves/${shelf["id"].asText()}").statusCode)
                .isEqualTo(HttpStatus.CONFLICT)
            assertThat(client.delete("/api/v1/inventories/$inventoryId").statusCode)
                .isEqualTo(HttpStatus.NO_CONTENT)
        }

        @Test
        fun `Shelf와 Box 위치와 폐기 확인을 처리한다`() {
            val room = json(client.post("/api/v1/rooms", mapOf("name" to "거실")).body)
            val shelf = json(client.post("/api/v1/rooms/${room["id"].asText()}/shelves", mapOf("name" to "책장")).body)
            val box = json(client.post("/api/v1/shelves/${shelf["id"].asText()}/boxes", mapOf("name" to "상자")).body)
            val item = json(client.post("/api/v1/items", mapOf("name" to "책")).body)

            val shelfInventory =
                json(
                    client
                        .post(
                            "/api/v1/inventories",
                            mapOf("itemId" to item["id"].asText(), "shelfId" to shelf["id"].asText()),
                        ).body,
                )
            assertThat(shelfInventory["location"]["type"].asText()).isEqualTo("SHELF")
            val boxInventory =
                client.post(
                    "/api/v1/inventories",
                    mapOf("itemId" to item["id"].asText(), "boxId" to box["id"].asText()),
                )
            assertThat(boxInventory.statusCode).isEqualTo(HttpStatus.CREATED)

            val moved =
                client.patch(
                    "/api/v1/inventories/${shelfInventory["id"].asText()}/location",
                    mapOf("boxId" to box["id"].asText(), "version" to 0),
                )
            assertThat(moved.statusCode).isEqualTo(HttpStatus.OK)
            assertThat(json(moved.body)["location"]["type"].asText()).isEqualTo("BOX")

            val inventoryId = shelfInventory["id"].asText()
            assertThat(
                client
                    .patch(
                        "/api/v1/inventories/$inventoryId",
                        mapOf("status" to "DISPOSED", "version" to 1),
                    ).statusCode,
            ).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY)
            val disposed =
                client.patch(
                    "/api/v1/inventories/$inventoryId",
                    mapOf("status" to "DISPOSED", "confirmDisposal" to true, "version" to 1),
                )
            assertThat(disposed.statusCode).isEqualTo(HttpStatus.OK)
            val defaultList = json(client.get("/api/v1/inventories").body)
            assertThat(defaultList["items"].any { it["id"].asText() == inventoryId }).isFalse()
            val disposedList = json(client.get("/api/v1/inventories?status=DISPOSED").body)
            assertThat(disposedList["items"].any { it["id"].asText() == inventoryId }).isTrue()
        }

        @Test
        fun `카테고리 중복과 사용 중 삭제를 차단한다`() {
            val category =
                json(client.post("/api/v1/categories", mapOf("name" to "도서")).body)
            val duplicate = client.post("/api/v1/categories", mapOf("name" to " 도서 "))
            assertThat(duplicate.statusCode).isEqualTo(HttpStatus.CONFLICT)
            assertThat(json(duplicate.body)["code"].asText()).isEqualTo("CATEGORY_ALREADY_EXISTS")

            client.post("/api/v1/items", mapOf("name" to "책", "categoryId" to category["id"].asText()))
            val deleted = client.delete("/api/v1/categories/${category["id"].asText()}")
            assertThat(deleted.statusCode).isEqualTo(HttpStatus.CONFLICT)
            assertThat(json(deleted.body)["code"].asText()).isEqualTo("CATEGORY_IN_USE")
        }

        private fun json(body: String?): JsonNode = objectMapper.readTree(body ?: "{}")
    }
