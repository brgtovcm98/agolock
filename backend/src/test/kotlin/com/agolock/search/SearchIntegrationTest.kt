@file:Suppress("MaxLineLength")

package com.agolock.search

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
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.web.client.TestRestTemplate
import org.springframework.context.annotation.Import
import org.springframework.http.HttpStatus
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.test.context.ActiveProfiles
import java.util.UUID

@SpringBootTest(
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
    properties = ["auth.signup.invite-required=false"],
)
@ActiveProfiles("test")
@Import(TestcontainersConfiguration::class, FakeMailConfiguration::class)
class SearchIntegrationTest
    @Autowired
    constructor(
        private val restTemplate: TestRestTemplate,
        private val objectMapper: ObjectMapper,
        private val rateLimiter: InMemoryRateLimiter,
        private val loginAttempts: LoginAttemptService,
        private val mailPort: FakeMailPort,
        private val outboxRelay: OutboxRelayService,
    ) {
        @Autowired
        private lateinit var jdbc: JdbcTemplate

        private lateinit var client: AuthTestClient

        @BeforeEach
        fun setUp() {
            rateLimiter.clear()
            loginAttempts.clear()
            client = AuthTestClient(restTemplate, objectMapper)
            client.signup("search-${UUID.randomUUID()}@example.com", "password-1234", "검색 사용자")
        }

        @Test
        fun `이름과 위치로 검색하고 최근 검색과 조회를 기록한다`() {
            val room = json(client.post("/api/v1/rooms", mapOf("name" to "거실")).body)
            val shelf =
                json(
                    client
                        .post("/api/v1/rooms/${room["id"].asText()}/shelves", mapOf("name" to "책장"))
                        .body,
                )
            val box =
                json(
                    client
                        .post("/api/v1/shelves/${shelf["id"].asText()}/boxes", mapOf("name" to "2번 상자"))
                        .body,
                )
            val item =
                json(
                    client
                        .post(
                            "/api/v1/items",
                            mapOf("name" to "무선 이어폰", "description" to "여행용"),
                        ).body,
                )
            client.post(
                "/api/v1/inventories",
                mapOf("itemId" to item["id"].asText(), "boxId" to box["id"].asText()),
            )

            val byName = json(client.get("/api/v1/search?q=이어폰").body)
            assertThat(byName["items"].size()).isEqualTo(1)
            assertThat(byName["items"][0]["itemName"].asText()).isEqualTo("무선 이어폰")
            assertThat(byName["items"][0]["inventories"][0]["locationPath"].asText())
                .isEqualTo("거실 > 책장 > 2번 상자")

            val byLocation = json(client.get("/api/v1/search?q=책장").body)
            assertThat(byLocation["items"].size()).isEqualTo(1)

            val recent = json(client.get("/api/v1/search/recent").body)
            assertThat(recent["items"].size()).isEqualTo(2)
            assertThat(recent["items"][0]["query"].asText()).isEqualTo("책장")

            val view = client.post("/api/v1/items/${item["id"].asText()}/view", mapOf("x" to "y"))
            assertThat(view.statusCode).isEqualTo(HttpStatus.NO_CONTENT)
            val frequent = json(client.get("/api/v1/search/frequent").body)
            assertThat(frequent["items"].size()).isEqualTo(1)
            assertThat(frequent["items"][0]["viewCount"].asLong()).isEqualTo(1)

            val clear = client.delete("/api/v1/search/recent")
            assertThat(clear.statusCode).isEqualTo(HttpStatus.NO_CONTENT)
            assertThat(json(client.get("/api/v1/search/recent").body)["items"].size()).isZero()
        }

        @Test
        fun `DISPOSED는 기본 검색에서 제외하고 위치 필터를 적용한다`() {
            val room = json(client.post("/api/v1/rooms", mapOf("name" to "창고")).body)
            val shelf =
                json(
                    client
                        .post("/api/v1/rooms/${room["id"].asText()}/shelves", mapOf("name" to "선반"))
                        .body,
                )
            val item = json(client.post("/api/v1/items", mapOf("name" to "드릴")).body)
            val inventory =
                json(
                    client
                        .post(
                            "/api/v1/inventories",
                            mapOf("itemId" to item["id"].asText(), "shelfId" to shelf["id"].asText()),
                        ).body,
                )

            assertThat(json(client.get("/api/v1/search?q=드릴").body)["items"].size()).isEqualTo(1)
            assertThat(
                json(client.get("/api/v1/search?q=드릴&roomId=${room["id"].asText()}").body)["items"].size(),
            ).isEqualTo(1)
            assertThat(
                client.get("/api/v1/search?q=드릴&roomId=${room["id"].asText()}&shelfId=${shelf["id"].asText()}").statusCode,
            ).isEqualTo(HttpStatus.BAD_REQUEST)

            val update =
                client.patch(
                    "/api/v1/inventories/${inventory["id"].asText()}",
                    mapOf(
                        "status" to "DISPOSED",
                        "confirmDisposal" to true,
                        "version" to inventory["version"].asLong(),
                    ),
                )
            assertThat(update.statusCode.is2xxSuccessful).isTrue()
            assertThat(json(client.get("/api/v1/search?q=드릴").body)["items"].size()).isZero()
            client.post("/api/v1/items/${item["id"].asText()}/view", emptyMap<String, String>())
            assertThat(json(client.get("/api/v1/search/frequent").body)["items"].size()).isZero()
        }

        @Test
        fun `공유받은 Room의 재고가 검색되고 소유자와 공유 여부를 표시한다`() {
            val owner = AuthTestClient(restTemplate, objectMapper)
            owner.signup("search-owner-${UUID.randomUUID()}@example.com", "password-1234", "소유자")
            val room = json(owner.post("/api/v1/rooms", mapOf("name" to "공유 창고")).body)
            val item = json(owner.post("/api/v1/items", mapOf("name" to "캠핑 랜턴")).body)
            owner.post(
                "/api/v1/inventories",
                mapOf("itemId" to item["id"].asText(), "roomId" to room["id"].asText()),
            )
            val guestEmail = "search-guest-${UUID.randomUUID()}@example.com"
            owner.post(
                "/api/v1/share-invitations",
                mapOf("roomId" to room["id"].asText(), "invitedEmail" to guestEmail, "permission" to "VIEW"),
            )
            outboxRelay.relay()
            val guest = AuthTestClient(restTemplate, objectMapper)
            guest.signup(guestEmail, "password-1234", "게스트")
            val token = mailPort.invitationMessages.last { it.email == guestEmail }.token
            guest.post("/api/v1/share-invitations/accept", mapOf("token" to token))

            val shared = json(guest.get("/api/v1/search?q=랜턴&scope=SHARED").body)
            assertThat(shared["items"].size()).isEqualTo(1)
            assertThat(shared["items"][0]["inventories"][0]["shared"].asBoolean()).isTrue()
            assertThat(shared["items"][0]["inventories"][0]["ownerName"].asText()).isEqualTo("소유자")

            assertThat(json(guest.get("/api/v1/search?q=랜턴&scope=OWNED").body)["items"].size()).isZero()
            assertThat(json(guest.get("/api/v1/search?q=랜턴&scope=ALL").body)["items"].size()).isEqualTo(1)
            guest.post("/api/v1/items/${item["id"].asText()}/view", emptyMap<String, String>())
            assertThat(json(guest.get("/api/v1/search/frequent").body)["items"].size()).isEqualTo(1)
            val grant = json(owner.get("/api/v1/shares").body)["items"][0]["id"].asText()
            assertThat(owner.delete("/api/v1/shares/$grant").statusCode).isEqualTo(HttpStatus.NO_CONTENT)
            assertThat(json(guest.get("/api/v1/search?q=랜턴").body)["items"].size()).isZero()
            assertThat(json(guest.get("/api/v1/search/frequent").body)["items"].size()).isZero()
        }

        @Test
        fun `한 그룹의 재고가 500개를 넘어도 정확한 개수와 다음 그룹을 반환한다`() {
            val room = json(client.post("/api/v1/rooms", mapOf("name" to "대량 창고")).body)
            val first = json(client.post("/api/v1/items", mapOf("name" to "A 대량 물건")).body)
            val second = json(client.post("/api/v1/items", mapOf("name" to "B 다음 물건")).body)
            seedInventories(first["id"].asText(), room["id"].asText(), 601)
            seedInventories(second["id"].asText(), room["id"].asText(), 1)

            val page = json(client.get("/api/v1/search?sort=NAME_ASC&limit=1").body)
            assertThat(page["items"][0]["inventoryCount"].asInt()).isEqualTo(601)
            assertThat(page["items"][0]["inventories"].size()).isEqualTo(5)
            assertThat(page["items"][0]["hasMoreInventories"].asBoolean()).isTrue()
            assertThat(page["nextCursor"]?.asText()).isNotBlank()
            val next = json(client.get("/api/v1/search?sort=NAME_ASC&limit=1&cursor=${page["nextCursor"].asText()}").body)
            assertThat(next["items"][0]["itemId"].asText()).isEqualTo(second["id"].asText())
            assertThat(next["nextCursor"]?.isNull ?: true).isTrue()
        }

        @Test
        fun `앞 페이지 물건이 삭제되어도 다음 그룹이 누락되지 않는다`() {
            val room = json(client.post("/api/v1/rooms", mapOf("name" to "커서 창고")).body)
            val ids =
                listOf("A", "B", "C").map { name ->
                    val item = json(client.post("/api/v1/items", mapOf("name" to name)).body)
                    seedInventories(item["id"].asText(), room["id"].asText(), 1)
                    item["id"].asText()
                }
            val first = json(client.get("/api/v1/search?sort=NAME_ASC&limit=1").body)
            jdbc.update("UPDATE inventory SET deleted_at = now() WHERE item_id = ?::uuid", ids[0])
            val next = json(client.get("/api/v1/search?sort=NAME_ASC&limit=1&cursor=${first["nextCursor"].asText()}").body)
            assertThat(next["items"][0]["itemId"].asText()).isEqualTo(ids[1])
        }

        @Test
        fun `잘못된 커서와 다른 필터의 커서를 400으로 거부한다`() {
            listOf("garbage", "bzox", "x".repeat(513)).forEach { cursor ->
                val response = client.get("/api/v1/search?cursor=$cursor")
                assertThat(response.statusCode).isEqualTo(HttpStatus.BAD_REQUEST)
                assertThat(json(response.body)["code"].asText()).isEqualTo("INVALID_CURSOR")
            }
            val room = json(client.post("/api/v1/rooms", mapOf("name" to "필터 창고")).body)
            repeat(2) {
                val item = json(client.post("/api/v1/items", mapOf("name" to "물건 $it")).body)
                seedInventories(item["id"].asText(), room["id"].asText(), 1)
            }
            val page = json(client.get("/api/v1/search?sort=NAME_ASC&limit=1").body)
            val response = client.get("/api/v1/search?sort=NAME_ASC&scope=OWNED&cursor=${page["nextCursor"].asText()}")
            assertThat(response.statusCode).isEqualTo(HttpStatus.BAD_REQUEST)
            assertThat(json(response.body)["code"].asText()).isEqualTo("INVALID_CURSOR")
        }

        private fun seedInventories(
            itemId: String,
            roomId: String,
            count: Int,
        ) {
            jdbc.update(
                """
                INSERT INTO inventory (id, space_id, item_id, room_id, created_at, updated_at)
                SELECT gen_random_uuid(), space_id, id, ?::uuid, now(), now()
                FROM item CROSS JOIN generate_series(1, ?) WHERE id = ?::uuid
                """.trimIndent(),
                roomId,
                count,
                itemId,
            )
        }

        @Test
        fun `검색 토큰을 위치와 이름에 나누어 적용하고 이름 오타를 찾는다`() {
            val room = json(client.post("/api/v1/rooms", mapOf("name" to "여행 창고")).body)
            val item = json(client.post("/api/v1/items", mapOf("name" to "wireless headphones")).body)
            seedInventories(item["id"].asText(), room["id"].asText(), 1)
            val byTokens = json(client.get("/api/v1/search?q=여행 wireless").body)
            assertThat(byTokens["items"].size()).isEqualTo(1)
            val byTypo = json(client.get("/api/v1/search?q=wireless headphons").body)
            assertThat(byTypo["items"].size()).isEqualTo(1)
        }

        @ParameterizedTest
        @ValueSource(strings = ["RELEVANCE", "UPDATED_DESC", "NAME_ASC", "VIEWED_DESC", "STATUS_ASC"])
        fun `정렬 키가 같은 그룹도 모든 페이지에서 한번씩 반환한다`(sort: String) {
            val room = json(client.post("/api/v1/rooms", mapOf("name" to "동점 창고")).body)
            val ids =
                (1..3).map {
                    val item = json(client.post("/api/v1/items", mapOf("name" to "동일 물건")).body)
                    seedInventories(item["id"].asText(), room["id"].asText(), 2)
                    item["id"].asText()
                }
            ids.forEach { jdbc.update("UPDATE inventory SET updated_at = '2026-01-01T00:00:00Z' WHERE item_id = ?::uuid", it) }
            val seen = mutableListOf<String>()
            var cursor: String? = null
            repeat(3) {
                val response = client.get("/api/v1/search?q=동일&sort=$sort&limit=1" + (cursor?.let { "&cursor=$it" } ?: ""))
                assertThat(response.statusCode).isEqualTo(HttpStatus.OK)
                val page = json(response.body)
                assertThat(page["items"].size()).isEqualTo(1)
                seen.add(page["items"][0]["itemId"].asText())
                cursor = page["nextCursor"]?.takeUnless { it.isNull }?.asText()
            }
            assertThat(seen).containsExactlyElementsOf(ids.sorted())
            assertThat(cursor).isNull()
        }

        private fun json(body: String?): JsonNode = objectMapper.readTree(body ?: "{}")
    }
