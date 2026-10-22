package com.agolock.media

import com.agolock.media.domain.Image
import com.agolock.media.domain.ImageStatus
import com.agolock.media.service.ImageProcessService
import com.agolock.media.service.ImageProcessTransaction
import com.agolock.media.storage.ImageStoragePort
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.mockito.Mockito.mock
import org.mockito.Mockito.`when`
import java.awt.image.BufferedImage
import java.io.ByteArrayOutputStream
import java.time.Clock
import java.time.Instant
import java.time.OffsetDateTime
import java.time.ZoneOffset
import java.util.UUID
import javax.imageio.ImageIO

class ImageProcessServiceTest {
    @Test
    fun `PNG 이미지를 WebP와 썸네일로 처리한다`() {
        val imageId = UUID.randomUUID()
        val objectKey = "spaces/s/item/$imageId/original.webp"
        val image =
            Image(
                imageId,
                UUID.randomUUID(),
                UUID.randomUUID(),
                null,
                objectKey,
                null,
                ImageStatus.PENDING,
                OffsetDateTime.now().plusHours(1),
                OffsetDateTime.now(),
            )
        val transaction = mock(ImageProcessTransaction::class.java)
        val storage = mock(ImageStoragePort::class.java)
        val now = OffsetDateTime.of(2026, 10, 21, 12, 0, 0, 0, ZoneOffset.UTC)
        `when`(transaction.claim(imageId, now)).thenReturn(image)
        `when`(storage.getObject(objectKey)).thenReturn(pngBytes())
        val service =
            ImageProcessService(
                transaction,
                storage,
                Clock.fixed(Instant.parse("2026-10-21T12:00:00Z"), ZoneOffset.UTC),
            )

        service.process(imageId)

        assertThat(
            org.mockito.Mockito
                .mockingDetails(transaction)
                .invocations
                .any { it.method.name == "markReady" },
        ).isTrue()
    }

    @Test
    fun `처리 중 예외가 나면 FAILED로 전이한다`() {
        val imageId = UUID.randomUUID()
        val image =
            Image(
                imageId,
                UUID.randomUUID(),
                UUID.randomUUID(),
                null,
                "spaces/s/item/$imageId/original.webp",
                null,
                ImageStatus.PENDING,
                OffsetDateTime.now().plusHours(1),
                OffsetDateTime.now(),
            )
        val transaction = mock(ImageProcessTransaction::class.java)
        val storage = mock(ImageStoragePort::class.java)
        val now = OffsetDateTime.of(2026, 10, 21, 12, 0, 0, 0, ZoneOffset.UTC)
        `when`(transaction.claim(imageId, now)).thenReturn(image)
        `when`(storage.getObject(image.objectKey)).thenThrow(IllegalStateException("storage down"))
        val service =
            ImageProcessService(
                transaction,
                storage,
                Clock.fixed(Instant.parse("2026-10-21T12:00:00Z"), ZoneOffset.UTC),
            )

        org.junit.jupiter.api.assertThrows<IllegalStateException> {
            service.process(imageId)
        }

        assertThat(
            org.mockito.Mockito
                .mockingDetails(transaction)
                .invocations
                .any { it.method.name == "markFailed" },
        ).isTrue()
    }

    private fun pngBytes(): ByteArray {
        val image = BufferedImage(256, 256, BufferedImage.TYPE_INT_ARGB)
        val output = ByteArrayOutputStream()
        ImageIO.write(image, "png", output)
        return output.toByteArray()
    }
}
