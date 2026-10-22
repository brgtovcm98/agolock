@file:Suppress("TooGenericExceptionCaught", "MagicNumber", "MaxLineLength", "ReturnCount")

package com.agolock.media.service

import com.agolock.media.domain.ImageStatus
import com.agolock.media.storage.ImageStoragePort
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import java.awt.image.BufferedImage
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.security.MessageDigest
import java.time.Clock
import java.time.OffsetDateTime
import java.util.HexFormat
import java.util.UUID
import javax.imageio.ImageIO

@Service
class ImageProcessService(
    private val transaction: ImageProcessTransaction,
    private val storage: ImageStoragePort,
    private val clock: Clock,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    fun process(imageId: UUID) {
        val now = OffsetDateTime.now(clock)
        val image = transaction.claim(imageId, now) ?: return
        if (image.status == ImageStatus.DELETED) return
        try {
            val source =
                ImageIO.read(ByteArrayInputStream(storage.getObject(image.objectKey)))
                    ?: throw IllegalArgumentException("이미지를 디코딩할 수 없습니다.")
            val normalized = resize(source, MAX_EDGE)
            val encoded = encodeWebp(normalized)
            val thumb = encodeWebp(resize(normalized, THUMB_EDGE))
            val thumbnailKey = image.objectKey.substringBeforeLast("original.webp") + "thumb-512.webp"
            storage.putObject(image.objectKey, encoded, "image/webp")
            storage.putObject(thumbnailKey, thumb, "image/webp")
            val sha256 = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(encoded))
            transaction.markReady(
                image.id,
                "image/webp",
                encoded.size.toLong(),
                normalized.width,
                normalized.height,
                sha256,
                thumbnailKey,
                OffsetDateTime.now(clock),
            )
        } catch (exception: Exception) {
            transaction.markFailed(image.id, exception.javaClass.simpleName, OffsetDateTime.now(clock))
            log.warn("이미지 처리 실패 id={} reason={}", image.id, exception.javaClass.simpleName)
        }
    }

    private fun resize(
        source: BufferedImage,
        maxEdge: Int,
    ): BufferedImage {
        val scale = maxEdge.toDouble() / maxOf(source.width, source.height)
        if (scale >= 1.0) return source
        val width = (source.width * scale).toInt().coerceAtLeast(1)
        val height = (source.height * scale).toInt().coerceAtLeast(1)
        val target = BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB)
        val graphics = target.createGraphics()
        graphics.drawImage(source, 0, 0, width, height, null)
        graphics.dispose()
        return target
    }

    private fun encodeWebp(image: BufferedImage): ByteArray {
        val output = ByteArrayOutputStream()
        check(ImageIO.write(image, "webp", output)) { "WebP encoder를 찾을 수 없습니다." }
        return output.toByteArray()
    }

    private companion object {
        const val MAX_EDGE = 2048
        const val THUMB_EDGE = 512
    }
}
