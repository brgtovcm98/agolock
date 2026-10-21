@file:Suppress("TooGenericExceptionCaught", "MaxLineLength", "MagicNumber")

package com.agolock.media.service

import com.agolock.media.persistence.ImageMapper
import com.agolock.media.storage.ImageStoragePort
import org.slf4j.LoggerFactory
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Service
import java.time.Clock
import java.time.OffsetDateTime

@Service
class ImageCleanupJob(
    private val imageMapper: ImageMapper,
    private val storage: ImageStoragePort,
    private val clock: Clock,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    @Scheduled(fixedDelayString = "\${agolock.image.cleanup-interval-ms:600000}")
    fun cleanup() {
        val now = OffsetDateTime.now(clock)
        imageMapper.failStuckProcessing(now.minusMinutes(STUCK_MINUTES), now)
        imageMapper.findExpired(now, now.minusDays(DELETED_RETENTION_DAYS), 100).forEach { image ->
            try {
                storage.delete(image.objectKey)
                if (image.thumbnailKey != null) storage.delete(image.thumbnailKey!!)
                imageMapper.deleteRow(image.id)
            } catch (exception: Exception) {
                log.warn("이미지 정리 실패 id={} reason={}", image.id, exception.javaClass.simpleName)
            }
        }
    }

    private companion object {
        const val STUCK_MINUTES = 30L
        const val DELETED_RETENTION_DAYS = 30L
    }
}
