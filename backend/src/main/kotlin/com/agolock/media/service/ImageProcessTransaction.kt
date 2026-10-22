@file:Suppress("LongParameterList", "ThrowsCount")

package com.agolock.media.service

import com.agolock.media.domain.Image
import com.agolock.media.persistence.ImageMapper
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.OffsetDateTime
import java.util.UUID

/**
 * 이미지 처리 상태 전이만 담당하는 짧은 트랜잭션 경계입니다.
 *
 * S3 다운로드/업로드/인코딩은 이 클래스 밖에서 수행해 DB 커넥션을 오래 잡지 않습니다.
 */
@Service
class ImageProcessTransaction(
    private val imageMapper: ImageMapper,
) {
    @Transactional
    fun claim(
        imageId: UUID,
        now: OffsetDateTime,
    ): Image? {
        if (imageMapper.claimForProcessing(imageId, now) == 0) return null
        return imageMapper.findById(imageId)
    }

    @Transactional
    fun markReady(
        imageId: UUID,
        mimeType: String,
        sizeBytes: Long,
        width: Int,
        height: Int,
        sha256: String,
        thumbnailKey: String,
        now: OffsetDateTime,
    ): Boolean =
        imageMapper.markReady(
            imageId,
            mimeType,
            sizeBytes,
            width,
            height,
            sha256,
            thumbnailKey,
            now,
        ) > 0

    @Transactional
    fun markFailed(
        imageId: UUID,
        error: String,
        now: OffsetDateTime,
    ) {
        imageMapper.markProcessingFailed(imageId, error, now)
    }
}
