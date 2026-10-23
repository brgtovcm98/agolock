package com.agolock.media

import com.agolock.media.domain.ImageStatus
import com.agolock.media.persistence.ImageMapper
import com.agolock.media.service.ImageCleanupJob
import com.agolock.media.service.ImageProcessTransaction
import com.agolock.media.storage.ImageStoragePort
import com.agolock.support.TestcontainersConfiguration
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.mockito.Mockito.mock
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.context.annotation.Import
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.test.context.ActiveProfiles
import org.springframework.transaction.annotation.Transactional
import java.time.Clock
import java.time.OffsetDateTime
import java.time.ZoneOffset
import java.time.temporal.ChronoUnit
import java.util.UUID

@SpringBootTest
@ActiveProfiles("test")
@Import(TestcontainersConfiguration::class)
@Transactional
class ImageMapperIT
    @Autowired
    constructor(
        private val imageMapper: ImageMapper,
        private val jdbcTemplate: JdbcTemplate,
        private val processTransaction: ImageProcessTransaction,
    ) {
        @Test
        fun `워커 중단으로 남은 PROCESSING 이미지는 Outbox로 한 번만 재등록한다`() {
            val fixture = fixture()
            val imageId = UUID.randomUUID()
            imageMapper.insertUploading(
                imageId,
                fixture.spaceId,
                fixture.itemId,
                null,
                "spaces/${fixture.spaceId}/$imageId/original.webp",
                "image/webp",
                100,
                fixture.now.plusHours(24),
                fixture.userId,
                fixture.now.minusHours(1),
            )
            imageMapper.completeUpload(
                imageId,
                ImageStatus.PENDING,
                "image/webp",
                100L,
                "a".repeat(64),
                fixture.now.minusHours(1),
            )
            assertThat(imageMapper.claimForProcessing(imageId, fixture.now.minusMinutes(31))).isEqualTo(1)
            // 장애 후 재전달은 이미 PROCESSING이므로 claim하지 못합니다.
            assertThat(imageMapper.claimForProcessing(imageId, fixture.now)).isZero()
            val job = cleanupJob(fixture.now)

            job.cleanup()
            job.cleanup()

            assertThat(imageMapper.findById(imageId)?.status).isEqualTo(ImageStatus.PENDING)
            assertThat(
                jdbcTemplate.queryForObject(
                    "SELECT count(*) FROM outbox_event WHERE aggregate_id = ? AND event_type = 'image.uploaded'",
                    Int::class.java,
                    imageId,
                ),
            ).isEqualTo(1)
        }

        @Test
        fun `findById는 Image의 12개 필드를 그대로 매핑한다`() {
            val fixture = fixture()
            val imageId = UUID.randomUUID()
            val objectKey = "spaces/${fixture.spaceId}/item/${fixture.itemId}/$imageId/original.webp"
            val uploadExpiresAt = fixture.now.plusHours(24)
            imageMapper.insertUploading(
                imageId,
                fixture.spaceId,
                fixture.itemId,
                null,
                objectKey,
                "image/webp",
                12_345,
                uploadExpiresAt,
                fixture.userId,
                fixture.now,
            )

            val image = checkNotNull(imageMapper.findById(imageId)) { "findById가 null을 반환했습니다." }

            assertThat(image.id).isEqualTo(imageId)
            assertThat(image.spaceId).isEqualTo(fixture.spaceId)
            assertThat(image.itemId).isEqualTo(fixture.itemId)
            assertThat(image.inventoryId).isNull()
            assertThat(image.objectKey).isEqualTo(objectKey)
            assertThat(image.thumbnailKey).isNull()
            assertThat(image.status).isEqualTo(ImageStatus.UPLOADING)
            assertThat(image.uploadExpiresAt.toInstant()).isEqualTo(uploadExpiresAt.toInstant())
            assertThat(image.createdAt.toInstant()).isEqualTo(fixture.now.toInstant())
            assertThat(image.mimeType).isNull()
            assertThat(image.sizeBytes).isNull()
            assertThat(image.isPrimary).isFalse()

            val byItem = imageMapper.listByItem(fixture.itemId)
            assertThat(byItem).hasSize(1)
            assertThat(byItem[0].id).isEqualTo(imageId)
        }

        @Test
        fun `복구 배치를 넘는 작업은 다음 정리에서 재등록하고 시도 한도는 유지한다`() {
            val fixture = fixture()
            jdbcTemplate.update(
                """
                INSERT INTO image (id, space_id, item_id, object_key, status, processing_attempts,
                                   upload_expires_at, created_by, created_at, updated_at, mime_type, size_bytes)
                SELECT gen_random_uuid(), ?, ?, ? || n, 'PROCESSING', 1, ?, ?, ?, ?, 'image/webp', 100
                FROM generate_series(1, 101) n
                """.trimIndent(),
                fixture.spaceId,
                fixture.itemId,
                "recovery/${fixture.spaceId}/",
                fixture.now.plusHours(24),
                fixture.userId,
                fixture.now.minusHours(1),
                fixture.now.minusMinutes(31),
            )
            val job = cleanupJob(fixture.now)
            job.cleanup()
            assertThat(
                jdbcTemplate.queryForObject(
                    "SELECT count(*) FROM image WHERE space_id = ? AND status = 'PROCESSING'",
                    Int::class.java,
                    fixture.spaceId,
                ),
            ).isEqualTo(1)
            job.cleanup()
            assertThat(
                jdbcTemplate.queryForObject(
                    "SELECT count(*) FROM outbox_event WHERE space_id = ? AND event_type = 'image.uploaded'",
                    Int::class.java,
                    fixture.spaceId,
                ),
            ).isEqualTo(101)
            jdbcTemplate.update(
                "UPDATE image SET status = 'PROCESSING', processing_attempts = 5, updated_at = ? WHERE space_id = ?",
                fixture.now.minusMinutes(31),
                fixture.spaceId,
            )
            job.cleanup()
            assertThat(
                jdbcTemplate.queryForObject(
                    "SELECT count(*) FROM image WHERE space_id = ? AND status = 'FAILED'",
                    Int::class.java,
                    fixture.spaceId,
                ),
            ).isEqualTo(101)
            assertThat(
                jdbcTemplate.queryForObject(
                    "SELECT count(*) FROM outbox_event WHERE space_id = ? AND event_type = 'image.uploaded'",
                    Int::class.java,
                    fixture.spaceId,
                ),
            ).isEqualTo(101)
        }

        @Test
        fun `countActiveByTarget은 null 대상을 SQL에 바인딩하지 않는다`() {
            val now = OffsetDateTime.now(ZoneOffset.UTC)

            assertThat(imageMapper.countActiveByTarget(UUID.randomUUID(), null, now)).isZero()
            assertThat(imageMapper.countActiveByTarget(null, UUID.randomUUID(), now)).isZero()
        }

        @Test
        fun `countActiveByTarget은 FAILED와 만료된 UPLOADING을 제외한다`() {
            val fixture = fixture()
            val activeImageId = UUID.randomUUID()
            imageMapper.insertUploading(
                activeImageId,
                fixture.spaceId,
                fixture.itemId,
                null,
                "spaces/${fixture.spaceId}/item/${fixture.itemId}/$activeImageId/original.webp",
                "image/webp",
                1_000,
                fixture.now.plusHours(1),
                fixture.userId,
                fixture.now,
            )
            assertThat(imageMapper.countActiveByTarget(fixture.itemId, null, fixture.now)).isEqualTo(1)

            jdbcTemplate.update(
                "UPDATE image SET status = 'FAILED', updated_at = ? WHERE id = ?",
                fixture.now,
                activeImageId,
            )
            val expiredImageId = UUID.randomUUID()
            imageMapper.insertUploading(
                expiredImageId,
                fixture.spaceId,
                fixture.itemId,
                null,
                "spaces/${fixture.spaceId}/item/${fixture.itemId}/$expiredImageId/original.webp",
                "image/webp",
                1_000,
                fixture.now.minusHours(1),
                fixture.userId,
                fixture.now.minusHours(2),
            )
            assertThat(imageMapper.countActiveByTarget(fixture.itemId, null, fixture.now)).isZero()
        }

        @Test
        fun `findExpiredUploads와 findExpiredDeleted는 만료 업로드와 삭제 보관 대상을 분리한다`() {
            val fixture = fixture()
            val expiredUploadId = UUID.randomUUID()
            imageMapper.insertUploading(
                expiredUploadId,
                fixture.spaceId,
                fixture.itemId,
                null,
                "spaces/${fixture.spaceId}/item/${fixture.itemId}/$expiredUploadId/original.webp",
                "image/webp",
                1_000,
                fixture.now.minusHours(1),
                fixture.userId,
                fixture.now.minusHours(2),
            )
            val deletedId = UUID.randomUUID()
            imageMapper.insertUploading(
                deletedId,
                fixture.spaceId,
                fixture.itemId,
                null,
                "spaces/${fixture.spaceId}/item/${fixture.itemId}/$deletedId/original.webp",
                "image/webp",
                1_000,
                fixture.now.plusHours(1),
                fixture.userId,
                fixture.now.minusDays(3),
            )
            jdbcTemplate.update(
                "UPDATE image SET status = 'DELETED', deleted_at = ?, updated_at = ? WHERE id = ?",
                fixture.now.minusDays(2),
                fixture.now,
                deletedId,
            )

            assertThat(imageMapper.findExpiredUploads(fixture.now, 100).map { it.id })
                .contains(expiredUploadId)
                .doesNotContain(deletedId)
            assertThat(imageMapper.findExpiredDeleted(fixture.now.minusDays(1), 100).map { it.id })
                .contains(deletedId)
                .doesNotContain(expiredUploadId)
        }

        private fun cleanupJob(now: OffsetDateTime): ImageCleanupJob =
            ImageCleanupJob(
                imageMapper = imageMapper,
                storage = mock(ImageStoragePort::class.java),
                clock = Clock.fixed(now.toInstant(), ZoneOffset.UTC),
                processTransaction = processTransaction,
            )

        private data class Fixture(
            val userId: UUID,
            val spaceId: UUID,
            val itemId: UUID,
            val now: OffsetDateTime,
        )

        private fun fixture(): Fixture {
            val now = OffsetDateTime.now(ZoneOffset.UTC).truncatedTo(ChronoUnit.MICROS)
            val userId = UUID.randomUUID()
            val spaceId = UUID.randomUUID()
            val itemId = UUID.randomUUID()
            jdbcTemplate.update(
                """
                INSERT INTO app_user (id, email, password_hash, display_name, status, created_at, updated_at)
                VALUES (?, ?, ?, ?, 'ACTIVE', ?, ?)
                """.trimIndent(),
                userId,
                "image-it-${UUID.randomUUID()}@example.com",
                "not-a-real-password-hash",
                "이미지 매퍼 테스트",
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
                "이미지 매퍼 테스트 공간",
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
                "이미지 대상 물건",
                now,
                now,
            )
            return Fixture(userId, spaceId, itemId, now)
        }
    }
