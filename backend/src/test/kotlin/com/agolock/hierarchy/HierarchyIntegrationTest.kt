package com.agolock.hierarchy

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
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

@SpringBootTest(
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
    properties = ["auth.signup.invite-required=false"],
)
@ActiveProfiles("test")
@Import(TestcontainersConfiguration::class)
class HierarchyIntegrationTest
    @Autowired
    constructor(
        private val restTemplate: TestRestTemplate,
        private val objectMapper: ObjectMapper,
    ) {
        private lateinit var client: AuthTestClient

        @BeforeEach
        fun setUp() {
            client = AuthTestClient(restTemplate, objectMapper)
            client.signup("hierarchy-${UUID.randomUUID()}@example.com", "password-1234", "지민")
        }

        @Test
        fun `Room과 Shelf와 Box를 만들고 삭제 차단을 확인한다`() {
            val room = createRoom("거실")
            assertThat(room["name"].asText()).isEqualTo("거실")
            assertThat(room["effectivePermission"].asText()).isEqualTo("OWNER")
            assertThat(room["isShared"].asBoolean()).isFalse()
            val roomId = room["id"].asText()

            val shelf = json(client.post("/api/v1/rooms/$roomId/shelves", mapOf("name" to "책장")).body)
            val shelfId = shelf["id"].asText()
            val box = json(client.post("/api/v1/shelves/$shelfId/boxes", mapOf("name" to "파란 박스")).body)
            val boxId = box["id"].asText()

            val roomDetail = json(client.get("/api/v1/rooms/$roomId").body)
            assertThat(roomDetail["shelfCount"].asInt()).isEqualTo(1)
            val shelfDetail = json(client.get("/api/v1/shelves/$shelfId").body)
            assertThat(shelfDetail["boxCount"].asInt()).isEqualTo(1)

            assertThat(client.delete("/api/v1/rooms/$roomId").statusCode).isEqualTo(HttpStatus.CONFLICT)
            assertThat(client.delete("/api/v1/shelves/$shelfId").statusCode).isEqualTo(HttpStatus.CONFLICT)
            assertThat(client.delete("/api/v1/boxes/$boxId").statusCode).isEqualTo(HttpStatus.NO_CONTENT)
            assertThat(client.delete("/api/v1/shelves/$shelfId").statusCode).isEqualTo(HttpStatus.NO_CONTENT)
            assertThat(client.delete("/api/v1/rooms/$roomId").statusCode).isEqualTo(HttpStatus.NO_CONTENT)
        }

        @Test
        fun `버전 충돌과 이동 no-op을 처리한다`() {
            val room1 = createRoom("거실")
            val room2 = createRoom("안방")
            val roomId1 = room1["id"].asText()
            val roomId2 = room2["id"].asText()
            val shelf = json(client.post("/api/v1/rooms/$roomId1/shelves", mapOf("name" to "책장")).body)
            val shelfId = shelf["id"].asText()

            val updated = client.patch("/api/v1/rooms/$roomId1", mapOf("name" to "큰 거실", "version" to 0))
            assertThat(updated.statusCode).isEqualTo(HttpStatus.OK)
            assertThat(json(updated.body)["version"].asLong()).isEqualTo(1)
            assertThat(client.patch("/api/v1/rooms/$roomId1", mapOf("name" to "다른 이름", "version" to 0)).statusCode)
                .isEqualTo(HttpStatus.CONFLICT)

            val moved = client.patch("/api/v1/shelves/$shelfId/parent", mapOf("roomId" to roomId2, "version" to 0))
            assertThat(moved.statusCode).isEqualTo(HttpStatus.OK)
            assertThat(json(moved.body)["roomId"].asText()).isEqualTo(roomId2)
            assertThat(json(moved.body)["version"].asLong()).isEqualTo(1)

            val noOp = client.patch("/api/v1/shelves/$shelfId/parent", mapOf("roomId" to roomId2, "version" to 1))
            assertThat(noOp.statusCode).isEqualTo(HttpStatus.OK)
            assertThat(json(noOp.body)["version"].asLong()).isEqualTo(1)
        }

        @Test
        fun `PATCH는 생략한 설명을 유지하고 null이면 삭제한다`() {
            val created = json(client.post("/api/v1/rooms", mapOf("name" to "거실", "description" to "창가 수납")).body)
            val roomId = created["id"].asText()

            val renamed = client.patch("/api/v1/rooms/$roomId", mapOf("name" to "큰 거실", "version" to 0))
            assertThat(renamed.statusCode).isEqualTo(HttpStatus.OK)
            assertThat(json(renamed.body)["description"].asText()).isEqualTo("창가 수납")

            val cleared =
                client.patchRaw(
                    "/api/v1/rooms/$roomId",
                    """{"description":null,"version":1}""",
                )
            assertThat(cleared.statusCode).isEqualTo(HttpStatus.OK)
            assertThat(json(cleared.body).has("description")).isFalse()
        }

        @Test
        fun `커서 페이지네이션과 Idempotency-Key를 지원한다`() {
            val first = createRoom("A Room", idempotencyKey = "create-a")
            val firstReplay = createRoom("A Room", idempotencyKey = "create-a")
            assertThat(firstReplay["id"].asText()).isEqualTo(first["id"].asText())
            assertThat(
                client
                    .post(
                        "/api/v1/rooms",
                        mapOf("name" to "다른 이름"),
                        mapOf("Idempotency-Key" to "create-a"),
                    ).statusCode,
            ).isEqualTo(HttpStatus.CONFLICT)

            createRoom("B Room")
            createRoom("C Room")
            val page1 = json(client.get("/api/v1/rooms?limit=2").body)
            assertThat(page1["items"].size()).isEqualTo(2)
            val cursor = page1["nextCursor"].asText()
            assertThat(cursor).isNotBlank()
            val page2 = json(client.get("/api/v1/rooms?limit=2&cursor=$cursor").body)
            assertThat(page2["items"].size()).isEqualTo(1)
        }

        @Test
        fun `같은 Idempotency-Key의 동시 요청은 한 번만 생성한다`() {
            val email = "race-${UUID.randomUUID()}@example.com"
            client.signup(email, "password-1234", "지민")
            val other = AuthTestClient(restTemplate, objectMapper)
            assertThat(other.login(email, "password-1234").statusCode).isEqualTo(HttpStatus.OK)

            val start = CountDownLatch(1)
            val pool = Executors.newFixedThreadPool(2)
            val futures =
                listOf(client, other).map { session ->
                    pool.submit<org.springframework.http.ResponseEntity<String>> {
                        start.await()
                        session.post(
                            "/api/v1/rooms",
                            mapOf("name" to "동시 생성"),
                            mapOf("Idempotency-Key" to "simultaneous-create"),
                        )
                    }
                }
            start.countDown()
            val responses = futures.map { it.get(10, TimeUnit.SECONDS) }
            pool.shutdown()

            assertThat(responses.map { it.statusCode }).containsOnly(HttpStatus.CREATED)
            val ids = responses.map { json(it.body)["id"].asText() }
            assertThat(ids.distinct()).hasSize(1)
            val rooms = json(client.get("/api/v1/rooms").body)["items"]
            assertThat(rooms.count { it["name"].asText() == "동시 생성" }).isEqualTo(1)
        }

        @Test
        fun `다른 Space의 리소스는 404로 숨긴다`() {
            val room = createRoom("내 방")
            val other = AuthTestClient(restTemplate, objectMapper)
            other.signup("other-${UUID.randomUUID()}@example.com", "password-1234", "민지")
            val otherRoom = json(other.post("/api/v1/rooms", mapOf("name" to "민지 방")).body)

            assertThat(client.get("/api/v1/rooms/${otherRoom["id"].asText()}").statusCode)
                .isEqualTo(HttpStatus.NOT_FOUND)
            assertThat(
                client
                    .patch(
                        "/api/v1/rooms/${otherRoom["id"].asText()}",
                        mapOf("name" to "탈취", "version" to 0),
                    ).statusCode,
            ).isEqualTo(HttpStatus.NOT_FOUND)
            assertThat(json(client.get("/api/v1/rooms/${room["id"].asText()}").body)["name"].asText())
                .isEqualTo("내 방")
        }

        private fun createRoom(
            name: String,
            idempotencyKey: String? = null,
        ): JsonNode {
            val headers = if (idempotencyKey == null) emptyMap() else mapOf("Idempotency-Key" to idempotencyKey)
            val response = client.post("/api/v1/rooms", mapOf("name" to name), headers)
            assertThat(response.statusCode).isEqualTo(HttpStatus.CREATED)
            return json(response.body)
        }

        private fun json(body: String?): JsonNode = objectMapper.readTree(body ?: "{}")
    }
