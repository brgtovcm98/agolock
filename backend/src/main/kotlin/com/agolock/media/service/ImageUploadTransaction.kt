@file:Suppress("LongParameterList", "ThrowsCount")

package com.agolock.media.service

import com.agolock.common.error.ApiException
import com.agolock.media.domain.Image
import com.agolock.media.domain.ImageStatus
import com.agolock.media.persistence.ImageMapper
import com.agolock.messaging.outbox.OutboxService
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.OffsetDateTime
import java.util.UUID

/**
 * 업로드 완료 상태 전이와 outbox 기록만 담당하는 짧은 트랜잭션 경계입니다.
 *
 * S3 HEAD/GET/형식 검증은 호출부에서 끝낸 뒤 이 메서드로 들어옵니다.
 */
@Service
class ImageUploadTransaction(
    private val imageMapper: ImageMapper,
    private val outboxService: OutboxService,
) {
    @Transactional
    fun complete(
        imageId: UUID,
        objectKey: String,
        mimeType: String,
        sizeBytes: Long,
        sha256: String,
        now: OffsetDateTime,
    ): Image {
        val image =
            imageMapper.findByIdForUpdate(imageId)
                ?: throw ApiException("RESOURCE_NOT_FOUND", HttpStatus.NOT_FOUND, "이미지를 찾을 수 없습니다.")
        if (image.objectKey != objectKey) {
            throw ApiException("VALIDATION_FAILED", HttpStatus.BAD_REQUEST, "객체 키가 일치하지 않습니다.")
        }
        if (image.status != ImageStatus.UPLOADING) {
            throw ApiException("IMAGE_INVALID_STATE", HttpStatus.CONFLICT, "업로드 대기 상태가 아닙니다.")
        }
        if (!image.uploadExpiresAt.isAfter(now)) {
            throw ApiException("IMAGE_UPLOAD_EXPIRED", HttpStatus.CONFLICT, "업로드 시간이 만료되었습니다.")
        }
        if (imageMapper.completeUpload(
                image.id,
                ImageStatus.PENDING,
                mimeType,
                sizeBytes,
                sha256,
                now,
            ) == 0
        ) {
            throw ApiException("IMAGE_INVALID_STATE", HttpStatus.CONFLICT, "이미지 상태를 변경할 수 없습니다.")
        }
        outboxService.enqueueImageUploaded(
            image.spaceId,
            image.id,
            mapOf(
                "imageId" to image.id,
                "targetType" to if (image.itemId != null) "ITEM" else "INVENTORY",
                "targetId" to (image.itemId ?: image.inventoryId),
                "objectKey" to image.objectKey,
                "declaredSizeBytes" to sizeBytes,
            ),
        )
        return image
    }
}
