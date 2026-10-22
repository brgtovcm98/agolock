package com.agolock.media

import com.agolock.common.error.ApiException
import com.agolock.media.domain.Image
import com.agolock.media.domain.ImageStatus
import com.agolock.media.persistence.ImageMapper
import com.agolock.media.service.ImageUploadTransaction
import com.agolock.messaging.outbox.OutboxService
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.mockito.Mockito.mock
import org.mockito.Mockito.never
import org.mockito.Mockito.verify
import org.mockito.Mockito.`when`
import java.time.OffsetDateTime
import java.time.ZoneOffset
import java.util.UUID

class ImageUploadTransactionTest {
    @Test
    fun `업로드 완료는 상태 전이와 image uploaded outbox를 같은 트랜잭션에 기록한다`() {
        val now = OffsetDateTime.of(2026, 10, 22, 12, 0, 0, 0, ZoneOffset.UTC)
        val image = uploadingImage(now)
        val mapper = mock(ImageMapper::class.java)
        val outbox = mock(OutboxService::class.java)
        `when`(mapper.findByIdForUpdate(image.id)).thenReturn(image)
        `when`(mapper.completeUpload(image.id, ImageStatus.PENDING, "image/webp", 100, "hash", now)).thenReturn(1)
        val service = ImageUploadTransaction(mapper, outbox)

        service.complete(image.id, image.objectKey, "image/webp", 100, "hash", now)

        verify(mapper).completeUpload(image.id, ImageStatus.PENDING, "image/webp", 100, "hash", now)
        verify(outbox).enqueueImageUploaded(
            image.spaceId,
            image.id,
            mapOf(
                "imageId" to image.id,
                "targetType" to "ITEM",
                "targetId" to image.itemId,
                "objectKey" to image.objectKey,
                "declaredSizeBytes" to 100L,
            ),
        )
    }

    @Test
    fun `이미 완료된 업로드는 409 대신 기존 행을 반환하고 outbox를 다시 쓰지 않는다`() {
        val now = OffsetDateTime.of(2026, 10, 22, 12, 0, 0, 0, ZoneOffset.UTC)
        val image = uploadingImage(now).copy(status = ImageStatus.PENDING, mimeType = "image/webp", sizeBytes = 100)
        val mapper = mock(ImageMapper::class.java)
        val outbox = mock(OutboxService::class.java)
        `when`(mapper.findByIdForUpdate(image.id)).thenReturn(image)
        val service = ImageUploadTransaction(mapper, outbox)

        val completed = service.complete(image.id, image.objectKey, "image/webp", 100, "hash", now)

        assertThat(completed).isEqualTo(image)
        verify(mapper, never()).completeUpload(image.id, ImageStatus.PENDING, "image/webp", 100, "hash", now)
        verify(outbox, never()).enqueueImageUploaded(
            image.spaceId,
            image.id,
            mapOf(
                "imageId" to image.id,
                "targetType" to "ITEM",
                "targetId" to image.itemId,
                "objectKey" to image.objectKey,
                "declaredSizeBytes" to 100L,
            ),
        )
    }

    @Test
    fun `객체 키가 다르면 상태를 바꾸지 않는다`() {
        val now = OffsetDateTime.of(2026, 10, 22, 12, 0, 0, 0, ZoneOffset.UTC)
        val image = uploadingImage(now)
        val mapper = mock(ImageMapper::class.java)
        `when`(mapper.findByIdForUpdate(image.id)).thenReturn(image)
        val service = ImageUploadTransaction(mapper, mock(OutboxService::class.java))

        val exception =
            assertThrows<ApiException> {
                service.complete(image.id, "other-key", "image/webp", 100, "hash", now)
            }

        assertThat(exception.code).isEqualTo("VALIDATION_FAILED")
    }

    private fun uploadingImage(now: OffsetDateTime): Image =
        Image(
            UUID.randomUUID(),
            UUID.randomUUID(),
            UUID.randomUUID(),
            null,
            "spaces/s/item/i/original.webp",
            null,
            ImageStatus.UPLOADING,
            now.plusHours(1),
            now,
        )
}
