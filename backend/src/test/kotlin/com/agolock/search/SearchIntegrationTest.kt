@file:Suppress("MaxLineLength")

package com.agolock.search

import com.agolock.common.cursor.TimeCursorCodec
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
import java.time.OffsetDateTime
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
        }

        @Test
        fun `검색어 없이도 기본 정렬과 상태 필터로 검색한다`() {
            val empty = client.get("/api/v1/search?scope=ALL")
            assertThat(empty.statusCode).isEqualTo(HttpStatus.OK)
            assertThat(json(empty.body)["items"].size()).isZero()

            val room = json(client.post("/api/v1/rooms", mapOf("name" to "서재")).body)
            val item = json(client.post("/api/v1/items", mapOf("name" to "독서등")).body)
            client.post(
                "/api/v1/inventories",
                mapOf("itemId" to item["id"].asText(), "roomId" to room["id"].asText()),
            )

            val all = client.get("/api/v1/search?scope=ALL")
            assertThat(all.statusCode).isEqualTo(HttpStatus.OK)
            assertThat(json(all.body)["items"].size()).isEqualTo(1)
            assertThat(json(all.body)["items"][0]["itemName"].asText()).isEqualTo("독서등")

            val stored = client.get("/api/v1/search?status=STORED")
            assertThat(stored.statusCode).isEqualTo(HttpStatus.OK)
            assertThat(json(stored.body)["items"].size()).isEqualTo(1)
        }

        @Test
        fun `UPDATED_DESC는 keyset 커서로 모든 Item을 중복과 누락 없이 순회한다`() {
            val room = json(client.post("/api/v1/rooms", mapOf("name" to "키셋 보관실")).body)
            val expected =
                (1..5).map { index ->
                    val item = json(client.post("/api/v1/items", mapOf("name" to "키셋 물건 $index")).body)
                    client.post(
                        "/api/v1/inventories",
                        mapOf("itemId" to item["id"].asText(), "roomId" to room["id"].asText()),
                    )
                    item["id"].asText()
                }

            val filtered = json(client.get("/api/v1/search?q=키셋&sort=UPDATED_DESC&limit=2").body)
            assertThat(filtered["items"].size()).isEqualTo(2)
            assertThat(filtered["nextCursor"].isNull).isFalse()

            val collected = mutableListOf<String>()
            var cursor: String? = null
            var pageCount = 0
            do {
                val path = "/api/v1/search?sort=UPDATED_DESC&limit=2" + cursor?.let { "&cursor=$it" }.orEmpty()
                val body = json(client.get(path).body)
                assertThat(body["items"].size()).isBetween(1, 2)
                body["items"].forEach { collected.add(it["itemId"].asText()) }
                cursor = body["nextCursor"]?.takeUnless { it.isNull }?.asText()
                pageCount += 1
            } while (cursor != null && pageCount < 10)

            assertThat(pageCount).isEqualTo(3)
            assertThat(collected).hasSize(5)
            assertThat(collected).doesNotHaveDuplicates()
            assertThat(collected).containsExactlyInAnyOrderElementsOf(expected)
        }

        @Test
        fun `RELEVANCE는 첫 페이지만 지원하고 커서를 거부한다`() {
            val room = json(client.post("/api/v1/rooms", mapOf("name" to "관련도 보관실")).body)
            (1..3).forEach { index ->
                val item = json(client.post("/api/v1/items", mapOf("name" to "관련도 물건 $index")).body)
                client.post(
                    "/api/v1/inventories",
                    mapOf("itemId" to item["id"].asText(), "roomId" to room["id"].asText()),
                )
            }

            val first = client.get("/api/v1/search?q=관련도&sort=RELEVANCE&limit=1")
            assertThat(first.statusCode).isEqualTo(HttpStatus.OK)
            val firstBody = json(first.body)
            assertThat(firstBody["items"].size()).isEqualTo(1)
            val nextCursor = firstBody["nextCursor"]
            assertThat(nextCursor == null || nextCursor.isNull).isTrue()

            val cursor = TimeCursorCodec.encode(OffsetDateTime.parse("2026-01-01T00:00:00Z"), UUID.randomUUID())
            val rejected = client.get("/api/v1/search?q=관련도&sort=RELEVANCE&limit=1&cursor=$cursor")
            assertThat(rejected.statusCode).isEqualTo(HttpStatus.BAD_REQUEST)
            assertThat(json(rejected.body)["code"].asText()).isEqualTo("CURSOR_NOT_SUPPORTED_FOR_SORT")
        }

        private fun json(body: String?): JsonNode = objectMapper.readTree(body ?: "{}")
    }
