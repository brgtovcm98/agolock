package com.agolock.inventory

import com.agolock.identity.service.InMemoryRateLimiter
import com.agolock.identity.service.LoginAttemptService
import com.agolock.support.AuthTestClient
import com.agolock.support.FakeMailConfiguration
import com.agolock.support.FakeMailPort
import com.agolock.support.TestcontainersConfiguration
import com.fasterxml.jackson.databind.ObjectMapper
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.web.client.TestRestTemplate
import org.springframework.context.annotation.Import
import org.springframework.http.HttpStatus
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.test.context.ActiveProfiles
import org.springframework.transaction.support.TransactionTemplate
import java.util.UUID
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException

@SpringBootTest(
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
    properties = ["auth.signup.invite-required=false"],
)
@ActiveProfiles("test")
@Import(TestcontainersConfiguration::class, FakeMailConfiguration::class)
class InventoryWriteHardeningIT
    @Autowired
    constructor(
        private val rest: TestRestTemplate,
        private val mapper: ObjectMapper,
        private val rateLimiter: InMemoryRateLimiter,
        private val attempts: LoginAttemptService,
        private val jdbc: JdbcTemplate,
        private val transaction: TransactionTemplate,
    ) {
        @Autowired
        private lateinit var mailPort: FakeMailPort

        @Autowired
        private lateinit var relay: com.agolock.messaging.outbox.OutboxRelayService

        @Autowired
        private lateinit var items: com.agolock.inventory.persistence.ItemMapper

        private lateinit var client: AuthTestClient

        @BeforeEach
        fun setup() {
            rateLimiter.clear()
            attempts.clear()
            client = AuthTestClient(rest, mapper)
            client.signup("writes-${UUID.randomUUID()}@example.com", "password-1234", "테스트")
        }

        @Test
        fun `잠금 이후 mapper 재조회는 JDBC 변경 이전의 캐시를 사용하지 않는다`() {
            val item = UUID.fromString(createId("/api/v1/items", mapOf("name" to "캐시 검증")))
            transaction.executeWithoutResult {
                assertThat(items.findByIdAny(item)).isNotNull()
                jdbc.update("UPDATE item SET deleted_at = now() WHERE id = ?", item)
                assertThat(items.findByIdAny(item)).isNull()
            }
        }

        @Test
        fun `폐기된 재고의 설명 수정은 폐기 확인 없이 빈 설명을 유지한다`() {
            val id = inventory()
            assertThat(
                client
                    .patch(
                        "/api/v1/inventories/$id",
                        mapOf("version" to 0, "status" to "DISPOSED", "confirmDisposal" to true),
                    ).statusCode,
            ).isEqualTo(HttpStatus.OK)
            val result = client.patch("/api/v1/inventories/$id", mapOf("version" to 1, "descriptionOverride" to ""))
            assertThat(result.statusCode).isEqualTo(HttpStatus.OK)
            assertThat(mapper.readTree(result.body)["descriptionOverride"].asText()).isEmpty()
        }

        @Test
        fun `빈 이름 오버라이드는 상속 대신 거부한다`() {
            val result =
                client.patch(
                    "/api/v1/inventories/${inventory()}",
                    mapOf("version" to 0, "nameOverride" to "   "),
                )
            assertThat(result.statusCode).isEqualTo(HttpStatus.BAD_REQUEST)
        }

        @Test
        fun `재고 삭제는 오래된 버전을 거부한다`() {
            val id = inventory()
            client.patch("/api/v1/inventories/$id", mapOf("version" to 0, "nameOverride" to "변경"))
            assertThat(client.delete("/api/v1/inventories/$id?version=0").statusCode).isEqualTo(HttpStatus.CONFLICT)
            assertThat(client.get("/api/v1/inventories/$id").statusCode).isEqualTo(HttpStatus.OK)
            assertThat(client.delete("/api/v1/inventories/$id?version=1").statusCode).isEqualTo(HttpStatus.NO_CONTENT)
        }

        @Test
        fun `카테고리 병합은 마스터와 재고 버전을 증가시켜 오래된 저장을 거부한다`() {
            val source = createId("/api/v1/categories", mapOf("name" to "원본"))
            val target = createId("/api/v1/categories", mapOf("name" to "대상"))
            val room = createId("/api/v1/rooms", mapOf("name" to "방"))
            val item = createId("/api/v1/items", mapOf("name" to "물건", "categoryId" to source))
            val inv = createId("/api/v1/inventories", mapOf("itemId" to item, "roomId" to room))
            client.patch("/api/v1/inventories/$inv", mapOf("version" to 0, "categoryIdOverride" to source))
            assertThat(client.post("/api/v1/categories/$source/merge", mapOf("targetCategoryId" to target)).statusCode)
                .isEqualTo(HttpStatus.OK)
            assertThat(mapper.readTree(client.get("/api/v1/items/$item").body)["version"].asLong()).isEqualTo(1)
            assertThat(mapper.readTree(client.get("/api/v1/inventories/$inv").body)["version"].asLong()).isEqualTo(2)
            assertThat(
                client.patch("/api/v1/inventories/$inv", mapOf("version" to 1, "nameOverride" to "오래된 값")).statusCode,
            ).isEqualTo(HttpStatus.CONFLICT)
        }

        @Test
        fun `물건 삭제와 경합하는 재고 생성은 잠금 후 삭제 상태를 재검증한다`() {
            val room = createId("/api/v1/rooms", mapOf("name" to "방"))
            val item = createId("/api/v1/items", mapOf("name" to "물건"))
            client.refreshCsrf()
            val executor = Executors.newSingleThreadExecutor()
            try {
                val pending =
                    transaction.execute {
                        jdbc.update("UPDATE item SET deleted_at = now() WHERE id = ?", UUID.fromString(item))
                        val future =
                            executor.submit<org.springframework.http.ResponseEntity<String>> {
                                client.post("/api/v1/inventories", mapOf("itemId" to item, "roomId" to room))
                            }
                        assertBlocked(future)
                        future
                    }!!
                assertThat(pending.get(5, TimeUnit.SECONDS).statusCode).isEqualTo(HttpStatus.NOT_FOUND)
            } finally {
                executor.shutdownNow()
            }
        }

        @Test
        fun `위치 삭제와 경합하는 재고 생성은 조상 잠금 후 삭제 상태를 재검증한다`() {
            val room = createId("/api/v1/rooms", mapOf("name" to "방"))
            val shelf = createId("/api/v1/rooms/$room/shelves", mapOf("name" to "선반"))
            val box = createId("/api/v1/shelves/$shelf/boxes", mapOf("name" to "상자"))
            val item = createId("/api/v1/items", mapOf("name" to "물건"))
            client.refreshCsrf()
            val executor = Executors.newSingleThreadExecutor()
            try {
                val pending =
                    transaction.execute {
                        jdbc.update("UPDATE room SET deleted_at = now() WHERE id = ?", UUID.fromString(room))
                        val future =
                            executor.submit<org.springframework.http.ResponseEntity<String>> {
                                client.post("/api/v1/inventories", mapOf("itemId" to item, "boxId" to box))
                            }
                        assertBlocked(future)
                        future
                    }!!
                assertThat(pending.get(5, TimeUnit.SECONDS).statusCode).isEqualTo(HttpStatus.NOT_FOUND)
            } finally {
                executor.shutdownNow()
            }
        }

        @Test
        fun `카테고리 삭제와 경합하는 물건 생성은 잠금 후 삭제 상태를 재검증한다`() {
            val category = createId("/api/v1/categories", mapOf("name" to "분류"))
            client.refreshCsrf()
            val executor = Executors.newSingleThreadExecutor()
            try {
                val pending =
                    transaction.execute {
                        jdbc.update("UPDATE category SET deleted_at = now() WHERE id = ?", UUID.fromString(category))
                        val future =
                            executor.submit<org.springframework.http.ResponseEntity<String>> {
                                client.post("/api/v1/items", mapOf("name" to "물건", "categoryId" to category))
                            }
                        assertBlocked(future)
                        future
                    }!!
                assertThat(pending.get(5, TimeUnit.SECONDS).statusCode).isEqualTo(HttpStatus.NOT_FOUND)
            } finally {
                executor.shutdownNow()
            }
        }

        @Test
        fun `조상 방 삭제와 경합하는 상자 생성은 삭제된 경로에 쓰지 않는다`() {
            val room = createId("/api/v1/rooms", mapOf("name" to "방"))
            val shelf = createId("/api/v1/rooms/$room/shelves", mapOf("name" to "선반"))
            client.refreshCsrf()
            val executor = Executors.newSingleThreadExecutor()
            try {
                val pending =
                    transaction.execute {
                        jdbc.update("UPDATE room SET deleted_at = now() WHERE id = ?", UUID.fromString(room))
                        val future =
                            executor.submit<org.springframework.http.ResponseEntity<String>> {
                                client.post("/api/v1/shelves/$shelf/boxes", mapOf("name" to "상자"))
                            }
                        assertBlocked(future)
                        future
                    }!!
                assertThat(pending.get(5, TimeUnit.SECONDS).statusCode).isEqualTo(HttpStatus.NOT_FOUND)
            } finally {
                executor.shutdownNow()
            }
        }

        @Test
        fun `대상 위치 삭제와 경합하는 이동은 삭제된 위치로 이동하지 않고 이력을 남기지 않는다`() {
            val id = inventory()
            val before = mapper.readTree(client.get("/api/v1/inventories/$id").body)
            val target = createId("/api/v1/rooms", mapOf("name" to "대상"))
            client.refreshCsrf()
            val executor = Executors.newSingleThreadExecutor()
            try {
                val pending =
                    transaction.execute {
                        jdbc.update("UPDATE room SET deleted_at = now() WHERE id = ?", UUID.fromString(target))
                        val future =
                            executor.submit<org.springframework.http.ResponseEntity<String>> {
                                client.patch(
                                    "/api/v1/inventories/$id/location",
                                    mapOf("roomId" to target, "version" to 0),
                                )
                            }
                        assertBlocked(future)
                        future
                    }!!
                assertThat(pending.get(5, TimeUnit.SECONDS).statusCode).isEqualTo(HttpStatus.NOT_FOUND)
                val after = mapper.readTree(client.get("/api/v1/inventories/$id").body)
                assertThat(after["version"].asLong()).isEqualTo(0)
                assertThat(after["location"]["roomId"].asText()).isEqualTo(before["location"]["roomId"].asText())
                assertThat(mapper.readTree(client.get("/api/v1/inventories/$id/movements").body).size()).isZero()
            } finally {
                executor.shutdownNow()
            }
        }

        @Test
        fun `동시 이동을 기다린 재고 삭제는 이전 위치의 버전으로 삭제하지 않는다`() {
            val id = inventory()
            val target = createId("/api/v1/rooms", mapOf("name" to "대상"))
            client.refreshCsrf()
            val executor = Executors.newSingleThreadExecutor()
            try {
                val pending =
                    transaction.execute {
                        jdbc.update(
                            "UPDATE inventory SET room_id = ?, version = version + 1 WHERE id = ?",
                            UUID.fromString(target),
                            UUID.fromString(id),
                        )
                        val future =
                            executor.submit<org.springframework.http.ResponseEntity<String>> {
                                client.delete("/api/v1/inventories/$id?version=0")
                            }
                        assertBlocked(future)
                        future
                    }!!
                assertThat(pending.get(5, TimeUnit.SECONDS).statusCode).isEqualTo(HttpStatus.CONFLICT)
                val after = mapper.readTree(client.get("/api/v1/inventories/$id").body)
                assertThat(after["version"].asLong()).isEqualTo(1)
                assertThat(after["location"]["roomId"].asText()).isEqualTo(target)
            } finally {
                executor.shutdownNow()
            }
        }

        @Test
        fun `대상 선반이 비공개 방으로 이동했으면 잠금 뒤 최신 경로 권한을 확인한다`() {
            val id = inventory()
            val current = mapper.readTree(client.get("/api/v1/inventories/$id").body)
            val room = current["location"]["roomId"].asText()
            val item = current["itemId"].asText()
            val shelf = createId("/api/v1/rooms/$room/shelves", mapOf("name" to "대상 선반"))
            val privateRoom = createId("/api/v1/rooms", mapOf("name" to "비공개 방"))
            val email = "moving-guest-${UUID.randomUUID()}@example.com"
            client.post(
                "/api/v1/share-invitations",
                mapOf("roomId" to room, "invitedEmail" to email, "permission" to "MANAGE"),
            )
            val guest = AuthTestClient(rest, mapper)
            guest.signup(email, "password-1234", "공유 사용자")
            relay.relay()
            val token = mailPort.invitationMessages.last { it.email == email }.token
            assertThat(guest.post("/api/v1/share-invitations/accept", mapOf("token" to token)).statusCode)
                .isEqualTo(HttpStatus.OK)
            guest.refreshCsrf()
            val executor = Executors.newSingleThreadExecutor()
            try {
                val pending =
                    transaction.execute {
                        jdbc.queryForList("SELECT id FROM item WHERE id = ? FOR UPDATE", UUID.fromString(item))
                        val future =
                            executor.submit<org.springframework.http.ResponseEntity<String>> {
                                guest.patch(
                                    "/api/v1/inventories/$id/location",
                                    mapOf("shelfId" to shelf, "version" to 0),
                                )
                            }
                        assertBlocked(future)
                        jdbc.update(
                            "UPDATE shelf SET room_id = ?, version = version + 1 WHERE id = ?",
                            UUID.fromString(privateRoom),
                            UUID.fromString(shelf),
                        )
                        future
                    }!!
                assertThat(pending.get(5, TimeUnit.SECONDS).statusCode).isEqualTo(HttpStatus.NOT_FOUND)
                assertThat(mapper.readTree(client.get("/api/v1/inventories/$id").body)["version"].asLong()).isEqualTo(0)
            } finally {
                executor.shutdownNow()
            }
        }

        private fun assertBlocked(future: java.util.concurrent.Future<*>) {
            assertThatThrownBy { future.get(400, TimeUnit.MILLISECONDS) }
                .isInstanceOf(TimeoutException::class.java)
        }

        private fun createId(
            path: String,
            body: Map<String, Any>,
        ): String = mapper.readTree(client.post(path, body).body)["id"].asText()

        private fun inventory(): String {
            val room = createId("/api/v1/rooms", mapOf("name" to "방"))
            val item = createId("/api/v1/items", mapOf("name" to "물건", "description" to "설명"))
            return createId("/api/v1/inventories", mapOf("itemId" to item, "roomId" to room))
        }
    }
