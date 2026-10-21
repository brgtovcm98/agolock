package com.agolock.media.api

import com.agolock.media.domain.ImageStatus
import java.time.OffsetDateTime
import java.util.UUID

data class PresignImageRequest(
    val targetType: String,
    val targetId: UUID,
    val contentType: String = "image/webp",
    val sizeBytes: Int,
)

data class PresignImageResponse(
    val imageId: UUID,
    val uploadUrl: String,
    val objectKey: String,
    val expiresAt: OffsetDateTime,
    val uploadExpiresAt: OffsetDateTime,
)

data class CompleteImageRequest(
    val objectKey: String,
)

data class ImageResponse(
    val id: UUID,
    val status: ImageStatus,
    val itemId: UUID?,
    val inventoryId: UUID?,
    val mimeType: String?,
    val sizeBytes: Long?,
    val createdAt: OffsetDateTime,
)
