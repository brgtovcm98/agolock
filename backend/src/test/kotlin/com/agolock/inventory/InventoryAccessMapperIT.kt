package com.agolock.inventory

import com.agolock.inventory.domain.InventoryStatus
import com.agolock.inventory.persistence.InventoryAccessMapper
import com.agolock.support.TestcontainersConfiguration
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.context.annotation.Import
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.test.context.ActiveProfiles
import org.springframework.transaction.annotation.Transactional
import java.time.OffsetDateTime
import java.time.ZoneOffset
import java.time.temporal.ChronoUnit
import java.util.UUID

@SpringBootTest
@ActiveProfiles("test")
@Import(TestcontainersConfiguration::class)
@Transactional
class InventoryAccessMapperIT
    @Autowired
    constructor(
        private val inventoryAccessMapper: InventoryAccessMapper,
        private val jdbcTemplate: JdbcTemplate,
    ) {
        @Test
        fun `Flyway가 V19 inventory_access view를 적용한다`() {
            val applied =
                jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM flyway_schema_history WHERE version = '19' AND success = true",
                    Int::class.java,
                )
            assertThat(applied).isEqualTo(1)

            val views =
                jdbcTemplate.queryForList(
                    """
                    SELECT table_name
                    FROM information_schema.views
                    WHERE table_schema = 'public' AND table_name = 'inventory_access'
                    """.trimIndent(),
                    String::class.java,
                )
            assertThat(views).containsExactly("inventory_access")
        }

        @Test
        fun `findById는 Box 재고의 위치를 view에서 한 번에 해석한다`() {
            val fixture = fixture()
            val inventoryId = UUID.randomUUID()
            insertBoxInventory(fixture, inventoryId, fixture.boxId)

            val location = checkNotNull(inventoryAccessMapper.findById(inventoryId)) { "findById가 null을 반환했습니다." }

            assertThat(location.inventoryId).isEqualTo(inventoryId)
            assertThat(location.spaceId).isEqualTo(fixture.spaceId)
            assertThat(location.itemId).isEqualTo(fixture.itemId)
            assertThat(location.boxId).isEqualTo(fixture.boxId)
            assertThat(location.status).isEqualTo(InventoryStatus.STORED)
            assertThat(location.locationType).isEqualTo("BOX")
            assertThat(location.effectiveRoomId).isEqualTo(fixture.roomId)
            assertThat(location.effectiveShelfId).isEqualTo(fixture.shelfId)
        }

        @Test
        fun `findById는 Room 직속 재고를 ROOM으로 해석한다`() {
            val fixture = fixture()
            val inventoryId = UUID.randomUUID()
            insertRoomInventory(fixture, inventoryId, fixture.roomId)

            val location = checkNotNull(inventoryAccessMapper.findById(inventoryId)) { "findById가 null을 반환했습니다." }

            assertThat(location.locationType).isEqualTo("ROOM")
            assertThat(location.effectiveRoomId).isEqualTo(fixture.roomId)
            assertThat(location.effectiveShelfId).isNull()
            assertThat(location.boxId).isNull()
        }

        private data class Fixture(
            val spaceId: UUID,
            val roomId: UUID,
            val shelfId: UUID,
            val boxId: UUID,
            val itemId: UUID,
            val now: OffsetDateTime,
        )

        @Suppress("LongMethod")
        private fun fixture(): Fixture {
            val now = OffsetDateTime.now(ZoneOffset.UTC).truncatedTo(ChronoUnit.MICROS)
            val userId = UUID.randomUUID()
            val spaceId = UUID.randomUUID()
            val roomId = UUID.randomUUID()
            val shelfId = UUID.randomUUID()
            val boxId = UUID.randomUUID()
            val itemId = UUID.randomUUID()
            jdbcTemplate.update(
                """
                INSERT INTO app_user (id, email, password_hash, display_name, status, created_at, updated_at)
                VALUES (?, ?, ?, ?, 'ACTIVE', ?, ?)
                """.trimIndent(),
                userId,
                "inventory-access-it-${UUID.randomUUID()}@example.com",
                "not-a-real-password-hash",
                "위치 접근 매퍼 테스트",
                now,
                now,
            )
            jdbcTemplate.update(
                """
                INSERT INTO space (id, owner_user_id, name, created_at, updated_at)
                VALUES (?, ?, ?, ?, ?)
                """.trimIndent(),
                spaceId,
                userId,
                "위치 접근 매퍼 테스트 공간",
                now,
                now,
            )
            jdbcTemplate.update(
                """
                INSERT INTO room (id, space_id, name, created_at, updated_at)
                VALUES (?, ?, ?, ?, ?)
                """.trimIndent(),
                roomId,
                spaceId,
                "테스트 방",
                now,
                now,
            )
            jdbcTemplate.update(
                """
                INSERT INTO shelf (id, space_id, room_id, name, created_at, updated_at)
                VALUES (?, ?, ?, ?, ?, ?)
                """.trimIndent(),
                shelfId,
                spaceId,
                roomId,
                "테스트 선반",
                now,
                now,
            )
            jdbcTemplate.update(
                """
                INSERT INTO box (id, space_id, shelf_id, name, created_at, updated_at)
                VALUES (?, ?, ?, ?, ?, ?)
                """.trimIndent(),
                boxId,
                spaceId,
                shelfId,
                "테스트 상자",
                now,
                now,
            )
            jdbcTemplate.update(
                """
                INSERT INTO item (id, space_id, name, created_at, updated_at)
                VALUES (?, ?, ?, ?, ?)
                """.trimIndent(),
                itemId,
                spaceId,
                "위치 해석 대상 물건",
                now,
                now,
            )
            return Fixture(spaceId, roomId, shelfId, boxId, itemId, now)
        }

        private fun insertBoxInventory(
            fixture: Fixture,
            inventoryId: UUID,
            boxId: UUID,
        ) {
            jdbcTemplate.update(
                """
                INSERT INTO inventory (id, space_id, item_id, box_id, created_at, updated_at)
                VALUES (?, ?, ?, ?, ?, ?)
                """.trimIndent(),
                inventoryId,
                fixture.spaceId,
                fixture.itemId,
                boxId,
                fixture.now,
                fixture.now,
            )
        }

        private fun insertRoomInventory(
            fixture: Fixture,
            inventoryId: UUID,
            roomId: UUID,
        ) {
            jdbcTemplate.update(
                """
                INSERT INTO inventory (id, space_id, item_id, room_id, created_at, updated_at)
                VALUES (?, ?, ?, ?, ?, ?)
                """.trimIndent(),
                inventoryId,
                fixture.spaceId,
                fixture.itemId,
                roomId,
                fixture.now,
                fixture.now,
            )
        }
    }
